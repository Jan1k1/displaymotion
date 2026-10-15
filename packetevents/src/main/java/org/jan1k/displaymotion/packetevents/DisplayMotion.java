package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Entry point. Create one per plugin in {@code onEnable}, then spawn displays from it.
 * Everything runs off the main thread. It closes itself when your plugin disables.
 */
public final class DisplayMotion {

    public static final double DEFAULT_VIEW_RANGE = 48.0;
    public static final String MAIN_PART = "main";

    private static final long VIEW_CHECK_TICKS = 10L;
    private static final double TELEPORT_RESET_DISTANCE = 32.0;

    private final Plugin plugin;
    private final MotionEngine engine;
    private final Events events = new Events();
    private final Effects effects = new Effects(this);
    private final BukkitTask snapshotTask;
    private final Location scratch = new Location(null, 0, 0, 0);
    private long snapshots;

    private DisplayMotion(Plugin plugin) {
        this.plugin = plugin;
        this.engine = new MotionEngine(plugin);
        Bukkit.getPluginManager().registerEvents(events, plugin);
        this.snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::snapshot, 0L, VIEW_CHECK_TICKS);
    }

    /**
     * Starts the library for this plugin. Call it once, on the main thread.
     * @throws IllegalStateException if PacketEvents is not running
     */
    public static DisplayMotion create(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || !api.isInitialized()) {
            throw new IllegalStateException("PacketEvents is not running. Install it and add 'depend: [packetevents]' to plugin.yml");
        }
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("create DisplayMotion on the main thread, for example in onEnable");
        }
        return new DisplayMotion(plugin);
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
        HandlerList.unregisterAll(events);
        engine.close();
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
