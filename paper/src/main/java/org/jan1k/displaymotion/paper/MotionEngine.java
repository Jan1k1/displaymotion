package org.jan1k.displaymotion.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.netty.channel.Channel;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Ease;
import org.jan1k.displaymotion.Frames;
import org.jan1k.displaymotion.Pose;
import org.jan1k.displaymotion.Decomposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

final class MotionEngine {

    static final int ALL_TRACKS = -1;
    static final int ALL_PARTS = -1;

    private static final long TICK_MILLIS = 50L;
    private static final long MIN_TICK_GAP_NANOS = TimeUnit.MILLISECONDS.toNanos(25);
    private static final int INLINE_BAKE_TICKS = 600; // bigger ones bake on a seperate thread
    private static final Frames REST_FRAMES = Frames.bake(Animation.keyframes().at(0, Pose.IDENTITY).build());
    private static final int LOUD_ERRORS = 10;
    private static final long QUIET_LOG_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final int HITBOX_WIDTH_SLOT = 8;
    private static final int HITBOX_HEIGHT_SLOT = 9;
    private static final int HITBOX_RESPONSE_SLOT = 10;
    private static final int[] NO_IDS = new int[0];
    private static final double EYE_HEIGHT = 1.62;
    private static final double CROUCH_EYE_HEIGHT = 1.27;
    // less then this is not worth a packet
    private static final float LOOK_STEP = 2f;
    // the client blends a turn over this many ticks, so a riding display turns smooth and not in steps
    private static final int RIDE_TURN_TICKS = 1;

    private final Plugin plugin;
    private final ProtocolManager protocol;
    private final DisplayPacket packet;
    private final Composer composer = new Composer();
    private final ScheduledExecutorService thread;
    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final List<DisplayState> displays = new ArrayList<>();
    private final List<DisplayState> fresh = new ArrayList<>();
    private final List<DisplayState> encoded = new ArrayList<>();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private final List<Viewer> viewerList = new ArrayList<>();
    private final List<Viewer> dirty = new ArrayList<>();
    private final Map<Animation, Frames> baked = new WeakHashMap<>();
    private final List<TextJob> texts = new ArrayList<>();
    private final List<DisplayState> lookers = new ArrayList<>();
    // read from netty threads when a click comes in, so it has to be concurrent
    private final Map<Integer, Hitbox> clickable = new ConcurrentHashMap<>();
    // vehicle entity id to our entity ids that ride it, read from netty threads too
    private final Map<Integer, int[]> riders = new ConcurrentHashMap<>();
    // what the server itself says rides that vehicle, so we never kick a real passenger off
    private final Map<Integer, int[]> serverPassengers = new ConcurrentHashMap<>();
    private final Map<Integer, List<DisplayState>> rideGroups = new HashMap<>();
    final Set<MotionDisplay> riding = ConcurrentHashMap.newKeySet();
    final Set<MotionDisplay> following = ConcurrentHashMap.newKeySet();
    // main thread only
    final Bodies bodies = new Bodies();
    // real server entities players sit on, removed when the library closes
    final Set<org.bukkit.entity.Entity> seats = ConcurrentHashMap.newKeySet();

    private volatile Snapshot snapshot = new Snapshot(0L, new PlayerView[0]);
    volatile boolean debug;
    // tests swap this for Runnable::run, there is no server to schedule on
    Consumer<Runnable> mainThread;
    private Snapshot seen;
    private Object bundle;
    private long tick;
    private long lastRun;
    private int errors;
    private long lastQuietLog;

    MotionEngine(Plugin plugin) {
        this(plugin, true);
    }

