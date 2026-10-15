package org.jan1k.displaymotion.example;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
import org.bukkit.util.Vector;
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
            case "windmill" -> windmill(inFront(player, 6));
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

    // front of the model is +z, so turn it to look back at the player
    private static Location inFront(Player player, double distance) {
        Location feet = player.getLocation();
        Vector forward = feet.getDirection().setY(0);
        if (forward.lengthSquared() < 1e-6) {
            forward = new Vector(0, 0, 1);
        }
        Location at = feet.clone().add(forward.normalize().multiply(distance));
        at.setYaw(feet.getYaw() + 180f);
        at.setPitch(0f);
        return at;
    }

    private MotionDisplay windmill(Location at) {
        ModelBuilder model = motion.model(at);

        box(model, "foundation", Material.COBBLESTONE, 0, 0.2f, 0, 2.4f, 0.4f, 2.4f);
        box(model, "foundation_top", Material.POLISHED_ANDESITE, 0, 0.45f, 0, 2.2f, 0.1f, 2.2f);

        // two tiers, the top one thinner, that's how the old dutch ones look
        tier(model, "lower", 1.4f, 1.8f, 1.8f);
        box(model, "band_lower", Material.DARK_OAK_PLANKS, 0, 2.3f, 0, 1.92f, 0.12f, 1.92f);
        tier(model, "upper", 3.11f, 1.5f, 1.5f);
        box(model, "band_upper", Material.DARK_OAK_PLANKS, 0, 3.9f, 0, 1.62f, 0.12f, 1.62f);

        roof(model, "roof_0", 4.11f, 1.75f, 0.3f);
        roof(model, "roof_1", 4.41f, 1.25f, 0.3f);
        roof(model, "roof_2", 4.69f, 0.75f, 0.26f);
        box(model, "rod", Material.LIGHTNING_ROD, 0, 5.05f, 0, 0.6f, 0.5f, 0.6f);

        box(model, "door_frame", Material.DARK_OAK_PLANKS, 0, 1.0f, 0.905f, 0.7f, 1.05f, 0.04f);
        box(model, "door", Material.SPRUCE_PLANKS, 0, 0.975f, 0.925f, 0.55f, 0.95f, 0.04f);
        for (int side = -1; side <= 1; side += 2) {
            window(model, "window_lower_" + side, side * 0.9f, 1.6f);
            window(model, "window_upper_" + side, side * 0.75f, 3.1f);
        }

        model.block("axle", Bukkit.createBlockData("minecraft:dark_oak_log[axis=z]"), Pose.IDENTITY
                .withOffset(0f, 3.3f, 0.95f).withScale(0.32f, 0.32f, 0.7f));
        for (int i = 0; i < 4; i++) {
            sail(model, i);
        }

        return model.spawn()
                .play(Animations.popIn(25))
                .play("axle", Animations.turn(0f, 0f, -360f, 120).loop());
    }

    private static void tier(ModelBuilder model, String name, float y, float height, float width) {
        box(model, name, Material.WHITE_TERRACOTTA, 0, y, 0, width, height, width);
        float corner = width / 2f - 0.04f;
        int post = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(model, name + "_post_" + post++, Material.STRIPPED_DARK_OAK_LOG,
                        sx * corner, y, sz * corner, 0.16f, height, 0.16f);
            }
        }
    }

    private static void roof(ModelBuilder model, String name, float y, float width, float height) {
        model.block(name, Material.DEEPSLATE_TILES.createBlockData(), Pose.IDENTITY
                .withOffset(0f, y, 0f).withRotation(0f, 45f, 0f).withScale(width, height, width));
    }

    private static void window(ModelBuilder model, String name, float x, float y) {
        float out = Math.signum(x) * 0.02f;
        box(model, name + "_frame", Material.DARK_OAK_PLANKS, x + out, y, 0, 0.05f, 0.4f, 0.4f);
        box(model, name, Material.BLACK_STAINED_GLASS, x + out * 2, y, 0, 0.04f, 0.28f, 0.28f);
    }

    private static void sail(ModelBuilder model, int index) {
        float angle = index * 90f;
        double radians = Math.toRadians(angle);
        String arm = "arm_" + index;
        // the arm sits in front of the axle, everything else hangs off the arm so it can use local offsets
        model.block(arm, Material.STRIPPED_SPRUCE_LOG.createBlockData(), Pose.IDENTITY
                .withOffset((float) (-Math.sin(radians) * 1.15), (float) (Math.cos(radians) * 1.15), 0.38f)
                .withRotation(0f, 0f, angle)
                .withScale(0.12f, 2.1f, 0.12f), "axle");
        model.block(arm + "_cloth", Material.WHITE_WOOL.createBlockData(), Pose.IDENTITY
                .withOffset(0.33f, 0.2f, -0.03f).withScale(0.55f, 1.65f, 0.03f), arm);
        model.block(arm + "_edge", Material.SPRUCE_PLANKS.createBlockData(), Pose.IDENTITY
                .withOffset(0.62f, 0.2f, 0.03f).withScale(0.05f, 1.7f, 0.05f), arm);
        float[] slats = {-0.45f, 0.1f, 0.65f};
        for (int s = 0; s < slats.length; s++) {
            model.block(arm + "_slat_" + s, Material.SPRUCE_PLANKS.createBlockData(), Pose.IDENTITY
                    .withOffset(0.28f, slats[s], 0.03f).withScale(0.68f, 0.05f, 0.05f), arm);
        }
    }

    private static void box(ModelBuilder model, String name, Material material,
                            float x, float y, float z, float sizeX, float sizeY, float sizeZ) {
        model.block(name, material.createBlockData(), Pose.IDENTITY.withOffset(x, y, z).withScale(sizeX, sizeY, sizeZ));
    }
}
