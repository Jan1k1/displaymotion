package org.jan1k.displaymotion.example;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.Pose;
import org.jan1k.displaymotion.packetevents.DisplayMotion;
import org.jan1k.displaymotion.packetevents.ModelBuilder;
import org.jan1k.displaymotion.packetevents.MotionDisplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExamplePlugin extends JavaPlugin implements Listener {

    private static final Animation IDLE = Animations.spin(80).with(Animations.bob(0.3f, 80));
    private static final Animation SHOW = Animations.popIn(20).then(IDLE);
    private static final Animation HIDE = Animations.popOut(10);
    // text spinning would be unreadable, so it only bobs
    private static final Animation SHOW_TEXT = Animations.popIn(20).then(Animations.bob(0.15f, 60));

    private final Map<UUID, MotionDisplay> displays = new HashMap<>();
    private DisplayMotion motion;

    @Override
    public void onEnable() {
        motion = DisplayMotion.create(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (motion != null) {
            motion.close();
        }
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
                old.play(HIDE, old::remove);
            }
            sender.sendPlainMessage("removed");
            return true;
        }
        if (old != null) {
            old.remove();
        }

        String kind = args.length > 0 ? args[0].toLowerCase() : "sword";
        MotionDisplay display = switch (kind) {
            case "block" -> motion.block(player.getLocation().add(0, 1.5, 0), Material.DIAMOND_BLOCK.createBlockData())
                    .play(SHOW);
            case "windmill" -> windmill(player.getLocation().add(2, 0, 0));
            case "text" -> motion.text(player.getLocation().add(0, 2, 0),
                            Component.text("displaymotion", NamedTextColor.GOLD))
                    .billboard(Display.Billboard.CENTER)
                    .play(SHOW_TEXT);
            default -> motion.item(player.getLocation().add(0, 1.5, 0), ItemStack.of(Material.DIAMOND_SWORD),
                    ItemDisplay.ItemDisplayTransform.GROUND).play(SHOW);
        };
        displays.put(player.getUniqueId(), display);
        return true;
    }

    private MotionDisplay windmill(Location at) {
        ModelBuilder model = motion.model(at)
                .block("base", Material.STONE_BRICKS.createBlockData(), Pose.IDENTITY
                        .withOffset(0f, 0.15f, 0f).withScale(1.2f, 0.3f, 1.2f))
                .block("tower", Material.OAK_PLANKS.createBlockData(), Pose.IDENTITY
                        .withOffset(0f, 1.15f, 0f).withScale(0.6f, 1.7f, 0.6f), "base")
                .block("roof", Material.DARK_OAK_PLANKS.createBlockData(), Pose.IDENTITY
                        .withOffset(0f, 1f, 0f).withRotation(0f, 45f, 0f).withScale(0.8f, 0.3f, 0.8f), "tower")
                .block("hub", Material.DARK_OAK_LOG.createBlockData(), Pose.IDENTITY
                        .withOffset(0f, 0.55f, 0.4f).withScale(0.2f, 0.2f, 0.2f), "tower");
        for (int i = 0; i < 4; i++) {
            float angle = i * 90f;
            double radians = Math.toRadians(angle);
            model.block("blade" + i, Material.WHITE_WOOL.createBlockData(), Pose.IDENTITY
                    .withOffset((float) (-Math.sin(radians) * 0.6), (float) (Math.cos(radians) * 0.6), 0.05f)
                    .withRotation(0f, 0f, angle)
                    .withScale(0.18f, 1f, 0.04f), "hub");
        }
        return model.spawn()
                .glow("hub", Color.ORANGE)
                .play(Animations.popIn(20))
                .play("hub", Animations.turn(0f, 0f, 360f, 60).loop());
    }
}