    MotionEngine(Plugin plugin, boolean scheduled) {
        this.plugin = plugin;
        this.protocol = PacketEvents.getAPI().getProtocolManager();
        this.packet = new DisplayPacket();
        this.mainThread = task -> {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        };
        if (scheduled) {
            this.thread = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread t = new Thread(task, "displaymotion-" + plugin.getName());
                t.setDaemon(true);
                return t;
            });
            thread.scheduleAtFixedRate(this::run, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
        } else {
            this.thread = null;
        }
    }

    boolean isClosed() {
        return closed.get();
    }

    void submit(Runnable command) {
        if (closed.get()) {
            throw new IllegalStateException("this DisplayMotion is closed");
        }
        commands.add(command);
    }

    void submitQuietly(Runnable command) {
        if (!closed.get()) {
            commands.add(command);
        }
    }

    void publish(Snapshot views) {
        snapshot = views;
    }

    Hitbox hitbox(int entityId) {
        return clickable.get(entityId);
    }

    boolean hasRiders() {
        return !riders.isEmpty();
    }

    boolean ridden(int vehicle) {
        return riders.containsKey(vehicle);
    }

    // our own passenger packet comes thru the listener too, so drop our ids or they count as real ones.
    // the bot test showed [570,571,570,571] for one backpack before this
    void serverPassengers(int vehicle, int[] passengers) {
        int[] ours = riders.get(vehicle);
        if (ours == null) {
            return;
        }
        serverPassengers.put(vehicle, Arrays.stream(passengers)
                .filter(id -> Arrays.stream(ours).noneMatch(our -> our == id))
                .toArray());
    }

    // the passenger packet replaces the whole list on the client, so it always has the real ones and ours
    int[] passengersOf(int vehicle) {
        int[] real = serverPassengers.getOrDefault(vehicle, NO_IDS);
        int[] ours = riders.getOrDefault(vehicle, NO_IDS);
        int[] all = Arrays.copyOf(real, real.length + ours.length);
        System.arraycopy(ours, 0, all, real.length, ours.length);
        return all;
    }

    void debug(String message) {
        if (debug) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (thread == null) {
            shutdown();
            return;
        }
        thread.execute(this::shutdown);
        thread.shutdown();
        try {
            if (!thread.awaitTermination(1, TimeUnit.SECONDS)) {
                thread.shutdownNow();
            }
        } catch (InterruptedException e) {
            thread.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    void runTick() {
        try {
            tick();
        } catch (Throwable t) {
            warn("displaymotion tick failed", t);
        }
    }

    // everything below here runs on the motion thread only

    void add(DisplayState display) {
        composer.compose(display, true);
        display.index = displays.size();
        // got a full size flash here when play() came a tick after the spawn,
        // so just wait one tick before showing it and the first frame is already right
        display.showAfter = tick + 1;
        displays.add(display);
        fresh.add(display);
    }

    void addHitbox(DisplayState display, Hitbox hitbox) {
        if (display.removed) {
            return;
        }
        display.addHitbox(hitbox);
        clickable.put(hitbox.entityId, hitbox);
        debug("hitbox '" + hitbox.name + "' (" + hitbox.entityId + ") " + hitbox.width + "x" + hitbox.height
                + " added, " + display.viewers.size() + " players see it now");
        List<Viewer> targets = display.viewers;
        if (display.fresh || targets.isEmpty()) {
            return;
        }
        Object channel = targets.getFirst().channel;
        sendToAll(targets, encode(spawnHitbox(display, hitbox), channel), false);
        sendToAll(targets, encode(hitboxData(hitbox), channel), false);
        if (display.vehicle >= 0) {
            updateRiders(display.vehicle);
            sendToAll(targets, encode(passengers(display.vehicle), channel), false);
        }
    }

    void ride(DisplayState display, int vehicle, int[] passengers, UUID world, double x, double y, double z, float yaw) {
        ride(display, vehicle, passengers, world, x, y, z, RidePose.turn(yaw));
    }

    void ride(DisplayState display, int vehicle, int[] passengers, UUID world, double x, double y, double z, RidePose pose) {
        ride(display, vehicle, passengers, world, x, y, z, pose, null);
    }

    void ride(DisplayState display, int vehicle, int[] passengers, UUID world, double x, double y, double z, RidePose pose, UUID wearer) {
        if (display.removed) {
            return;
        }
        display.glideSteps = 0;
        if (display.vehicle >= 0) {
            dismount(display, false);
        }
        serverPassengers.put(vehicle, passengers);
        moveTo(display, world, x, y, z);
        display.vehicle = vehicle;
        rideGroups.computeIfAbsent(vehicle, id -> new ArrayList<>()).add(display);
        updateRiders(vehicle);
        level(display);
        wear(display, wearer);
        poseTo(display, pose);
        if (display.fresh) {
            return;
        }
        List<Viewer> targets = display.viewers;
        if (!targets.isEmpty()) {
            sendToAll(targets, encode(passengers(vehicle), targets.getFirst().channel), false);
        }
        updateViewers(display);
    }

    // the client turns a display by the entity yaw and pitch first (DisplayRenderer.calculateOrientation) and a
    // passenger keeps them. a model made where a player looked down kept that pitch and leaned the whole hat or
    // backpack, so set both to 0 while it is not a passenger yet. the spawn sends angles as a byte, 0 stays exact
    private void level(DisplayState display) {
        if (display.yaw == 0f && display.pitch == 0f) {
            return;
        }
        display.yaw = 0f;
        display.pitch = 0f;
        List<Viewer> targets = display.viewers;
        if (display.fresh || targets.isEmpty()) {
            return;
        }
        Object channel = targets.getFirst().channel;
        Location at = new Location(display.x, display.y, display.z, 0f, 0f);
        for (PartState part : display.parts) {
            if (part.hasEntity()) {
                sendToAll(targets, encode(new WrapperPlayServerEntityTeleport(part.entityId, at, false), channel), false);
            }
        }
    }

    // the wearer starts from what everyone got, after that it gets its own transform
    private void wear(DisplayState display, UUID wearer) {
        display.wearer = wearer;
        display.ownTurn = display.turn;
        display.ownTilt = display.tilt;
        for (PartState part : display.parts) {
            System.arraycopy(part.sent, 0, part.own, 0, Decomposer.SIZE);
        }
    }

    // the entity yaw is 0 while it rides, the whole turn goes in the transform and blends
    private void poseTo(DisplayState display, RidePose pose) {
        float turn = wrap(pose.yaw() - display.yaw);
        float ownTurn = display.wearer == null ? turn : wrap(pose.own() - display.yaw);
        float ownTilt = display.wearer == null ? pose.tilt() : pose.ownTilt();
        boolean ownHidden = display.wearer != null && pose.ownHidden();
        if (turn == display.turn && ownTurn == display.ownTurn && ownTilt == display.ownTilt && ownHidden == display.ownHidden && pose.tilt() == display.tilt
                && pose.pivot() == display.pivot && pose.drop() == display.drop) {
            return;
        }
        display.turn = turn;
        display.ownTurn = ownTurn;
        display.ownTilt = ownTilt;
        display.ownHidden = ownHidden;
        display.tilt = pose.tilt();
        display.pivot = pose.pivot();
        display.drop = pose.drop();
        composer.compose(display, true);
        if (!display.fresh && !display.viewers.isEmpty()) {
            sendChanges(display, RIDE_TURN_TICKS);
        }
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    // the client moves a passenger by itself every frame, so here only the numbers change and a turn goes out
    void follow(DisplayState display, UUID world, double x, double y, double z, float yaw) {
        follow(display, world, x, y, z, RidePose.turn(yaw));
    }

    void follow(DisplayState display, UUID world, double x, double y, double z, RidePose pose) {
        if (display.removed || display.vehicle < 0) {
            return;
        }
        moveTo(display, world, x, y, z);
        poseTo(display, pose);
    }

    void dismount(DisplayState display, boolean stayHere) {
        int vehicle = display.vehicle;
        if (vehicle < 0) {
            return;
        }
        display.vehicle = -1;
        // off the vehicle the turn goes back into the entity yaw
        display.yaw = wrap(display.yaw + display.turn);
        display.turn = 0f;
        display.ownTurn = 0f;
        display.ownTilt = 0f;
        display.ownHidden = false;
        display.tilt = 0f;
        display.pivot = 0f;
        display.drop = 0f;
        composer.compose(display, true);
        if (!display.removed && !display.fresh && !display.viewers.isEmpty()) {
            sendChanges(display, Frames.STEP_TICKS);
        }
        display.wearer = null;
        List<DisplayState> group = rideGroups.get(vehicle);
        if (group != null) {
            group.remove(display);
        }
        updateRiders(vehicle);
        List<Viewer> targets = display.viewers;
        if (!display.removed && !display.fresh && !targets.isEmpty()) {
            sendToAll(targets, encode(passengers(vehicle), targets.getFirst().channel), false);
        }
        if (!riders.containsKey(vehicle)) {
            serverPassengers.remove(vehicle);
        }
        if (display.removed || display.fresh || targets.isEmpty()) {
            return;
        }
        Object channel = targets.getFirst().channel;
        if (stayHere) {
            Location at = new Location(display.x, display.y, display.z, display.yaw, display.pitch);
            for (PartState part : display.parts) {
                if (part.hasEntity()) {
                    sendToAll(targets, encode(new WrapperPlayServerEntityTeleport(part.entityId, at, false), channel), false);
                }
            }
        }
    }

    private void moveTo(DisplayState display, UUID world, double x, double y, double z) {
        if (!display.world.equals(world)) {
            for (int i = display.viewers.size() - 1; i >= 0; i--) {
                hide(display, display.viewers.get(i));
            }
            display.world = world;
        }
        display.x = x;
        display.y = y;
        display.z = z;
    }

    private void updateRiders(int vehicle) {
        List<DisplayState> group = rideGroups.get(vehicle);
        if (group == null || group.isEmpty()) {
            rideGroups.remove(vehicle);
            riders.remove(vehicle);
            return;
        }
        int count = 0;
        for (DisplayState display : group) {
            count += display.entityIds.length;
        }
        int[] ids = new int[count];
        int i = 0;
        for (DisplayState display : group) {
            System.arraycopy(display.entityIds, 0, ids, i, display.entityIds.length);
            i += display.entityIds.length;
        }
        riders.put(vehicle, ids);
    }

    private WrapperPlayServerSetPassengers passengers(int vehicle) {
        return new WrapperPlayServerSetPassengers(vehicle, passengersOf(vehicle));
    }

    void play(DisplayState display, int track, Animation animation, Runnable onEnd) {
        play(display, track, animation, onEnd, 0);
    }

    void play(DisplayState display, int track, Animation animation, Runnable onEnd, int fadeTicks) {
        if (display.removed) {
            return;
        }
        long request = ++display.requests[track];
        Frames frames = baked.get(animation);
        if (frames != null || animation.introTicks() + animation.loopTicks() <= INLINE_BAKE_TICKS) {
            if (frames == null) {
                frames = Frames.bake(animation);
                baked.put(animation, frames);
            }
            start(display, track, frames, onEnd, fadeTicks);
            return;
        }
        CompletableFuture.supplyAsync(() -> Frames.bake(animation), ForkJoinPool.commonPool())
                .whenComplete((result, error) -> {
                    if (error != null) {
                        submitQuietly(() -> warn("displaymotion could not bake an animation", error));
                        return;
                    }
                    submitQuietly(() -> {
                        baked.put(animation, result);
                        if (display.requests[track] == request && !display.removed) {
                            start(display, track, result, onEnd, fadeTicks);
                        }
                    });
                });
    }

    void stop(DisplayState display, int track) {
        Track[] tracks = display.tracks;
        for (int i = 0; i < tracks.length; i++) {
            if (track == ALL_TRACKS || track == i) {
                display.requests[i]++;
                if (tracks[i] != null) {
                    tracks[i].running = false;
                }
            }
        }
        if (track == ALL_TRACKS || track == 0) {
            display.handle.playing = false;
        }
        display.active = anyRunning(display);
    }

    void look(DisplayState display, int part, Consumer<PartState> change) {
        if (display.removed) {
            return;
        }
        for (int i = 0; i < display.parts.length; i++) {
            if (part == ALL_PARTS || part == i) {
                PartState state = display.parts[i];
                change.accept(state);
                sendPart(display, state, state.kind == PartState.NPC ? NpcPackets.data(state) : packet.look(state));
            }
        }
        // billboard decides if a part can have a culling box so check boxes again
        composer.compose(display, true);
        if (!display.fresh && !display.viewers.isEmpty()) {
            sendChanges(display, Frames.STEP_TICKS);
        }
    }

    // swaps the item or block of the parts of this kind and tells the viewers
    void content(DisplayState display, int part, int kind, Consumer<PartState> change) {
        if (display.removed) {
            return;
        }
        for (int i = 0; i < display.parts.length; i++) {
            PartState state = display.parts[i];
            if ((part == ALL_PARTS || part == i) && state.kind == kind) {
                change.accept(state);
                sendPart(display, state, packet.content(state));
            }
        }
    }

    void text(DisplayState display, int part, Component text) {
        if (display.removed) {
            return;
        }
        // a text set by hand wins over a typing or counting that still runs there
        texts.removeIf(job -> job.display == display && (part == ALL_PARTS || job.part == part || job.part == ALL_PARTS));
        setText(display, part, text);
    }

    private void setText(DisplayState display, int part, Component text) {
        for (int i = 0; i < display.parts.length; i++) {
            PartState state = display.parts[i];
            if ((part == ALL_PARTS || part == i) && state.kind == PartState.TEXT && !text.equals(state.text)) {
                state.text = text;
                sendPart(display, state, packet.content(state));
            }
        }
    }

    private void sendPart(DisplayState display, PartState part, PacketWrapper<?> update) {
        List<Viewer> targets = display.viewers;
        if (display.fresh || targets.isEmpty() || !part.hasEntity()) {
            return;
        }
        sendToAll(targets, encode(update, targets.getFirst().channel), false);
    }

    void teleport(DisplayState display, UUID world, double x, double y, double z, float yaw, float pitch, int ticks) {
        display.glideSteps = 0;
        move(display, world, x, y, z, yaw, pitch, ticks);
    }

    // client teleport_duration is linear and caps at 59 ticks, so step the ease here every 2 ticks
    void glide(DisplayState display, double x, double y, double z, int ticks, Ease ease) {
        if (display.removed) {
            return;
        }
        display.fromX = display.x;
        display.fromY = display.y;
        display.fromZ = display.z;
        display.toX = x;
        display.toY = y;
        display.toZ = z;
        display.glideSteps = Math.max(1, Math.round(ticks / (float) Frames.STEP_TICKS));
        display.glideStep = 0;
        display.glideEase = ease;
        display.active = true;
    }

    // last step snaps to the exact spot so float math doesnt leave it a hair off
    static double lerp(double from, double to, int step, int steps, Ease ease) {
        return step >= steps ? to : from + (to - from) * ease.apply(step / (float) steps);
    }

    private void move(DisplayState display, UUID world, double x, double y, double z, float yaw, float pitch, int ticks) {
        if (display.removed) {
            return;
        }
        if (!display.world.equals(world)) {
            for (int i = display.viewers.size() - 1; i >= 0; i--) {
                hide(display, display.viewers.get(i));
            }
        }
        display.world = world;
        display.x = x;
        display.y = y;
        display.z = z;
        display.yaw = yaw;
        display.pitch = pitch;
        if (display.fresh) {
            display.moveTicks = ticks;
            return;
        }
        // viewers that just got it spawned already have the new spot, only the ones who kept it need the move
        List<Viewer> kept = new ArrayList<>(display.viewers);
        int oldTicks = display.moveTicks;
        display.moveTicks = ticks;
        if (display.viewers.isEmpty()) {
            display.stale = true;
        } else {
            composer.compose(display, true);
            sendChanges(display, Frames.STEP_TICKS);
        }
        updateViewers(display);
        kept.retainAll(display.viewers);
        if (kept.isEmpty()) {
            return;
        }
        Object channel = kept.getFirst().channel;
        // smoothing time has to be set before the move, same bundle so client sees both at once
        if (ticks != oldTicks) {
            for (PartState part : display.parts) {
                if (!part.isDisplay()) {
                    continue;
                }
                sendToAll(kept, encode(packet.move(part, ticks), channel), false);
            }
        }
        Location to = new Location(x, y, z, yaw, pitch);
        for (PartState part : display.parts) {
            if (!part.hasEntity()) {
                continue;
            }
            sendToAll(kept, encode(new WrapperPlayServerEntityTeleport(part.entityId, to, false), channel), false);
        }
        for (Hitbox hitbox : display.hitboxes) {
            sendToAll(kept, encode(new WrapperPlayServerEntityTeleport(hitbox.entityId, hitboxSpot(display, hitbox), false), channel), false);
        }
    }

    void visibility(DisplayState display, UUID player, boolean visible) {
        if (display.removed) {
            return;
        }
        if (visible) {
            display.hiddenFrom.remove(player);
            display.shownTo.add(player);
        } else {
            display.shownTo.remove(player);
            display.hiddenFrom.add(player);
        }
        if (!display.fresh) {
            updateViewers(display);
        }
    }

    void visibleByDefault(DisplayState display, boolean visible) {
        if (display.removed) {
            return;
        }
        display.visibleByDefault = visible;
        if (!display.fresh) {
            updateViewers(display);
        }
    }

    void lod(DisplayState display, double blocks) {
        if (display.removed) {
            return;
        }
        display.lod = blocks;
        if (!display.fresh) {
            updateDetail(display);
        }
    }

    void viewRange(DisplayState display, double range) {
        if (display.removed) {
            return;
        }
        display.range = range;
        if (display.fresh) {
            return;
        }
        List<Viewer> targets = display.viewers;
        if (!targets.isEmpty()) {
            for (PartState part : display.parts) {
                if (!part.isDisplay()) {
                    continue;
                }
                sendToAll(targets, encode(packet.range(part, display.clientRange()), targets.getFirst().channel), false);
            }
        }
        updateViewers(display);
    }

    void remove(DisplayState display) {
        if (display.removed) {
            return;
        }
        display.removed = true;
        display.active = false;
        for (Hitbox hitbox : display.hitboxes) {
            clickable.remove(hitbox.entityId);
        }
        if (display.vehicle >= 0) {
            dismount(display, false);
        }
        display.handle.playing = false;
        for (int i = display.viewers.size() - 1; i >= 0; i--) {
            hide(display, display.viewers.get(i));
        }
        DisplayState last = displays.removeLast();
        if (last != display) {
            displays.set(display.index, last);
            last.index = display.index;
        }
        fresh.remove(display);
    }

    void viewerGone(UUID id) {
        Viewer viewer = viewers.remove(id);
        if (viewer == null) {
            return;
        }
        viewerList.remove(viewer);
        for (DisplayState display : viewer.visible) {
            display.viewers.remove(viewer);
            display.far.remove(viewer);
            forgetLooks(display, viewer);
        }
        viewer.visible.clear();
        viewer.drop();
        dirty.remove(viewer);
    }

    void viewerReset(UUID id, long sequence) {
        Viewer viewer = viewers.get(id);
        if (viewer == null) {
            return;
        }
        for (DisplayState display : new ArrayList<>(viewer.visible)) {
            hide(display, viewer);
        }
        viewer.waitFor = Math.max(viewer.waitFor, sequence + 1);
        viewer.waiting = true;
    }

    private void run() {
        long now = System.nanoTime();
        // after a lag spike scheduleAtFixedRate runs all the missed ticks at once and
        // everything jumps 3 frames, so skip the ones that come to to fast
        if (now - lastRun < MIN_TICK_GAP_NANOS) {
            return;
        }
        lastRun = now;
        runTick();
    }

    private void tick() {
        drainCommands();
        tick++;

        Snapshot latest = snapshot;
        if (latest != seen) {
            seen = latest;
            refreshViewers(latest);
        }
        for (int i = fresh.size() - 1; i >= 0; i--) {
            DisplayState display = fresh.get(i);
            if (display.showAfter < tick) {
                fresh.remove(i);
                updateViewers(display);
            }
        }
        checkBackpressure();

        if (tick % Frames.STEP_TICKS == 0) {
            step();
        }
        stepTexts();
        if (tick % Frames.STEP_TICKS == 0) {
            stepLooks();
        }
        flush();
    }

    void npc(DisplayState display, int part, Consumer<PartState> change, boolean equipment) {
        if (display.removed) {
            return;
        }
        PartState npc = display.parts[part];
        change.accept(npc);
        sendPart(display, npc, equipment ? NpcPackets.equipment(npc) : NpcPackets.data(npc));
    }

    void npcSize(DisplayState display, int part, float size) {
        if (display.removed) {
            return;
        }
        PartState npc = display.parts[part];
        npc.npcSize = size;
        sendPart(display, npc, NpcPackets.scale(npc));
    }

    void npcSwing(DisplayState display, int part, boolean offHand) {
        if (display.removed) {
            return;
        }
        PartState npc = display.parts[part];
        sendPart(display, npc, NpcPackets.swing(npc, offHand));
    }

    void npcLook(DisplayState display, int part, double range) {
        if (display.removed) {
            return;
        }
        display.parts[part].lookRange = range;
        if (range > 0 && !lookers.contains(display)) {
            lookers.add(display);
        }
    }

    // each viewer gets its own turn of the head, so everyone sees the npc look at them. only sent when it moved
    // a bit, a player standing still costs nothing
    private void stepLooks() {
        for (int d = lookers.size() - 1; d >= 0; d--) {
            DisplayState display = lookers.get(d);
            boolean any = false;
            for (PartState npc : display.parts) {
                if (npc.kind != PartState.NPC || (npc.lookRange <= 0 && npc.looks.isEmpty())) {
                    continue;
                }
                any |= npc.lookRange > 0;
                if (display.removed) {
                    continue;
                }
                for (Viewer viewer : display.viewers) {
                    look(display, npc, viewer);
                }
            }
            if (!any || display.removed) {
                lookers.remove(d);
            }
        }
    }

    private void look(DisplayState display, PartState npc, Viewer viewer) {
        double dx = viewer.x - display.x;
        double dy = viewer.y + EYE_HEIGHT - (display.y + (npc.npcPose == NpcPose.CROUCHING ? CROUCH_EYE_HEIGHT : EYE_HEIGHT) * npc.npcSize);
        double dz = viewer.z - display.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw;
        float pitch;
        if (npc.lookRange > 0 && flat * flat + dy * dy <= npc.lookRange * npc.lookRange && flat > 1e-3) {
            yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
            pitch = (float) Math.toDegrees(-Math.atan2(dy, flat));
        } else {
            yaw = display.yaw;
            pitch = 0f;
        }
        float[] last = npc.looks.get(viewer);
        if (last != null && Math.abs(wrap(yaw - last[0])) < LOOK_STEP && Math.abs(pitch - last[1]) < LOOK_STEP) {
            return;
        }
        if (last == null && yaw == display.yaw && pitch == 0f) {
            return;
        }
        npc.looks.put(viewer, new float[]{yaw, pitch});
        queue(viewer, encode(new WrapperPlayServerEntityRotation(npc.entityId, yaw, pitch, true), viewer.channel));
        queue(viewer, encode(new WrapperPlayServerEntityHeadLook(npc.entityId, yaw), viewer.channel));
    }

    private static void forgetLooks(DisplayState display, Viewer viewer) {
        for (PartState part : display.parts) {
            if (part.kind == PartState.NPC) {
                part.looks.remove(viewer);
            }
        }
    }

    // a text that changes over time, like typing or counting. it runs here every tick, not on the main thread
    record TextJob(DisplayState display, int part, int ticks, int every, java.util.function.IntFunction<Component> frame,
                   Runnable onEnd, int[] tick) {
    }

    void animateText(DisplayState display, int part, int ticks, int every, java.util.function.IntFunction<Component> frame, Runnable onEnd) {
        if (display.removed) {
            return;
        }
        texts.removeIf(job -> job.display == display && job.part == part);
        texts.add(new TextJob(display, part, ticks, every, frame, onEnd, new int[]{0}));
        setText(display, part, frame.apply(0));
    }

    private void stepTexts() {
        for (int i = texts.size() - 1; i >= 0; i--) {
            TextJob job = texts.get(i);
            if (job.display.removed) {
                texts.remove(i);
                continue;
            }
            int now = ++job.tick[0];
            if (now >= job.ticks) {
                texts.remove(i);
                setText(job.display, job.part, job.frame.apply(job.ticks));
                if (job.onEnd != null) {
                    mainThread.accept(job.onEnd);
                }
            } else if (now % job.every == 0) {
                setText(job.display, job.part, job.frame.apply(now));
            }
        }
    }

    private void drainCommands() {
        Runnable command;
        while ((command = commands.poll()) != null) {
            try {
                command.run();
            } catch (Throwable t) {
                warn("displaymotion command failed", t);
            }
        }
    }

    private void refreshViewers(Snapshot latest) {
        for (PlayerView view : latest.views()) {
            Viewer viewer = viewers.get(view.id());
            if (viewer == null) {
                viewer = new Viewer(view.id());
                viewers.put(view.id(), viewer);
                viewerList.add(viewer);
            }
            viewer.update(view, tick, latest.sequence());
        }
        for (int i = viewerList.size() - 1; i >= 0; i--) {
            Viewer viewer = viewerList.get(i);
            if (viewer.seen != tick) {
                viewerGone(viewer.id);
            }
        }
        for (int i = 0; i < displays.size(); i++) {
            DisplayState display = displays.get(i);
            if (!display.fresh) {
                updateViewers(display);
            }
        }
    }

    private void updateViewers(DisplayState display) {
        display.fresh = false;
        for (int i = 0; i < viewerList.size(); i++) {
            Viewer viewer = viewerList.get(i);
            if (viewer.waiting) {
                continue;
            }
            boolean shown = viewer.visible.contains(display);
            boolean see = display.canSee(viewer, shown);
            if (see && !shown) {
                show(display, viewer);
            } else if (!see && shown) {
                hide(display, viewer);
            }
        }
        if (display.lod > 0 || !display.far.isEmpty()) {
            updateDetail(display);
        }
    }

    // slow conection, drop frames for that player and send the full state when it catches up
    private void checkBackpressure() {
        for (int i = 0; i < viewerList.size(); i++) {
            Viewer viewer = viewerList.get(i);
            boolean writable = !(viewer.channel instanceof Channel channel) || channel.isWritable();
            if (!writable) {
                viewer.stalled = true;
            } else if (viewer.stalled) {
                viewer.stalled = false;
                if (viewer.resync) {
                    viewer.resync = false;
                    resync(viewer);
                }
            }
        }
    }

    private void resync(Viewer viewer) {
        for (DisplayState display : viewer.visible) {
            freshen(display);
            for (PartState part : display.parts) {
                if (!part.isDisplay()) {
                    continue;
                }
                float[] values = viewer.id.equals(display.wearer) ? part.own : part.sent;
                queue(viewer, encode(packet.spawn(part, values, display.clientRange(), display.moveTicks), viewer.channel));
            }
        }
    }

    private void step() {
        for (int i = 0; i < displays.size(); i++) {
            DisplayState display = displays.get(i);
            if (!display.active) {
                continue;
            }
            try {
                stepDisplay(display);
            } catch (Throwable t) {
                warn("displaymotion removed a display that failed", t);
                remove(display);
                if (i < displays.size() && displays.get(i) != display) {
                    i--;
                }
            }
        }
    }

    private void stepDisplay(DisplayState display) {
        Track[] tracks = display.tracks;
        boolean running = false;
        for (int t = 0; t < tracks.length; t++) {
            Track track = tracks[t];
            if (track == null) {
                continue;
            }
            // a short animation can end before its fade is done, the fade still has to finish
            if (track.from != null) {
                fade(display, track);
                running = true;
            }
            if (!track.running) {
                continue;
            }
            if (!advance(display, track, t)) {
                track.running = false;
                if (t == 0) {
                    display.handle.playing = false;
                }
                if (track.onEnd != null) {
                    mainThread.accept(track.onEnd);
                }
                continue;
            }
            running = true;
        }
        display.size.step();
        running |= display.size.busy();
        for (PartState part : display.parts) {
            part.size.step();
            running |= part.size.busy();
        }
        boolean gliding = display.glideStep < display.glideSteps;
        if (gliding) {
            display.glideStep++;
            running |= display.glideStep < display.glideSteps;
        }
        display.active = running;

        if (!display.viewers.isEmpty()) {
            composer.compose(display, display.stale);
            display.stale = false;
            sendChanges(display, Frames.STEP_TICKS);
        } else {
            display.stale = true;
            for (Track track : tracks) {
                if (track != null) {
                    track.moved = false;
                }
            }
        }
        if (gliding) {
            double x = lerp(display.fromX, display.toX, display.glideStep, display.glideSteps, display.glideEase);
            double y = lerp(display.fromY, display.toY, display.glideStep, display.glideSteps, display.glideEase);
            double z = lerp(display.fromZ, display.toZ, display.glideStep, display.glideSteps, display.glideEase);
            display.handle.movedTo(x, y, z);
            move(display, display.world, x, y, z, display.yaw, display.pitch, Frames.STEP_TICKS);
        }
    }

    private void start(DisplayState display, int track, Frames frames, Runnable onEnd, int fadeTicks) {
        Track previous = display.tracks[track];
        if (fadeTicks > 0) {
            // nothing played there yet, so it fades in from the rest pose
            Track from = previous != null ? previous : new Track(REST_FRAMES, null);
            from.from = null;
            if (previous == null) {
                from.running = false;
            }
            display.tracks[track] = new Track(frames, onEnd, from, Math.max(1, Math.round(fadeTicks / (float) Frames.STEP_TICKS)));
        } else {
            display.tracks[track] = new Track(frames, onEnd);
        }
        display.active = true;
        if (track == 0) {
            display.handle.playing = true;
        }
        if (display.fresh) {
            composer.compose(display, true);
        }
    }

    // moves a track on by the speed of its display. a fast one passes more then one frame in a step and each
    // of them still gets its events, a slow one stays between two frames. false when it ran past its end
    private boolean advance(DisplayState display, Track track, int t) {
        long was = (long) Math.floor(track.clock);
        track.clock += display.speed;
        long now = (long) Math.floor(track.clock);
        for (long step = was + 1; step <= now; step++) {
            int next = track.frames.frameAt(step);
            if (next < 0) {
                return false;
            }
            if (t >= 0) {
                String[] events = track.frames.eventsAt(next);
                if (events.length > 0 && display.handle.hearsEvents()) {
                    String part = t == 0 ? null : display.parts[t - 1].name;
                    mainThread.accept(() -> display.handle.reached(part, events));
                }
            }
            track.moved |= next != track.frame;
            track.frame = next;
        }
        float between = (float) (track.clock - now);
        int ahead = between > 0f && now >= 0 ? track.frames.frameAt(now + 1) : -1;
        if (ahead < 0) {
            between = 0f;
        }
        track.moved |= between != track.between || between > 0f && ahead != track.ahead;
        track.between = between;
        track.ahead = ahead;
        return true;
    }

    void speed(DisplayState display, double speed) {
        display.speed = speed;
    }

    void scale(DisplayState display, int part, float size, int ticks) {
        if (display.removed) {
            return;
        }
        int steps = Math.max(1, Math.round(ticks / (float) Frames.STEP_TICKS));
        (part == ALL_PARTS ? display.size : display.parts[part].size).to(size, steps);
        display.active = true;
    }

    // the old animation keeps going under the new one until the fade is over
    private void fade(DisplayState display, Track track) {
        Track from = track.from;
        if (from.running && !advance(display, from, -1)) {
            from.running = false;
        }
        track.moved = true;
        if (++track.fadeStep >= track.fadeSteps) {
            track.from = null;
        }
    }

    private void freshen(DisplayState display) {
        if (display.stale) {
            composer.compose(display, true);
            display.stale = false;
        }
    }

    // far viewers keep the pose they have, they get it all again when they come closer
    private void updateDetail(DisplayState display) {
        List<Viewer> targets = display.viewers;
        for (int i = 0; i < targets.size(); i++) {
            Viewer viewer = targets.get(i);
            boolean was = display.far.contains(viewer);
            boolean now = display.farFor(viewer, was);
            if (now == was) {
                continue;
            }
            if (now) {
                display.far.add(viewer);
                int[] details = detailIds(display);
                if (details.length > 0) {
                    queue(viewer, encode(new WrapperPlayServerDestroyEntities(details), viewer.channel));
                }
            } else {
                display.far.remove(viewer);
                catchUp(display, viewer);
            }
        }
    }

    private void catchUp(DisplayState display, Viewer viewer) {
        boolean wearer = viewer.id.equals(display.wearer);
        Location at = new Location(display.x, display.y, display.z, display.yaw, display.pitch);
        for (PartState part : display.parts) {
            if (!part.hasEntity()) {
                continue;
            }
            if (part.detail) {
                queue(viewer, encode(new WrapperPlayServerSpawnEntity(part.entityId, part.uuid, entityType(part), at, display.yaw, 0, Vector3d.zero()), viewer.channel));
            }
            if (part.kind == PartState.NPC) {
                if (part.detail) {
                    queue(viewer, encode(NpcPackets.data(part), viewer.channel));
                    if (!part.equipment.isEmpty()) {
                        queue(viewer, encode(NpcPackets.equipment(part), viewer.channel));
                    }
                    if (part.npcSize != 1f) {
                        queue(viewer, encode(NpcPackets.scale(part), viewer.channel));
                    }
                }
                continue;
            }
            queue(viewer, encode(wearer ? packet.spawn(part, part.own, display.clientRange(), display.moveTicks)
                    : packet.spawn(part, display.clientRange(), display.moveTicks), viewer.channel));
        }
        if (display.vehicle >= 0) {
            queue(viewer, encode(passengers(display.vehicle), viewer.channel));
        }
    }

    private static int[] detailIds(DisplayState display) {
        return java.util.Arrays.stream(display.parts).filter(part -> part.detail && part.hasEntity()).mapToInt(part -> part.entityId).toArray();
    }

    private List<Viewer> nearViewers(DisplayState display) {
        if (display.far.isEmpty()) {
            return display.viewers;
        }
        List<Viewer> near = new ArrayList<>(display.viewers.size());
        for (Viewer viewer : display.viewers) {
            if (!display.far.contains(viewer)) {
                near.add(viewer);
            }
        }
        return near;
    }

    private void sendChanges(DisplayState display, int duration) {
        List<Viewer> targets = nearViewers(display);
        if (targets.isEmpty()) {
            return;
        }
        Viewer wearer = wearerOf(display);
        for (PartState part : display.parts) {
            if (!part.hasEntity()) {
                continue;
            }
            if (part.changes != 0 && (wearer == null || targets.size() > 1)) {
                sendToAll(targets, wearer, encode(packet.frame(part, part.changes, duration), targets.getFirst().channel), true);
            }
            if (wearer != null && part.ownChanges != 0) {
                sendToAll(List.of(wearer), null, encode(packet.frame(part, part.own, part.ownChanges, duration), wearer.channel), true);
            }
        }
    }

    // the player a riding display sits on, when that player sees it
    private Viewer wearerOf(DisplayState display) {
        if (display.wearer == null) {
            return null;
        }
        Viewer viewer = viewers.get(display.wearer);
        return viewer != null && viewer.visible.contains(display) ? viewer : null;
    }

    private void sendToAll(List<Viewer> targets, Object buffer, boolean droppable) {
        sendToAll(targets, null, buffer, droppable);
    }

    private void sendToAll(List<Viewer> targets, Viewer skip, Object buffer, boolean droppable) {
        try {
            for (int i = 0; i < targets.size(); i++) {
                Viewer viewer = targets.get(i);
                if (viewer == skip) {
                    continue;
                }
                if (droppable && viewer.stalled) {
                    viewer.resync = true;
                    continue;
                }
                queue(viewer, ByteBufHelper.retainedDuplicate(buffer));
            }
        } finally {
            ByteBufHelper.release(buffer);
        }
    }

    private void show(DisplayState display, Viewer viewer) {
        if (display.spawnBuffers == null) {
            freshen(display);
            PartState[] parts = display.parts;
            List<Hitbox> hitboxes = display.hitboxes;
            Object[] buffers = new Object[(parts.length + hitboxes.size()) * 2];
            display.spawnBuffers = buffers;
            markEncoded(display);
            for (int i = 0; i < parts.length; i++) {
                PartState part = parts[i];
                if (!part.hasEntity()) {
                    continue;
                }
                WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(part.entityId, part.uuid,
                        entityType(part),
                        new Location(display.x, display.y, display.z, display.yaw, display.pitch), display.yaw, 0, Vector3d.zero());
                buffers[i * 2] = encode(spawn, viewer.channel);
                buffers[i * 2 + 1] = encode(part.kind == PartState.NPC ? NpcPackets.data(part)
                        : packet.spawn(part, display.clientRange(), display.moveTicks), viewer.channel);
            }
            for (int i = 0; i < hitboxes.size(); i++) {
                int slot = (parts.length + i) * 2;
                buffers[slot] = encode(spawnHitbox(display, hitboxes.get(i)), viewer.channel);
                buffers[slot + 1] = encode(hitboxData(hitboxes.get(i)), viewer.channel);
            }
        }
        boolean wearer = viewer.id.equals(display.wearer);
        boolean far = display.farFor(viewer, false);
        if (far) {
            display.far.add(viewer);
        }
        Object[] buffers = display.spawnBuffers;
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] == null) {
                continue;
            }
            if (far && i / 2 < display.parts.length && display.parts[i / 2].detail) {
                continue;
            }
            // the wearer gets its own transform in the data packet after each spawn
            if (wearer && i % 2 == 1 && i / 2 < display.parts.length && display.parts[i / 2].isDisplay()) {
                PartState part = display.parts[i / 2];
                queue(viewer, encode(packet.spawn(part, part.own, display.clientRange(), display.moveTicks), viewer.channel));
                continue;
            }
            queue(viewer, ByteBufHelper.retainedDuplicate(buffers[i]));
        }
        for (PartState part : display.parts) {
            if (part.kind == PartState.NPC && !(far && part.detail)) {
                if (!part.equipment.isEmpty()) {
                    queue(viewer, encode(NpcPackets.equipment(part), viewer.channel));
                }
                if (part.npcSize != 1f) {
                    queue(viewer, encode(NpcPackets.scale(part), viewer.channel));
                }
            }
        }
        // after the spawn, the client ignores passengers it does not know yet
        if (display.vehicle >= 0) {
            queue(viewer, encode(passengers(display.vehicle), viewer.channel));
        }
        viewer.visible.add(display);
        display.viewers.add(viewer);
    }

    private void hide(DisplayState display, Viewer viewer) {
        if (display.destroyBuffer == null) {
            markEncoded(display);
            display.destroyBuffer = encode(new WrapperPlayServerDestroyEntities(display.entityIds), viewer.channel);
        }
        queue(viewer, ByteBufHelper.retainedDuplicate(display.destroyBuffer));
        viewer.visible.remove(display);
        display.viewers.remove(viewer);
        display.far.remove(viewer);
        forgetLooks(display, viewer);
    }

    private void queue(Viewer viewer, Object buffer) {
        if (viewer.queue(buffer)) {
            dirty.add(viewer);
        }
    }

    private void flush() {
        for (int i = 0; i < dirty.size(); i++) {
            Viewer viewer = dirty.get(i);
            if (!viewer.hasPending()) {
                continue;
            }
            if (bundle == null) {
                bundle = encode(new WrapperPlayServerBundle(), viewer.channel);
            }
            Object channel = viewer.channel;
            Object[] buffers = viewer.drain(bundle);
            ChannelHelper.runInEventLoop(channel, () -> write(channel, buffers));
        }
        dirty.clear();

        for (int i = 0; i < encoded.size(); i++) {
            DisplayState display = encoded.get(i);
            if (display.spawnBuffers != null) {
                for (Object buffer : display.spawnBuffers) {
                    release(buffer);
                }
                display.spawnBuffers = null;
            }
            release(display.destroyBuffer);
            display.destroyBuffer = null;
            display.encoded = false;
        }
        encoded.clear();
    }

    private void write(Object channel, Object[] buffers) {
        for (Object buffer : buffers) {
            protocol.writePacket(channel, buffer);
        }
        ChannelHelper.flush(channel);
    }

    private void markEncoded(DisplayState display) {
        if (!display.encoded) {
            display.encoded = true;
            encoded.add(display);
        }
    }

    private void shutdown() {
        try {
            drainCommands();
            for (int i = displays.size() - 1; i >= 0; i--) {
                remove(displays.get(i));
            }
            flush();
            release(bundle);
            bundle = null;
        } catch (Throwable t) {
            warn("displaymotion could not clean up", t);
        }
    }

    private void warn(String message, Throwable error) {
        errors++;
        if (errors <= LOUD_ERRORS) {
            plugin.getLogger().log(Level.WARNING, message, error);
            return;
        }
        long now = System.nanoTime();
        if (now - lastQuietLog >= QUIET_LOG_NANOS) {
            lastQuietLog = now;
            plugin.getLogger().log(Level.WARNING, message + " (" + errors + " errors so far)", error);
        }
    }

    private static WrapperPlayServerSpawnEntity spawnHitbox(DisplayState display, Hitbox hitbox) {
        return new WrapperPlayServerSpawnEntity(hitbox.entityId, hitbox.uuid, EntityTypes.INTERACTION,
                hitboxSpot(display, hitbox), 0f, 0, Vector3d.zero());
    }

    // response true makes the hand swing on a right click, like a real entity
    private static WrapperPlayServerEntityMetadata hitboxData(Hitbox hitbox) {
        return new WrapperPlayServerEntityMetadata(hitbox.entityId, List.of(
                new EntityData<>(HITBOX_WIDTH_SLOT, EntityDataTypes.FLOAT, hitbox.width),
                new EntityData<>(HITBOX_HEIGHT_SLOT, EntityDataTypes.FLOAT, hitbox.height),
                new EntityData<>(HITBOX_RESPONSE_SLOT, EntityDataTypes.BOOLEAN, true)));
    }

    private static Location hitboxSpot(DisplayState display, Hitbox hitbox) {
        double[] offset = hitbox.offset(display.yaw, display.pitch);
        return new Location(display.x + offset[0], display.y + offset[1], display.z + offset[2], 0f, 0f);
    }

    private static EntityType entityType(PartState part) {
        return switch (part.kind) {
            case PartState.BLOCK -> EntityTypes.BLOCK_DISPLAY;
            case PartState.TEXT -> EntityTypes.TEXT_DISPLAY;
            case PartState.NPC -> EntityTypes.MANNEQUIN;
            default -> EntityTypes.ITEM_DISPLAY;
        };
    }

    private static boolean anyRunning(DisplayState display) {
        for (Track track : display.tracks) {
            if (track != null && track.running) {
                return true;
            }
        }
        if (display.size.busy() || display.glideStep < display.glideSteps) {
            return true;
        }
        for (PartState part : display.parts) {
            if (part.size.busy()) {
                return true;
            }
        }
        return false;
    }

    private static Object encode(PacketWrapper<?> wrapper, Object channel) {
        try {
            // encode once and every viewer gets a retainedDuplicate, way cheaper then encoding per player
            wrapper.prepareForSend(channel, true);
            return wrapper.getBuffer();
        } catch (Throwable t) {
            release(wrapper.getBuffer());
            throw t;
        } finally {
            wrapper.setBuffer(null);
        }
    }

    private static void release(Object buffer) {
        if (buffer != null && ByteBufHelper.refCnt(buffer) > 0) {
            ByteBufHelper.release(buffer);
        }
    }
}
