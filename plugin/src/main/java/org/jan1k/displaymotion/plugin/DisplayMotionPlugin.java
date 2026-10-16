package org.jan1k.displaymotion.plugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;
import org.jan1k.displaymotion.paper.bstats.Metrics;

public final class DisplayMotionPlugin extends JavaPlugin {

    private static final int BSTATS_ID = 34708;

    private PacketEventsAPI<?> packetEvents;

    @Override
    public void onLoad() {
        packetEvents = SpigotPacketEventsBuilder.build(this);
        packetEvents.getSettings()
                .checkForUpdates(false)
                .bStats(false)
                .debug(false)
                .reEncodeByDefault(false);
        PacketEvents.setAPI(packetEvents);
        packetEvents.load();
    }

    @Override
    public void onEnable() {
        packetEvents.init();
        // one report for the whole plugin, plugins that use it send nothing
        new Metrics(this, BSTATS_ID);
    }

    @Override
    public void onDisable() {
        if (packetEvents != null) {
            packetEvents.terminate();
        }
    }
}
