package org.jan1k.displaymotion.example;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.packetevents.DisplayMotion;
import org.jan1k.displaymotion.packetevents.MotionDisplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExamplePlugin extends JavaPlugin implements Listener {

    private static final Animation IDLE = Animations.spin(80).with(Animations.bob(0.3f, 80));
    private static final Animation SHOW = Animations.popIn(20).then(IDLE);
    private static final Animation HIDE = Animations.popOut(10);

    private final Map<UUID, MotionDisplay> displays = new HashMap<>();
    private DisplayMotion motion;

    @Override
    public void onEnable() {
        motion = DisplayMotion.create(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        motion.close();
        displays.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MotionDisplay display = displays.remove(event.getPlayer().getUniqueId());
        if (display != null) {
            display.remove();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendPlainMessage("players only");
            return true;
        }

        MotionDisplay old = displays.remove(player.getUniqueId());
        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            if (old != null) {
                old.play(HIDE).onEnd(old::remove);
            }
            sender.sendPlainMessage("removed");
            return true;
        }
        if (old != null) {
            old.remove();
        }

        Location at = player.getLocation().add(0, 1.5, 0);
        MotionDisplay display = args.length > 0 && args[0].equalsIgnoreCase("block")
                ? motion.block(at, Material.DIAMOND_BLOCK.createBlockData())
                : motion.item(at, ItemStack.of(Material.DIAMOND_SWORD), ItemDisplay.ItemDisplayTransform.GROUND);
        display.play(SHOW);
        displays.put(player.getUniqueId(), display);
        return true;
    }
}
