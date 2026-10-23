package org.jan1k.displaymotion.example;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.Ease;
import org.jan1k.displaymotion.Pose;
import org.jan1k.displaymotion.example.DisplayOptions.Part;
import org.jan1k.displaymotion.paper.DisplayMotion;
import org.jan1k.displaymotion.paper.Follow;
import org.jan1k.displaymotion.paper.Leaderboard;
import org.jan1k.displaymotion.paper.Skin;
import org.jan1k.displaymotion.paper.SelfView;
import org.jan1k.displaymotion.paper.ModelBuilder;
import org.jan1k.displaymotion.paper.MotionDisplay;
import org.jan1k.displaymotion.paper.ImportedModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

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
    private final Map<UUID, Leaderboard> boards = new HashMap<>();
    private final Map<UUID, BukkitTask> boardTasks = new HashMap<>();
    private final Map<UUID, DragonRide> rides = new HashMap<>();
    private DisplayMotion motion;
    private List<String> itemNames;
    private List<String> blockNames;

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
        for (DragonRide ride : List.copyOf(rides.values())) {
            ride.remove();
        }
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
        clearBoard(event.getPlayer().getUniqueId());
        clearRide(event.getPlayer().getUniqueId());
        MotionDisplay display = displays.remove(event.getPlayer().getUniqueId());
        if (display != null) {
            display.remove();
        }
    }

    // left click while you ride it: roar and fire
    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        DragonRide ride = riding(event.getPlayer());
        if (ride != null) {
            ride.roar();
        }
    }

    // no breaking or placing blocks from up there by accident
    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (riding(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    // sneak is the vanilla way off, here it lands first and then lets you off
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && event.isCancellable()) {
            DragonRide ride = riding(player);
            if (ride != null && ride.keepOn()) {
                event.setCancelled(true);
            }
        }
    }

    private DragonRide riding(Player player) {
        DragonRide ride = rides.get(player.getUniqueId());
        return ride != null && ride.rider() == player ? ride : null;
    }

    private boolean clearRide(UUID id) {
        DragonRide ride = rides.get(id);
        if (ride == null) {
            return false;
        }
        ride.remove();
        return true;
    }

    private void registerCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("displaymotion")
                .requires(source -> source.getSender().hasPermission("displaymotion.use"))
                .executes(context -> show(context.getSource(), EnumSet.of(Part.ITEM), false, (player, options) -> sword(player), ""))
                .then(spawns("sword", EnumSet.of(Part.ITEM), this::sword))
                .then(spawns("block", EnumSet.of(Part.BLOCK), this::block))
                .then(spawns("windmill", EnumSet.of(Part.BLOCK), player -> windmill(inFront(player, 6))))
                .then(spawns("text", EnumSet.of(Part.TEXT), true, this::text))
                .then(spawns("pet", EnumSet.of(Part.ITEM), this::pet))
                .then(spawns("path", EnumSet.of(Part.ITEM), this::path))
                .then(spawns("turret", EnumSet.of(Part.BLOCK), this::turret))
                .then(spawns("hat", EnumSet.of(Part.BLOCK, Part.ITEM), this::hat))
                .then(spawns("backpack", EnumSet.of(Part.BLOCK), this::backpack))
                .then(Commands.literal("selfview").executes(context -> selfView(context.getSource())))
                .then(Commands.literal("leaderboard").executes(context -> leaderboard(context.getSource())))
                .then(Commands.literal("ease").executes(context -> easings(context.getSource())))
                .then(dragons())
                .then(spawns("npc", EnumSet.of(Part.TEXT), false, (player, options) -> npc(player, inFront(player, 4), options)))
                .then(spawns("bench", EnumSet.of(Part.BLOCK), player -> bench(inFront(player, 3))))
                .then(spawns("button", EnumSet.of(Part.BLOCK, Part.TEXT), player -> button(inFront(player, 3))))
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
        // after the file: an animation name, then options. no animation is fine, options start at the first key:value
        root.then(Commands.literal("model")
                .then(Commands.argument("file", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            modelFiles().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> showModel(context.getSource(), StringArgumentType.getString(context, "file"), ""))
                        .then(Commands.argument("animation and options", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    String typed = builder.getRemaining();
                                    if (typed.indexOf(' ') < 0 && typed.indexOf(':') < 0) {
                                        ImportedModel model = loadModel(StringArgumentType.getString(context, "file"));
                                        if (model != null) {
                                            model.animations().forEach(builder::suggest);
                                        }
                                    }
                                    return suggestOptions(EnumSet.allOf(Part.class), builder);
                                })
                                .executes(context -> showModel(context.getSource(),
                                        StringArgumentType.getString(context, "file"),
                                        StringArgumentType.getString(context, "animation and options"))))));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(root.build(), "spawn an animated display", List.of("dm")));
    }

    // /dm <name> [options], the options are key:value words, see DisplayOptions
    private LiteralArgumentBuilder<CommandSourceStack> spawns(String name, Set<Part> parts, Function<Player, MotionDisplay> spawn) {
        return spawns(name, parts, false, (player, options) -> spawn.apply(player));
    }

    // ownText: the spawn already writes the text: option itself
    private LiteralArgumentBuilder<CommandSourceStack> spawns(String name, Set<Part> parts, boolean ownText,
                                                              BiFunction<Player, DisplayOptions, MotionDisplay> spawn) {
        return Commands.literal(name)
                .executes(context -> show(context.getSource(), parts, ownText, spawn, ""))
                .then(Commands.argument("options", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestOptions(parts, builder))
                        .executes(context -> show(context.getSource(), parts, ownText, spawn,
                                StringArgumentType.getString(context, "options"))));
    }

    private CompletableFuture<Suggestions> suggestOptions(Set<Part> parts, SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        SuggestionsBuilder last = builder.createOffset(builder.getStart() + DisplayOptions.lastTokenStart(typed));
        DisplayOptions.suggest(typed, parts, this::materialNames).forEach(last::suggest);
        return last.buildFuture();
    }

    private List<String> materialNames(Part part) {
        if (itemNames == null) {
            itemNames = names(Material::isItem);
            blockNames = names(Material::isBlock);
        }
        return part == Part.ITEM ? itemNames : blockNames;
    }

    private static List<String> names(Predicate<Material> kind) {
        return Arrays.stream(Material.values())
                .filter(material -> !material.isLegacy() && kind.test(material))
                .map(material -> material.name().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
    }

    private int show(CommandSourceStack source, Set<Part> parts, boolean ownText,
                     BiFunction<Player, DisplayOptions, MotionDisplay> spawn, String rawOptions) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        DisplayOptions.Parsed parsed = DisplayOptions.parse(rawOptions);
        MotionDisplay old = displays.remove(player.getUniqueId());
        if (old != null) {
            old.remove();
        }
        List<String> problems = new ArrayList<>(parsed.errors());
        try {
            MotionDisplay display = spawn.apply(player, parsed.options());
            displays.put(player.getUniqueId(), display);
            DisplayOptions toApply = ownText ? parsed.options().withoutText() : parsed.options();
            problems.addAll(toApply.apply(display, parts));
        } catch (UnsupportedOperationException e) {
            player.sendPlainMessage(e.getMessage());
            return 0;
        }
        for (String problem : problems) {
            player.sendMessage(Component.text(problem, NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int showModel(CommandSourceStack source, String file, String rest) {
        String animation = null;
        String options = rest.strip();
        int space = options.indexOf(' ');
        String first = space < 0 ? options : options.substring(0, space);
        if (!first.isEmpty() && first.indexOf(':') < 0) {
            animation = first;
            options = space < 0 ? "" : options.substring(space + 1);
        }
        ImportedModel model = loadModel(file);
        if (model == null) {
            source.getSender().sendPlainMessage("cant read models/" + file + ", is it a .bbmodel or .bdengine file?");
            return 0;
        }
        if (animation != null && !model.animations().contains(animation)) {
            source.getSender().sendPlainMessage("no animation " + animation + ", there is " + model.animations());
            return 0;
        }
        String clip = animation != null ? animation : model.animations().stream().findFirst().orElse(null);
        return show(source, EnumSet.allOf(Part.class), false, (player, parsed) -> {
            MotionDisplay display = model.spawn(motion, inFront(player, 5));
            if (clip != null) {
                model.play(display, clip);
            }
            return display.play(Animations.popIn(15));
        }, options);
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

    // a mannequin with your skin that looks at you, right click it and it waves and says hello
    private MotionDisplay npc(Player player, Location at, DisplayOptions options) {
        MotionDisplay guide = motion.model(at)
                .npc("guide", Skin.of(player))
                .text("speech", Component.empty(), Pose.IDENTITY.withOffset(0f, 2.55f, 0f).withScale(0.8f))
                .spawn()
                .billboard("speech", Display.Billboard.CENTER)
                .hitbox(0.8f, 1.9f);
        guide.npc("guide")
                .name(Component.text("Guide", NamedTextColor.GOLD))
                .equip(org.bukkit.inventory.EquipmentSlot.HAND, ItemStack.of(Material.DIAMOND_SWORD))
                .lookAtPlayers();
        if (options.size() != null) {
            guide.npc("guide").size(options.size());
        }
        guide.onClick(click -> {
            guide.npc("guide").swing();
            guide.type("speech", Component.text("Hello, " + click.player().getName() + "!", NamedTextColor.WHITE), 2);
        });
        return guide;
    }

    // one block per ease, dropping and rising again, so you can see how each one feels
    private int easings(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        UUID id = player.getUniqueId();
        clearShowcase(id);
        Ease[] eases = {Ease.LINEAR, Ease.OUT, Ease.IN_OUT, Ease.BACK_OUT, Ease.ELASTIC_OUT, Ease.BOUNCE_OUT};
        Location center = inFront(player, 7);
        List<MotionDisplay> spawned = new ArrayList<>();
        for (int i = 0; i < eases.length; i++) {
            float x = (i - (eases.length - 1) / 2f) * 2f;
            Location at = center.clone().add(sideways(center, x));
            Animation drop = Animation.keyframes()
                    .at(0, Pose.IDENTITY.withOffset(0f, 1.6f, 0f))
                    .at(24, Pose.IDENTITY, eases[i])
                    .at(40, Pose.IDENTITY)
                    .at(64, Pose.IDENTITY.withOffset(0f, 1.6f, 0f), eases[i])
                    .at(80, Pose.IDENTITY.withOffset(0f, 1.6f, 0f))
                    .build()
                    .loop();
            spawned.add(motion.model(at)
                    .block("cube", Material.DIAMOND_BLOCK.createBlockData(), Pose.IDENTITY.withOffset(0f, 0.3f, 0f).withScale(0.6f))
                    .spawn()
                    .play(drop));
            spawned.add(label(at, 2.5, eases[i].name().toLowerCase()));
        }
        showcases.put(id, spawned);
        player.sendPlainMessage("easings spawned, /dm stop removes them");
        return Command.SINGLE_SUCCESS;
    }

    // /dm dragon [build|ice|ender|three]. build starts with the tornado of blocks, three is a fire, an ice
    // and an ender dragon at once. they need room, about 30 blocks around for the flight
    private LiteralArgumentBuilder<CommandSourceStack> dragons() {
        LiteralArgumentBuilder<CommandSourceStack> dragon = Commands.literal("dragon")
                .executes(context -> dragon(context.getSource(), Dragon.Kind.FIRE, false, false))
                .then(Commands.literal("build").executes(context -> dragon(context.getSource(), Dragon.Kind.FIRE, true, false)))
                .then(Commands.literal("three").executes(context -> dragon(context.getSource(), Dragon.Kind.FIRE, false, true)));
        LiteralArgumentBuilder<CommandSourceStack> ride = Commands.literal("ride")
                .executes(context -> ride(context.getSource(), Dragon.Kind.FIRE));
        for (Dragon.Kind kind : Dragon.Kind.values()) {
            dragon.then(Commands.literal(kind.name().toLowerCase(Locale.ROOT))
                    .executes(context -> dragon(context.getSource(), kind, false, false)));
            ride.then(Commands.literal(kind.name().toLowerCase(Locale.ROOT))
                    .executes(context -> ride(context.getSource(), kind)));
        }
        return dragon.then(ride);
    }

    private int dragon(CommandSourceStack source, Dragon.Kind kind, boolean build, boolean three) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        UUID id = player.getUniqueId();
        clearShowcase(id);
        Location at = inFront(player, 14);
        List<MotionDisplay> spawned = new ArrayList<>(Dragon.spawn(motion, at, kind, build, 0, 1, 1f));
        if (three) {
            // smaller ones on the sides, later in their life and flying the other way so they dont move in step
            spawned.addAll(Dragon.spawn(motion, at.clone().add(sideways(at, -11f)).add(forward(at, -6f)), Dragon.Kind.ICE, false, 130, 1, 0.75f));
            spawned.addAll(Dragon.spawn(motion, at.clone().add(sideways(at, 11f)).add(forward(at, -6f)), Dragon.Kind.ENDER, false, 255, -1, 0.65f));
        }
        showcases.put(id, spawned);
        player.sendPlainMessage((three ? "dragons" : "dragon") + " spawned, /dm stop removes it");
        return Command.SINGLE_SUCCESS;
    }

    // /dm dragon ride [fire|ice|ender]: look where to fly, forward is faster, jump climbs, nothing glides,
    // left click roars fire, sneak lands it and lets you off
    private int ride(CommandSourceStack source, Dragon.Kind kind) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        UUID id = player.getUniqueId();
        clearShowcase(id);
        clearRide(id);
        player.leaveVehicle();
        rides.put(id, DragonRide.start(this, motion, player, kind, () -> rides.remove(id)));
        player.sendPlainMessage("look where to fly, W faster, space up, left click fire, sneak to land");
        return Command.SINGLE_SUCCESS;
    }

    private static Vector forward(Location at, float z) {
        double yaw = Math.toRadians(at.getYaw());
        return new Vector(-Math.sin(yaw) * z, 0, Math.cos(yaw) * z);
    }

    // x blocks to the right of where the location looks
    private static Vector sideways(Location at, float x) {
        double yaw = Math.toRadians(at.getYaw());
        return new Vector(-Math.cos(yaw) * x, 0, -Math.sin(yaw) * x);
    }

    // a fake top 8 that changes every 3 seconds, so you can see rows glide and scores count up
    private int leaderboard(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        UUID id = player.getUniqueId();
        clearBoard(id);
        Location at = inFront(player, 5).add(0, 3.2, 0);
        Leaderboard board = motion.leaderboard(at).rows(8).title(Component.text("Top kills", NamedTextColor.GOLD));
        List<String> names = new ArrayList<>(List.of("Notch", "jeb_", "Dinnerbone", "Grumm", "Steve", "Alex", "Herobrine", "Technoblade", "Dream"));
        names.add(player.getName());
        Map<String, Long> scores = new HashMap<>();
        java.util.Random random = new java.util.Random();
        for (String name : names) {
            scores.put(name, 50L + random.nextInt(950));
        }
        Runnable push = () -> board.update(names.stream()
                .map(name -> Leaderboard.Entry.of(name, Component.text(name, NamedTextColor.WHITE), scores.get(name))
                        .icon(Leaderboard.head(Bukkit.getOfflinePlayer(name))))
                .toList());
        push.run();
        boards.put(id, board);
        boardTasks.put(id, getServer().getScheduler().runTaskTimer(this, () -> {
            for (int i = 0; i < 3; i++) {
                String lucky = names.get(random.nextInt(names.size()));
                scores.merge(lucky, 40L + random.nextInt(260), Long::sum);
            }
            push.run();
        }, 60L, 60L));
        player.sendPlainMessage("leaderboard spawned, /dm stop removes it");
        return Command.SINGLE_SUCCESS;
    }

    private boolean clearBoard(UUID id) {
        BukkitTask task = boardTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
        Leaderboard board = boards.remove(id);
        if (board == null) {
            return false;
        }
        board.remove();
        return true;
    }

    // hat or backpack on you: what you see of it yourself, others always see it
    private int selfView(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        MotionDisplay display = displays.get(player.getUniqueId());
        if (display == null || display.isRemoved() || display.vehicle() == null) {
            player.sendPlainMessage("wear something first, /dm hat or /dm backpack");
            return 0;
        }
        SelfView next = switch (display.selfView()) {
            case SHOW -> SelfView.HIDE_LOOKING_DOWN;
            case HIDE_LOOKING_DOWN -> SelfView.HIDE;
            case HIDE -> SelfView.SHOW;
        };
        display.selfView(next);
        player.sendPlainMessage(switch (next) {
            case SHOW -> "you see it on you";
            case HIDE_LOOKING_DOWN -> "you see it on you, but not when you look down";
            case HIDE -> "only others see it";
        });
        return Command.SINGLE_SUCCESS;
    }

    private int stop(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendPlainMessage("players only");
            return 0;
        }
        boolean showcase = clearShowcase(player.getUniqueId()) | clearBoard(player.getUniqueId()) | clearRide(player.getUniqueId());
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

    // floats over your right shoulder and trails a bit behind when you walk
    private MotionDisplay pet(Player player) {
        return motion.item(petSpot(player), ItemStack.of(Material.DIAMOND_SWORD), ItemDisplay.ItemDisplayTransform.GROUND)
                .play(SHOW)
                .follow(player, new Vector(0.7, 1.7, -0.6), 5);
    }

    // flies one loop around you on a smooth curve, up and down on the way, and stops in front of you again
    private MotionDisplay path(Player player) {
        Location center = player.getLocation();
        List<Location> loop = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            double angle = i * Math.PI / 3;
            Vector around = forward(center, 3f * (float) Math.cos(angle)).add(sideways(center, 3f * (float) Math.sin(angle)));
            loop.add(center.clone().add(around).add(0, 1.5 + Math.sin(angle * 2) * 1.2, 0));
        }
        return motion.item(inFront(player, 3).add(0, 1.5, 0), ItemStack.of(Material.DIAMOND_SWORD), ItemDisplay.ItemDisplayTransform.GROUND)
                .play(SHOW)
                .moveAlong(loop, 120, Ease.IN_OUT, () -> player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f));
    }

    // the dispenser front faces south, thats yaw 0, so lookAt points it right at you
    private MotionDisplay turret(Player player) {
        Directional front = (Directional) Material.DISPENSER.createBlockData();
        front.setFacing(BlockFace.SOUTH);
        return motion.model(inFront(player, 4).add(0, 1.5, 0))
                .block("body", front, box(0f, 0f, 0f, 0.8f, 0.8f, 0.8f))
                .block("barrel", Material.IRON_BLOCK.createBlockData(), box(0f, 0f, 0.55f, 0.25f, 0.25f, 0.4f))
                .spawn()
                .play(Animations.popIn(10))
                .lookAt(player);
    }

    // a crown on the head, it turns where the player looks. 0 is the top of the hitbox (1.8), the drawn head
    // goes up to 1.875 so the crown sits a bit higher or it ends up inside the head
    private MotionDisplay hat(Player player) {
        ModelBuilder crown = motion.model(player.getLocation())
                .block("band", Material.GOLD_BLOCK.createBlockData(), box(0f, 0.04f, 0f, 0.52f, 0.12f, 0.52f))
                .block("velvet", Material.RED_WOOL.createBlockData(), box(0f, 0.11f, 0f, 0.44f, 0.06f, 0.44f))
                .block("ruby", Material.REDSTONE_BLOCK.createBlockData(), box(0f, 0.04f, 0.265f, 0.08f, 0.06f, 0.02f))
                .block("sapphire", Material.LAPIS_BLOCK.createBlockData(), box(0f, 0.04f, -0.265f, 0.08f, 0.06f, 0.02f))
                .block("emerald_left", Material.EMERALD_BLOCK.createBlockData(), box(-0.265f, 0.04f, 0f, 0.02f, 0.06f, 0.08f))
                .block("emerald_right", Material.EMERALD_BLOCK.createBlockData(), box(0.265f, 0.04f, 0f, 0.02f, 0.06f, 0.08f));
        int spike = 0;
        for (float x : new float[]{-0.21f, 0.21f}) {
            for (float z : new float[]{-0.21f, 0.21f}) {
                crown.block("spike_" + spike, Material.GOLD_BLOCK.createBlockData(), box(x, 0.18f, z, 0.1f, 0.16f, 0.1f));
                crown.block("tip_" + spike++, Material.GOLD_BLOCK.createBlockData(), box(x, 0.285f, z, 0.05f, 0.05f, 0.05f));
            }
        }
        float[][] sides = {{0f, 0.21f}, {0f, -0.21f}, {0.21f, 0f}, {-0.21f, 0f}};
        for (float[] side : sides) {
            crown.block("spike_" + spike, Material.GOLD_BLOCK.createBlockData(), box(side[0], 0.15f, side[1], 0.08f, 0.1f, 0.08f));
            crown.block("tip_" + spike++, Material.GOLD_BLOCK.createBlockData(), box(side[0], 0.22f, side[1], 0.04f, 0.04f, 0.04f));
        }
        return crown.item("gem", ItemStack.of(Material.DIAMOND), Pose.IDENTITY.withOffset(0f, 0.36f, 0f).withScale(0.3f))
                .spawn()
                .ride(player, Follow.HEAD)
                .play(Animations.popIn(10))
                .play("gem", Animations.spin(40).with(Animations.bob(0.04f, 40)));
    }

    // sits on the back and turns with the body, not with the head. the back of the skin is at z -0.117,
    // the shoulders at y -0.393 and the waist at -1.096 from the passenger point
    private MotionDisplay backpack(Player player) {
        ModelBuilder pack = motion.model(player.getLocation())
                .block("bag", Material.BROWN_TERRACOTTA.createBlockData(), box(0f, -0.73f, -0.24f, 0.44f, 0.56f, 0.24f))
                .block("lid", Material.BROWN_WOOL.createBlockData(), box(0f, -0.435f, -0.245f, 0.46f, 0.03f, 0.26f))
                .block("flap", Material.BROWN_WOOL.createBlockData(), box(0f, -0.55f, -0.365f, 0.42f, 0.22f, 0.02f))
                .block("buckle", Material.GOLD_BLOCK.createBlockData(), box(0f, -0.64f, -0.38f, 0.06f, 0.05f, 0.015f))
                .block("pocket", Material.BROWN_WOOL.createBlockData(), box(0f, -0.86f, -0.375f, 0.3f, 0.18f, 0.04f))
                .block("bedroll", Material.RED_WOOL.createBlockData(), box(0f, -1.06f, -0.25f, 0.5f, 0.1f, 0.12f));
        for (float x : new float[]{-0.12f, 0.12f}) {
            String side = x < 0 ? "left" : "right";
            pack.block("strap_back_" + side, Material.DARK_OAK_PLANKS.createBlockData(), box(x, -0.73f, -0.365f, 0.04f, 0.5f, 0.012f))
                    .block("strap_shoulder_" + side, Material.DARK_OAK_PLANKS.createBlockData(), box(x, -0.385f, 0f, 0.06f, 0.02f, 0.26f))
                    .block("strap_chest_" + side, Material.DARK_OAK_PLANKS.createBlockData(), box(x, -0.52f, 0.125f, 0.06f, 0.26f, 0.015f));
        }
        return pack.spawn()
                .ride(player, Follow.BODY)
                .play(Animations.popIn(10));
    }

    private static Pose box(float x, float y, float z, float sizeX, float sizeY, float sizeZ) {
        return Pose.IDENTITY.withOffset(x, y, z).withScale(sizeX, sizeY, sizeZ);
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

    private MotionDisplay text(Player player, DisplayOptions options) {
        Component message = options.text() != null
                ? Component.text(options.text(), NamedTextColor.WHITE)
                : Component.text("Made with ", NamedTextColor.WHITE).append(Component.text("displaymotion", NamedTextColor.GOLD));
        return motion.text(player.getLocation().add(0, 2, 0), Component.empty())
                .billboard(Display.Billboard.CENTER)
                .play(SHOW_TEXT)
                .type(message, 2);
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
            model.play(display, clips.get(clip[0]), 8);
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
        model.detail("door_frame", "rod");
        for (int side = -1; side <= 1; side += 2) {
            window(model, "window_lower_" + side, side * 0.9f, 1.6f);
            window(model, "window_upper_" + side, side * 0.75f, 3.1f);
        }

        model.block("axle", Bukkit.createBlockData("minecraft:dark_oak_log[axis=z]"), Pose.IDENTITY
                .withOffset(0f, 3.3f, 0.95f).withScale(0.32f, 0.32f, 0.7f));
        for (int i = 0; i < 4; i++) {
            sail(model, i);
        }

        // past 24 blocks the sails stand still and the small parts go away, near players see it all
        return model.spawn()
                .lod(24)
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
        // too small to see from far away anyway
        model.detail(name + "_frame", name);
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
