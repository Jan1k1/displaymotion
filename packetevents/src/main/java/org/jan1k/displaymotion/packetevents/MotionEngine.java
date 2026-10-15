package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Frames;
import org.jan1k.displaymotion.Pose;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

final class MotionEngine {

    private static final long TICK_MILLIS = 50L;
    private static final int MAX_LOGGED_ERRORS = 10;

    private final Plugin plugin;
    private final ProtocolManager protocol;
    private final DisplayPacket packet;
    private final ScheduledExecutorService thread;
    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();

    private final List<DisplayState> displays = new ArrayList<>();
    private final List<DisplayState> fresh = new ArrayList<>();
    private final List<DisplayState> encoded = new ArrayList<>();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private final List<Viewer> viewerList = new ArrayList<>();
    private final List<Viewer> dirty = new ArrayList<>();
    private final Map<Animation, Frames> itemFrames = new WeakHashMap<>();
    private final Map<Animation, Frames> blockFrames = new WeakHashMap<>();
    private final Frames stillItem;
    private final Frames stillBlock;

    private volatile Snapshot snapshot = new Snapshot(0L, new PlayerView[0]);
    private volatile boolean closed;
    private Snapshot seen;
    private Object bundle;
    private long tick;
    private int errors;

    MotionEngine(Plugin plugin) {
        this.plugin = plugin;
        this.protocol = PacketEvents.getAPI().getProtocolManager();
        this.packet = new DisplayPacket();
        Animation still = Animation.keyframes().at(0, Pose.IDENTITY).build();
        this.stillItem = Frames.bake(still, false);
        this.stillBlock = Frames.bake(still, true);
        this.thread = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread t = new Thread(task, "displaymotion-" + plugin.getName());
            t.setDaemon(true);
            return t;
        });
        thread.scheduleAtFixedRate(this::run, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    boolean isClosed() {
        return closed;
    }

    void submit(Runnable command) {
        if (closed) {
            throw new IllegalStateException("this DisplayMotion is closed");
        }
        commands.add(command);
    }

    void publish(Snapshot views) {
        snapshot = views;
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
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

    // everything below runs on the motion thread only

    void add(DisplayState display) {
        display.frames = display.isBlock() ? stillBlock : stillItem;
        display.step = -1;
        display.index = displays.size();
        displays.add(display);
        fresh.add(display);
    }

    void play(DisplayState display, Animation animation) {
        if (display.removed) {
            return;
        }
        display.frames = bake(animation, display.isBlock());
        display.frame = 0;
        display.playing = true;
        display.handle.playing = true;
        if (display.fresh) {
            display.step = -1;
            return;
        }
        display.step = 0;
        sendFrame(display, 0, 1);
    }

    void stop(DisplayState display) {
        display.playing = false;
        display.handle.playing = false;
    }

    void viewRange(DisplayState display, double range) {
        if (display.removed) {
            return;
        }
        display.range = range;
        display.rangeSquared = range * range;
        if (display.fresh) {
            return;
        }
        List<Viewer> targets = display.viewers;
        if (!targets.isEmpty()) {
            Object buffer = encode(packet.range(display), targets.getFirst().channel);
            for (int i = 0; i < targets.size(); i++) {
                queue(targets.get(i), ByteBufHelper.retainedDuplicate(buffer));
            }
            ByteBufHelper.release(buffer);
        }
        updateViewers(display);
    }

    void remove(DisplayState display) {
        if (display.removed) {
            return;
        }
        display.removed = true;
        display.playing = false;
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
        // wait for a position copy taken after the move
        viewer.waitFor = Math.max(viewer.waitFor, sequence + 1);
        viewer.waiting = true;
    }

    private void run() {
        try {
            tick();
        } catch (Throwable t) {
            if (errors++ < MAX_LOGGED_ERRORS) {
                plugin.getLogger().log(Level.WARNING, "displaymotion tick failed", t);
            }
        }
    }

    private void tick() {
        drainCommands();
        tick++;

        Snapshot latest = snapshot;
        if (latest != seen) {
            seen = latest;
            refreshViewers(latest);
        }
        for (int i = 0; i < fresh.size(); i++) {
            updateViewers(fresh.get(i));
        }
        fresh.clear();

        if (tick % Frames.STEP_TICKS == 0) {
            step();
        }
        flush();
    }

    private void drainCommands() {
        Runnable command;
        while ((command = commands.poll()) != null) {
            command.run();
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
            updateViewers(displays.get(i));
        }
    }

    private void updateViewers(DisplayState display) {
        display.fresh = false;
        for (int i = 0; i < viewerList.size(); i++) {
            Viewer viewer = viewerList.get(i);
            if (viewer.waiting) {
                continue;
            }
            boolean see = display.canSee(viewer);
            boolean shown = viewer.visible.contains(display);
            if (see && !shown) {
                show(display, viewer);
            } else if (!see && shown) {
                hide(display, viewer);
            }
        }
    }

    private void step() {
        for (int i = 0; i < displays.size(); i++) {
            DisplayState display = displays.get(i);
            if (!display.playing) {
                continue;
            }
            int frame = display.frames.frameAt(++display.step);
            if (frame < 0) {
                finish(display);
                continue;
            }
            display.frame = frame;
            if (display.frames.changed(frame)) {
                sendFrame(display, frame, Frames.STEP_TICKS);
            }
        }
    }

    private void finish(DisplayState display) {
        display.playing = false;
        display.handle.playing = false;
        Runnable onEnd = display.onEnd;
        if (onEnd != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, onEnd);
        }
    }

    private void sendFrame(DisplayState display, int frame, int duration) {
        List<Viewer> targets = display.viewers;
        if (targets.isEmpty()) {
            return;
        }
        Object buffer = encode(packet.frame(display, frame, duration), targets.getFirst().channel);
        for (int i = 0; i < targets.size(); i++) {
            queue(targets.get(i), ByteBufHelper.retainedDuplicate(buffer));
        }
        ByteBufHelper.release(buffer);
    }

    private void show(DisplayState display, Viewer viewer) {
        if (display.spawnBuffer == null) {
            WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(display.entityId, display.uuid,
                    display.isBlock() ? EntityTypes.BLOCK_DISPLAY : EntityTypes.ITEM_DISPLAY,
                    new Location(display.x, display.y, display.z, 0f, 0f), 0f, 0, null);
            display.spawnBuffer = encode(spawn, viewer.channel);
            display.metadataBuffer = encode(packet.spawn(display), viewer.channel);
            markEncoded(display);
        }
        queue(viewer, ByteBufHelper.retainedDuplicate(display.spawnBuffer));
        queue(viewer, ByteBufHelper.retainedDuplicate(display.metadataBuffer));
        viewer.visible.add(display);
        display.viewers.add(viewer);
    }

    private void hide(DisplayState display, Viewer viewer) {
        if (display.destroyBuffer == null) {
            display.destroyBuffer = encode(new WrapperPlayServerDestroyEntities(display.entityId), viewer.channel);
            markEncoded(display);
        }
        queue(viewer, ByteBufHelper.retainedDuplicate(display.destroyBuffer));
        viewer.visible.remove(display);
        display.viewers.remove(viewer);
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
            release(display.spawnBuffer);
            release(display.metadataBuffer);
            release(display.destroyBuffer);
            display.spawnBuffer = null;
            display.metadataBuffer = null;
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

    private Frames bake(Animation animation, boolean block) {
        Map<Animation, Frames> cache = block ? blockFrames : itemFrames;
        return cache.computeIfAbsent(animation, key -> Frames.bake(key, block));
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
            plugin.getLogger().log(Level.WARNING, "displaymotion could not clean up", t);
        }
    }

    private static Object encode(PacketWrapper<?> wrapper, Object channel) {
        wrapper.prepareForSend(channel, true);
        Object buffer = wrapper.getBuffer();
        wrapper.setBuffer(null);
        return buffer;
    }

    private static void release(Object buffer) {
        if (buffer != null) {
            ByteBufHelper.release(buffer);
        }
    }
}
