package org.jan1k.displaymotion.example;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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
import org.jan1k.displaymotion.packetevents.blockbench.BlockbenchModel;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class ExamplePlugin extends JavaPlugin implements Listener {

    private static final Animation IDLE = Animations.spin(80).with(Animations.bob(0.3f, 80));
    private static final Animation SHOW = Animations.popIn(20).then(IDLE);
    private static final Animation HIDE = Animations.popOut(10);
    // spinning text is unreadable so it only bobs
    private static final Animation SHOW_TEXT = Animations.popIn(20).then(Animations.bob(0.15f, 60));

    private static final List<String> EFFECTS = List.of("shatter", "tombstone", "swords", "soul");

    private final Map<UUID, MotionDisplay> displays = new HashMap<>();
    private final Map<UUID, String> killEffects = new HashMap<>();
    private DisplayMotion motion;

    @Override
    public void onEnable() {
        motion = DisplayMotion.create(this);
        if (!new File(getDataFolder(), "models/robot.bbmodel").exists()) {
            saveResource("models/robot.bbmodel", false);
        }
        getServer().getPluginManager().registerEvents(this, this);
        registerCommand();
    }

    @Override
    public void onDisable() {
        if (motion != null) {
            motion.close();
        }
        displays.clear();
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        String effect = killer == null ? null : killEffects.get(killer.getUniqueId());
        if (effect == null) {
            return;
        }
        // turn it so the front looks at the killer, tombstone text has to be readable
        Location at = event.getEntity().getLocation();
        at.setDirection(killer.getLocation().toVector().subtract(at.toVector()));
        at.setPitch(0f);
        playEffect(effect, at, event.getEntity().name());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        killEffects.remove(event.getPlayer().getUniqueId());
        MotionDisplay display = displays.remove(event.getPlayer().getUniqueId());
        if (display != null) {
            display.remove();
        }
    }

    private void registerCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("displaymotion")
                .requires(source -> source.getSender().hasPermission("displaymotion.use"))
                .executes(context -> show(context.getSource(), this::sword))
                .then(Commands.literal("sword").executes(context -> show(context.getSource(), this::sword)))
                .then(Commands.literal("block").executes(context -> show(context.getSource(), this::block)))
                .then(Commands.literal("windmill").executes(context -> show(context.getSource(), player -> windmill(inFront(player, 6)))))
                .then(Commands.literal("text").executes(context -> show(context.getSource(), this::text)))
                .then(Commands.literal("pet").executes(context -> show(context.getSource(), this::pet)))
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())));

        LiteralArgumentBuilder<CommandSourceStack> effect = Commands.literal("effect");
        LiteralArgumentBuilder<CommandSourceStack> killEffect = Commands.literal("killeffect")
                .then(Commands.literal("off").executes(context -> setKillEffect(context.getSource(), null)));
        for (String name : EFFECTS) {
            effect.then(Commands.literal(name).executes(context -> effectHere(context.getSource(), name)));
            killEffect.then(Commands.literal(name).executes(context -> setKillEffect(context.getSource(), name)));
        }
        root.then(effect).then(killEffect);

        // drop any .bbmodel from blockbench into plugins/displaymotion-example/models
        root.then(Commands.literal("model")
                .then(Commands.argument("file", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            modelFiles().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> showModel(context.getSource(), StringArgumentType.getString(context, "file"), null))
                        .then(Commands.argument("animation", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    BlockbenchModel model = loadModel(StringArgumentType.getString(context, "file"));
                                    if (model != null) {
                                        model.animations().forEach(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> showModel(context.getSource(),
                                        StringArgumentType.getString(context, "file"),
                                        StringArgumentType.getString(context, "animation"))))));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(root.build(), "spawn an animated display", List.of("dm")));
    }

    private int show(CommandSourceStack source, Function<Player, MotionDisplay> spawn) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        MotionDisplay old = displays.remove(player.getUniqueId());
        if (old != null) {
            old.remove();
        }
        displays.put(player.getUniqueId(), spawn.apply(player));
        return Command.SINGLE_SUCCESS;
    }

    private int showModel(CommandSourceStack source, String file, String animation) {
        BlockbenchModel model = loadModel(file);
        if (model == null) {
            source.getSender().sendPlainMessage("cant read models/" + file + ", is it a .bbmodel from blockbench?");
            return 0;
        }
        if (animation != null && !model.animations().contains(animation)) {
            source.getSender().sendPlainMessage("no animation " + animation + ", there is " + model.animations());
            return 0;
        }
        return show(source, player -> {
            MotionDisplay display = model.spawn(motion, inFront(player, 5));
            String clip = animation != null ? animation : model.animations().stream().findFirst().orElse(null);
            if (clip != null) {
                model.play(display, clip);
            }
            return display.play(Animations.popIn(15));
        });
    }

    private List<String> modelFiles() {
        File[] files = new File(getDataFolder(), "models").listFiles((dir, name) -> name.endsWith(".bbmodel"));
        return files == null ? List.of() : java.util.Arrays.stream(files).map(File::getName).sorted().toList();
    }

    private BlockbenchModel loadModel(String file) {
        File source = new File(new File(getDataFolder(), "models"), file);
        if (!source.isFile() || !file.endsWith(".bbmodel")) {
            return null;
        }
        try {
            return BlockbenchModel.load(source.toPath());
        } catch (Exception e) {
            getLogger().warning("could not read " + file + ": " + e.getMessage());
            return null;
        }
    }

    private int effectHere(CommandSourceStack source, String effect) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        playEffect(effect, inFront(player, 4), player.name());
        return Command.SINGLE_SUCCESS;
    }

    private int setKillEffect(CommandSourceStack source, String effect) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        if (effect == null) {
            killEffects.remove(player.getUniqueId());
            player.sendPlainMessage("kill effect off");
        } else {
            killEffects.put(player.getUniqueId(), effect);
            player.sendPlainMessage("kill effect: " + effect + ", now kill something");
        }
        return Command.SINGLE_SUCCESS;
    }

    private void playEffect(String effect, Location at, Component name) {
        switch (effect) {
            case "shatter" -> motion.effects().shatter(at, Material.REDSTONE_BLOCK.createBlockData());
            case "tombstone" -> motion.effects().tombstone(at, name);
            case "swords" -> motion.effects().swordRain(at, ItemStack.of(Material.IRON_SWORD));
            default -> motion.effects().soul(at);
        }
    }

    private int stop(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        MotionDisplay old = displays.remove(player.getUniqueId());
        if (old == null) {
            player.sendPlainMessage("nothing to remove");
            return 0;
        }
        old.play(HIDE, old::remove);
        player.sendPlainMessage("removed");
        return Command.SINGLE_SUCCESS;
    }

    private MotionDisplay sword(Player player) {
        return motion.item(player.getLocation().add(0, 1.5, 0), ItemStack.of(Material.DIAMOND_SWORD),
                ItemDisplay.ItemDisplayTransform.GROUND).play(SHOW);
    }

    private MotionDisplay block(Player player) {
        return motion.block(player.getLocation().add(0, 1.5, 0), Material.DIAMOND_BLOCK.createBlockData()).play(SHOW);
    }

    private MotionDisplay pet(Player player) {
        MotionDisplay pet = motion.item(petSpot(player), ItemStack.of(Material.DIAMOND_SWORD),
                ItemDisplay.ItemDisplayTransform.GROUND).play(SHOW);
        // move it every 2 ticks and let the client glide over 4 so it trails a bit behind like a pet
        getServer().getScheduler().runTaskTimer(this, task -> {
            if (pet.isRemoved() || !player.isOnline()) {
                task.cancel();
                return;
            }
            pet.teleport(petSpot(player), 4);
        }, 2L, 2L);
        return pet;
    }

    private static Location petSpot(Player player) {
        Location spot = player.getLocation();
        Vector back = spot.getDirection().setY(0);
        if (back.lengthSquared() > 1e-6) {
            spot.subtract(back.normalize().multiply(1.2));
        }
        spot.add(0, 1.6, 0);
        spot.setPitch(0f);
        return spot;
    }

    private MotionDisplay text(Player player) {
        return motion.text(player.getLocation().add(0, 2, 0), Component.text("displaymotion", NamedTextColor.GOLD))
                .billboard(Display.Billboard.CENTER)
                .play(SHOW_TEXT);
    }

    // front of the model is +z so turn it to look back at the player
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

        // two tiers, top one thinner, thats how the old dutch ones look
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
        // arm sits in front of the axle, everything else hangs of the arm so it can use local offsets
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
