package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public final class DisplayMotion {

    public static final double DEFAULT_VIEW_RANGE = 48.0;

    private static final long VIEW_CHECK_TICKS = 10L;
    private static final double TELEPORT_RESET_DISTANCE = 32.0;

    private final Plugin plugin;
    private final MotionEngine engine;
    private final Events events = new Events();
    private final BukkitTask snapshotTask;
    private final Location scratch = new Location(null, 0, 0, 0);
    private long snapshots;

    private DisplayMotion(Plugin plugin) {
        this.plugin = plugin;
        this.engine = new MotionEngine(plugin);
        Bukkit.getPluginManager().registerEvents(events, plugin);
        this.snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::snapshot, 0L, VIEW_CHECK_TICKS);
    }

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

    public MotionDisplay item(Location location, ItemStack item) {
        return item(location, item, ItemDisplay.ItemDisplayTransform.NONE);
    }

    public MotionDisplay item(Location location, ItemStack item, ItemDisplay.ItemDisplayTransform transform) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(transform, "transform");
        World world = requireWorld(location);
        // enum order matches the network id
        return spawn(location, world, SpigotConversionUtil.fromBukkitItemStack(item), transform.ordinal(), 0);
    }

    public MotionDisplay block(Location location, BlockData block) {
        Objects.requireNonNull(block, "block");
        World world = requireWorld(location);
        return spawn(location, world, null, 0, SpigotConversionUtil.fromBukkitBlockData(block).getGlobalId());
    }

    public void close() {
        if (engine.isClosed()) {
            return;
        }
        snapshotTask.cancel();
        HandlerList.unregisterAll(events);
        engine.close();
    }

    public boolean isClosed() {
        return engine.isClosed();
    }

    private MotionDisplay spawn(Location location, World world,
                                com.github.retrooper.packetevents.protocol.item.ItemStack item,
                                int itemTransform, int blockState) {
        int entityId = Bukkit.getUnsafe().nextEntityId(world);
        MotionDisplay display = new MotionDisplay(engine, location.clone(), entityId,
                item, itemTransform, blockState, DEFAULT_VIEW_RANGE);
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
            engine.submit(() -> engine.viewerGone(event.getPlayer().getUniqueId()));
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
            engine.submit(() -> engine.viewerReset(id, after));
        }
    }
}
