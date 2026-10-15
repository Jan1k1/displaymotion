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
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.Pose;
import org.jan1k.displaymotion.paper.DisplayMotion;
import org.jan1k.displaymotion.paper.Follow;
import org.jan1k.displaymotion.paper.ModelBuilder;
import org.jan1k.displaymotion.paper.MotionDisplay;
import org.jan1k.displaymotion.paper.ImportedModel;

import java.io.File;
import java.util.ArrayList;
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
    private static final Animation PRESS = Animation.keyframes()
            .at(0, Pose.IDENTITY)
            .at(2, Pose.IDENTITY.withOffset(0f, -0.08f, 0f))
            .at(6, Pose.IDENTITY)
            .build()
            .event(2, "click");
    private static final Animation CAP_IDLE = Animations.bob(0.02f, 30);
    private static final Animation LABEL_IDLE = Animations.bob(0.08f, 50);
    private static final int SHOWCASE_SLOTS = 7;
    private static final double SHOWCASE_RADIUS = 7;
    private static final float SHOWCASE_SPREAD = 25f;
    private static final long EFFECT_EVERY_TICKS = 60L;

    private static final List<String> EFFECTS = List.of("shatter", "tombstone", "swords", "soul");

    private final Map<UUID, MotionDisplay> displays = new HashMap<>();
    private final Map<UUID, String> killEffects = new HashMap<>();
    private final Map<UUID, List<MotionDisplay>> showcases = new HashMap<>();
    private final Map<UUID, BukkitTask> showcaseTasks = new HashMap<>();
    private DisplayMotion motion;

    @Override
    public void onEnable() {
        motion = DisplayMotion.create(this);
        // the shipped examples always get the newest version, your own files in models/ are left alone
        for (String example : List.of("models/robot.bbmodel", "models/windmill.bbmodel", "models/chest.bdengine")) {
            saveResource(example, true);
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
        clearShowcase(event.getPlayer().getUniqueId());
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
                .then(Commands.literal("hat").executes(context -> show(context.getSource(), this::hat)))
                .then(Commands.literal("backpack").executes(context -> show(context.getSource(), this::backpack)))
                .then(Commands.literal("bench").executes(context -> show(context.getSource(), player -> bench(inFront(player, 3)))))
                .then(Commands.literal("button").executes(context -> show(context.getSource(), player -> button(inFront(player, 3)))))
                .then(Commands.literal("showcase").executes(context -> showcase(context.getSource())))
                .then(Commands.literal("debug").executes(context -> debug(context.getSource())))
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())));

        LiteralArgumentBuilder<CommandSourceStack> effect = Commands.literal("effect");
        LiteralArgumentBuilder<CommandSourceStack> killEffect = Commands.literal("killeffect")
                .then(Commands.literal("off").executes(context -> setKillEffect(context.getSource(), null)));
        for (String name : EFFECTS) {
            effect.then(Commands.literal(name).executes(context -> effectHere(context.getSource(), name)));
            killEffect.then(Commands.literal(name).executes(context -> setKillEffect(context.getSource(), name)));
        }
        root.then(effect).then(killEffect);

        // drop any .bbmodel (blockbench) or .bdengine (bdengine) file into plugins/displaymotion-example/models
        root.then(Commands.literal("model")
                .then(Commands.argument("file", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            modelFiles().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> showModel(context.getSource(), StringArgumentType.getString(context, "file"), null))
                        .then(Commands.argument("animation", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    ImportedModel model = loadModel(StringArgumentType.getString(context, "file"));
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
        ImportedModel model = loadModel(file);
        if (model == null) {
            source.getSender().sendPlainMessage("cant read models/" + file + ", is it a .bbmodel or .bdengine file?");
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
        File[] files = new File(getDataFolder(), "models").listFiles((dir, name) -> name.endsWith(".bbmodel") || name.endsWith(".bdengine"));
        return files == null ? List.of() : java.util.Arrays.stream(files).map(File::getName).sorted().toList();
    }

    private ImportedModel loadModel(String file) {
        File source = new File(new File(getDataFolder(), "models"), file);
        if (!source.isFile()) {
            return null;
        }
        try {
            return ImportedModel.load(source.toPath());
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
        boolean showcase = clearShowcase(player.getUniqueId());
        MotionDisplay old = displays.remove(player.getUniqueId());
        if (old == null && !showcase) {
            player.sendPlainMessage("nothing to remove");
            return 0;
        }
        if (old != null && !old.isRemoved()) {
            old.play(HIDE, old::remove);
        }
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

    // a crown on the head, it turns where the player looks. 0 is the top of the hitbox (1.8), the drawn head
    // goes up to 1.875 so the crown sits a bit higher or it ends up inside the head
    private MotionDisplay hat(Player player) {
        ModelBuilder crown = motion.model(player.getLocation())
                .block("band", Material.GOLD_BLOCK.createBlockData(), Pose.IDENTITY.withOffset(0f, 0.04f, 0f).withScale(0.5f, 0.12f, 0.5f));
        int spike = 0;
        for (float x : new float[]{-0.2f, 0.2f}) {
            for (float z : new float[]{-0.2f, 0.2f}) {
                crown.block("spike_" + spike++, Material.GOLD_BLOCK.createBlockData(),
                        Pose.IDENTITY.withOffset(x, 0.17f, z).withScale(0.1f, 0.14f, 0.1f));
            }
        }
        return crown.item("gem", ItemStack.of(Material.DIAMOND), Pose.IDENTITY.withOffset(0f, 0.32f, 0f).withScale(0.35f))
                .spawn()
                .ride(player, Follow.HEAD)
                .play(Animations.popIn(10))
                .play("gem", Animations.spin(40).with(Animations.bob(0.04f, 40)));
    }

    // sits on the back and turns with the body, not with the head
    private MotionDisplay backpack(Player player) {
        return motion.model(player.getLocation())
                .block("bag", Material.BARREL.createBlockData(), Pose.IDENTITY.withOffset(0f, -0.74f, -0.235f).withScale(0.45f, 0.55f, 0.25f))
                .block("flap", Material.BROWN_WOOL.createBlockData(), Pose.IDENTITY.withOffset(0f, -0.52f, -0.37f).withScale(0.4f, 0.2f, 0.02f))
                .spawn()
                .ride(player, Follow.BODY)
                .play(Animations.popIn(10));
    }

    // right click to sit, two people fit. sneak to get up
    private MotionDisplay bench(Location at) {
        MotionDisplay bench = motion.model(at)
                .block("seat", Material.SPRUCE_PLANKS.createBlockData(), Pose.IDENTITY.withOffset(0f, 0.4f, 0f).withScale(1.6f, 0.1f, 0.5f))
                .block("back", Material.SPRUCE_PLANKS.createBlockData(), Pose.IDENTITY.withOffset(0f, 0.75f, -0.22f).withScale(1.6f, 0.4f, 0.06f))
                .block("leg_left", Material.DARK_OAK_LOG.createBlockData(), Pose.IDENTITY.withOffset(-0.7f, 0.18f, 0f).withScale(0.1f, 0.36f, 0.45f))
                .block("leg_right", Material.DARK_OAK_LOG.createBlockData(), Pose.IDENTITY.withOffset(0.7f, 0.18f, 0f).withScale(0.1f, 0.36f, 0.45f))
                .spawn()
                .hitbox(1.6f, 0.9f)
                .seat("left", -0.4, 0.35, 0)
                .seat("right", 0.4, 0.35, 0)
                .play(Animations.popIn(10));
        bench.onClick(click -> {
            if (click.rightClick() && !bench.sit(click.player(), "left") && !bench.sit(click.player(), "right")) {
                click.player().sendActionBar(Component.text("the bench is full", NamedTextColor.RED));
            }
        });
        return bench;
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

    // right click presses it and counts, left click breaks it
    private MotionDisplay button(Location at) {
        int[] clicks = {0};
        MotionDisplay button = motion.model(at)
                .block("base", Material.POLISHED_BLACKSTONE.createBlockData(), Pose.IDENTITY.withOffset(0f, 0.1f, 0f).withScale(0.9f, 0.2f, 0.9f))
                .block("cap", Material.RED_CONCRETE.createBlockData(), Pose.IDENTITY.withOffset(0f, 0.27f, 0f).withScale(0.6f, 0.14f, 0.6f))
                .text("label", Component.text("Click me", NamedTextColor.GOLD), Pose.IDENTITY.withOffset(0f, 0.8f, 0f))
                .spawn()
                .billboard("label", Display.Billboard.CENTER)
                .hitbox(1f, 0.5f)
                .play(Animations.popIn(10))
                .play("cap", CAP_IDLE)
                .play("label", LABEL_IDLE)
                .onEvent(event -> at.getWorld().playSound(at, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1f, 1f));
        button.onClick(click -> {
            if (click.leftClick()) {
                motion.effects().shatter(at, Material.RED_CONCRETE.createBlockData());
                displays.remove(click.player().getUniqueId(), button);
                button.remove();
                return;
            }
            clicks[0]++;
            button.glow("cap", Color.WHITE)
                    .text("label", Component.text("Clicks: " + clicks[0], NamedTextColor.GOLD))
                    .play("cap", PRESS, () -> {
                        if (!button.isRemoved()) {
                            button.glow("cap", null).play("cap", CAP_IDLE);
                        }
                    });
        });
        return button;
    }

    private int debug(CommandSourceStack source) {
        motion.debug(!motion.isDebug());
        source.getSender().sendPlainMessage("displaymotion debug " + (motion.isDebug() ? "on, see the server log" : "off"));
        return Command.SINGLE_SUCCESS;
    }

    // everything at once in a half circle, for videos and screenshots
    private int showcase(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        UUID id = player.getUniqueId();
        clearShowcase(id);
        List<MotionDisplay> spawned = new ArrayList<>();
        Location feet = player.getLocation();

        Location sword = showcaseSpot(feet, 0);
        spawned.add(motion.item(sword.clone().add(0, 1.5, 0), ItemStack.of(Material.DIAMOND_SWORD),
                ItemDisplay.ItemDisplayTransform.GROUND).play(SHOW));
        spawned.add(label(sword, 2.6, "Item"));

        Location block = showcaseSpot(feet, 1);
        spawned.add(motion.block(block.clone().add(0, 1.5, 0), Material.DIAMOND_BLOCK.createBlockData()).play(SHOW));
        spawned.add(label(block, 2.7, "Block"));

        Location robot = showcaseSpot(feet, 2);
        spawned.add(clickableModel("robot.bbmodel", robot, 1f, 1f, 2f));
        spawned.add(label(robot, 3.2, "Blockbench, click me"));

        Location button = showcaseSpot(feet, 3);
        spawned.add(button(button));

        Location chest = showcaseSpot(feet, 4);
        spawned.add(clickableModel("chest.bdengine", chest, 1.5f, 1.6f, 1.4f));
        spawned.add(label(chest, 2.6, "BDEngine, click me"));

        Location windmill = showcaseSpot(feet, 5);
        spawned.add(clickableModel("windmill.bbmodel", windmill, 0.5f, 2f, 4f));
        spawned.add(label(windmill, 5, "Windmill"));

        Location effects = showcaseSpot(feet, 6);
        spawned.add(label(effects, 2.8, "Effects"));
        int[] next = {0};
        showcaseTasks.put(id, getServer().getScheduler().runTaskTimer(this, () ->
                playEffect(EFFECTS.get(next[0]++ % EFFECTS.size()), effects, Component.text("Steve")), 20L, EFFECT_EVERY_TICKS));

        spawned.removeIf(java.util.Objects::isNull);
        showcases.put(id, spawned);
        player.sendPlainMessage("showcase spawned, /dm stop removes it");
        return Command.SINGLE_SUCCESS;
    }

    // right click plays the next animation of the model
    private MotionDisplay clickableModel(String file, Location at, float scale, float width, float height) {
        ImportedModel model = loadModel(file);
        if (model == null) {
            return null;
        }
        MotionDisplay display = model.spawn(motion, at, scale).hitbox(width, height);
        List<String> clips = model.animations();
        int[] clip = {0};
        if (!clips.isEmpty()) {
            model.play(display, clips.getFirst());
        }
        display.onClick(click -> {
            if (clips.isEmpty()) {
                return;
            }
            clip[0] = (clip[0] + 1) % clips.size();
            model.play(display, clips.get(clip[0]));
            click.player().sendActionBar(Component.text(file + ": " + clips.get(clip[0]), NamedTextColor.GOLD));
        });
        return display.play(Animations.popIn(15));
    }

    private MotionDisplay label(Location at, double height, String text) {
        return motion.text(at.clone().add(0, height, 0), Component.text(text, NamedTextColor.WHITE))
                .billboard(Display.Billboard.CENTER)
                .play(Animations.popIn(15));
    }

    private static Location showcaseSpot(Location feet, int slot) {
        float angle = feet.getYaw() + (slot - (SHOWCASE_SLOTS - 1) / 2f) * SHOWCASE_SPREAD;
        double radians = Math.toRadians(angle);
        Location at = feet.clone().add(-Math.sin(radians) * SHOWCASE_RADIUS, 0, Math.cos(radians) * SHOWCASE_RADIUS);
        at.setYaw(angle + 180f);
        at.setPitch(0f);
        return at;
    }

    private boolean clearShowcase(UUID id) {
        BukkitTask task = showcaseTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
        List<MotionDisplay> old = showcases.remove(id);
        if (old == null) {
            return false;
        }
        for (MotionDisplay display : old) {
            if (!display.isRemoved()) {
                display.play(HIDE, display::remove);
            }
        }
        return true;
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
