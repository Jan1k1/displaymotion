package org.jan1k.displaymotion.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jan1k.displaymotion.Pose;
import org.jan1k.displaymotion.paper.bstats.Metrics;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Entry point. Create one per plugin in {@code onEnable}, then spawn displays from it.
 * Everything runs off the main thread. It closes itself when your plugin disables.
 */
public final class DisplayMotion {

    // 48 is less then the 64 the client uses so we always hide it before the client does
    public static final double DEFAULT_VIEW_RANGE = 48.0;
    public static final String MAIN_PART = "main";

    private static final long VIEW_CHECK_TICKS = 10L;
    private static final double TELEPORT_RESET_DISTANCE = 32.0;
    private static final int BSTATS_ID = 34708;

    private final Plugin plugin;
    private final MotionEngine engine;
    private final Events events = new Events();
    private final Effects effects = new Effects(this);
    private final BukkitTask snapshotTask;
    private final BukkitTask rideTask;
    private final Location rideScratch = new Location(null, 0, 0, 0);
    private final PacketListenerCommon clicks;
    private final Location scratch = new Location(null, 0, 0, 0);
    private long snapshots;

    private DisplayMotion(Plugin plugin, boolean metrics) {
        this.plugin = plugin;
        this.engine = new MotionEngine(plugin);
        Bukkit.getPluginManager().registerEvents(events, plugin);
        this.snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::snapshot, 0L, VIEW_CHECK_TICKS);
        this.rideTask = Bukkit.getScheduler().runTaskTimer(plugin, this::followVehicles, 1L, 1L);
        this.clicks = PacketEvents.getAPI().getEventManager().registerListener(new Packets(), PacketListenerPriority.NORMAL);
        if (metrics) {
            // shows which plugins use the library, owners turn it off in plugins/bStats/config.yml
            new Metrics(plugin, BSTATS_ID).addCustomChart(new Metrics.SimplePie("host_plugin", plugin::getName));
        }
    }

    /**
     * Starts the library for this plugin. Call it once, on the main thread.
     * It sends anonymous usage stats to bStats (which plugins use displaymotion, server counts).
     * Use {@link #create(Plugin, boolean)} to turn that off. Server owners can turn off all bStats
     * in {@code plugins/bStats/config.yml}.
     * @throws IllegalStateException if PacketEvents is not running
     */
    public static DisplayMotion create(Plugin plugin) {
        return create(plugin, true);
    }

    /**
     * Same as {@link #create(Plugin)}. {@code metrics} false sends no bStats stats.
     * @throws IllegalStateException if PacketEvents is not running
     */
    public static DisplayMotion create(Plugin plugin, boolean metrics) {
        Objects.requireNonNull(plugin, "plugin");
        PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || !api.isInitialized()) {
            throw new IllegalStateException("PacketEvents is not running. Install it and add 'depend: [packetevents]' to plugin.yml");
        }
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("create DisplayMotion on the main thread, for example in onEnable");
        }
        return new DisplayMotion(plugin, metrics);
    }

    /** Spawns one item display at this location. Its single part is named {@code main}. */
    public MotionDisplay item(Location location, ItemStack item) {
        return item(location, item, ItemDisplay.ItemDisplayTransform.NONE);
    }

    /** Spawns one item display drawn with this item transform, for example {@code GROUND}. */
    public MotionDisplay item(Location location, ItemStack item, ItemDisplay.ItemDisplayTransform transform) {
        return model(location).item(MAIN_PART, item, transform, Pose.IDENTITY).spawn();
    }

    /** Spawns one block display. It turns and scales around the block center. */
    public MotionDisplay block(Location location, BlockData block) {
        return model(location).block(MAIN_PART, block, Pose.IDENTITY).spawn();
    }

    /** Spawns one text display. The text grows up from the location. */
    public MotionDisplay text(Location location, Component text) {
        return model(location).text(MAIN_PART, text, Pose.IDENTITY).spawn();
    }

    /** Starts a model made of many parts. Add parts, then call {@link ModelBuilder#spawn()}. */
    /** Ready-made effects like kill effects. Each one removes itself when it ends. */
    public Effects effects() {
        return effects;
    }

    public ModelBuilder model(Location location) {
        World world = requireWorld(location);
        return new ModelBuilder(this, location.clone(), world);
    }

    /** Removes every display of this plugin for every player. Safe to call more than once. */
    public void close() {
        if (engine.isClosed()) {
            return;
        }
        snapshotTask.cancel();
        rideTask.cancel();
        HandlerList.unregisterAll(events);
        PacketEvents.getAPI().getEventManager().unregisterListener(clicks);
        engine.close();
    }

    /**
     * Logs what the library does with clicks and hitboxes to the plugin logger. Off by default.
     * Turn it on when a hitbox does not react, the log says where the click got lost.
     */
    public DisplayMotion debug(boolean on) {
        engine.debug = on;
        return this;
    }

    /** True while {@link #debug(boolean)} is on. */
    public boolean isDebug() {
        return engine.debug;
    }

    /** True after {@link #close()} or after the plugin disabled. */
    public boolean isClosed() {
        return engine.isClosed();
    }

    MotionDisplay spawn(Location location, PartState[] parts) {
        MotionDisplay display = new MotionDisplay(engine, location, parts, DEFAULT_VIEW_RANGE);
        engine.submit(() -> engine.add(display.state()));
        return display;
    }

    private World requireWorld(Location location) {
        Objects.requireNonNull(location, "location");
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("location has no world");
        }
        return world;
    }

    // only thing on the main thread, just copy where everyone is. all the heavy stuff is is on the motion thread
    private void snapshot() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        PlayerManager players = PacketEvents.getAPI().getPlayerManager();
        PlayerView[] views = new PlayerView[online.size()];
        int count = 0;
        for (Player player : online) {
            Object channel = players.getChannel(player);
            if (channel == null) {
                continue;
            }
            player.getLocation(scratch);
            views[count++] = new PlayerView(player.getUniqueId(), scratch.getWorld().getUID(),
                    scratch.getX(), scratch.getY(), scratch.getZ(), channel);
        }
        engine.publish(new Snapshot(++snapshots, count == views.length ? views : Arrays.copyOf(views, count)));
    }

    private void followVehicles() {
        for (MotionDisplay display : engine.riding) {
            display.followVehicle(rideScratch);
        }
    }

    private final class Packets implements PacketListener {

        // the server knows nothing about our riders, so every passenger list it sends for that
        // vehicle gets ours added, and a fresh spawn of the vehicle gets them sent right after
        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (!engine.hasRiders()) {
                return;
            }
            if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
                WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
                int vehicle = packet.getEntityId();
                if (!engine.ridden(vehicle)) {
                    return;
                }
                engine.serverPassengers(vehicle, packet.getPassengers());
                packet.setPassengers(engine.passengersOf(vehicle));
                event.markForReEncode(true);
            } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                int vehicle = new WrapperPlayServerSpawnEntity(event).getEntityId();
                if (!engine.ridden(vehicle)) {
                    return;
                }
                User user = event.getUser();
                event.getTasksAfterSend().add(() ->
                        user.sendPacketSilently(new WrapperPlayServerSetPassengers(vehicle, engine.passengersOf(vehicle))));
            }
        }

        // 26.1 split the old packet in two: left click is its own attack packet now, and a right click
        // only comes as interact_at. first version only listened for the old way so no click ever got thru
        private final boolean newClicks = PacketEvents.getAPI().getServerManager().getVersion()
                .isNewerThanOrEquals(ServerVersion.V_26_1);

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            int entityId;
            boolean left;
            if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
                entityId = new WrapperPlayClientAttack(event).getEntityId();
                left = true;
            } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                entityId = packet.getEntityId();
                WrapperPlayClientInteractEntity.InteractAction action = packet.getAction();
                left = action == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
                if (engine.debug && engine.hitbox(entityId) != null) {
                    engine.debug("click packet on hitbox " + entityId + ": " + action + " " + packet.getHand());
                }
                // older servers send interact_at and interact for one right click, and again for the off hand
                WrapperPlayClientInteractEntity.InteractAction counted = newClicks
                        ? WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT
                        : WrapperPlayClientInteractEntity.InteractAction.INTERACT;
                if (!left && (action != counted || packet.getHand() != InteractionHand.MAIN_HAND)) {
                    if (engine.hitbox(entityId) != null) {
                        event.setCancelled(true);
                    }
                    return;
                }
            } else {
                return;
            }
            Hitbox hitbox = engine.hitbox(entityId);
            if (hitbox == null) {
                return;
            }
            // the server does not know this entity, so dont let it even look at the packet
            event.setCancelled(true);
            UUID id = event.getUser().getUUID();
            engine.debug((left ? "left" : "right") + " click on hitbox '" + hitbox.name + "' (" + entityId + ") by " + id);
            if (id == null || !plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> hitbox.handle.click(id, hitbox, left));
        }
    }

    private final class Events implements Listener {

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            UUID id = event.getPlayer().getUniqueId();
            engine.submitQuietly(() -> engine.viewerGone(id));
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onWorldChange(PlayerChangedWorldEvent event) {
            reset(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onRespawn(PlayerRespawnEvent event) {
            reset(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onTeleport(PlayerTeleportEvent event) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getWorld() != to.getWorld()
                    || from.distanceSquared(to) > TELEPORT_RESET_DISTANCE * TELEPORT_RESET_DISTANCE) {
                reset(event.getPlayer());
            }
        }

        @EventHandler
        public void onPluginDisable(PluginDisableEvent event) {
            if (event.getPlugin() == plugin) {
                close();
            }
        }

        private void reset(Player player) {
            UUID id = player.getUniqueId();
            long after = snapshots;
            engine.submitQuietly(() -> engine.viewerReset(id, after));
        }
    }
}
