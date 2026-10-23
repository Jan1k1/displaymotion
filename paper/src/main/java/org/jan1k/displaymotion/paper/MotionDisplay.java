package org.jan1k.displaymotion.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Ease;
import org.jan1k.displaymotion.Frames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * A spawned display or model. It is not a real entity, only packets.
 * Every method is safe from any thread. After {@link #remove()} the methods throw {@link IllegalStateException}, except {@code remove()}.
 */
public final class MotionDisplay {

    public static final int MAX_TELEPORT_TICKS = 59;
    private static final float MIN_LOOK_TURN = 0.5f;
    /** The longest {@link #crossfade(Animation, int)}. */
    public static final int MAX_FADE_TICKS = 200;
    /** The biggest size for {@link #scale(float)}. */
    public static final float MAX_SCALE = 32f;
    /** The longest a {@link #scale(float, int)} change can take. */
    public static final int MAX_SCALE_TICKS = 200;
    /** The fastest speed for {@link #speed(double)}. */
    public static final double MAX_SPEED = 10.0;
    private static final float MAX_SHADOW_RADIUS = 64f;
    /** Where {@link #follow(Entity)} puts it: a bit above and behind, like a pet. */
    public static final Vector DEFAULT_FOLLOW_OFFSET = new Vector(0.0, 1.6, -1.2);
    public static final int DEFAULT_FOLLOW_GLIDE = 4;
    // a counting number changes every this many ticks, every tick would be a lot of packets for little
    private static final int COUNT_EVERY_TICKS = 2;
    /** The biggest hitbox side, in blocks. */
    public static final float MAX_HITBOX_SIZE = 16f;
    /** The most points for {@link #moveAlong(List, int)}. */
    public static final int MAX_PATH_POINTS = 256;

    // a bit more then survival reach, so a click on the far edge of a big box still counts
    private static final double CLICK_REACH = 8.0;
    // less then this is not worth a packet, the client cant even show a turn of under a degree
    private static final float RIDE_TURN = 0.5f;
    private static final double RIDE_MOVE = 0.5;
    // less then this is not worth a move packet
    private static final double FOLLOW_STILL = 0.01;
    // where a player head and body turn, from the 1.21.11 player model (scale 0.9375, 1.501 down), in blocks
    // from the passenger point. it sits 1.8 up standing and 1.5 sneaking, but the model does not
    // move the same amount, so a sneaking hat has to come down a bit and the body leans forward.
    // the drop is the sneaking pivot minus the standing one (-0.3928)
    static final float HEAD_PIVOT = -0.3928125f;
    // a hat for its own wearer runs ahead by the head turn speed times this many ticks, at most
    private static final float MAX_LEAD_TICKS = 10f;
    // half a tick for the server tick and one for the client blend, on top of the ping
    private static final float LEAD_EXTRA_TICKS = 1.5f;
    private static final float MAX_LEAD_DEGREES = 60f;
    private static final float HEAD_SPEED_SMOOTHING = 0.5f;
    // from 35 degrees down the top of a backpack comes into the first person view
    private static final float LOOK_DOWN_HIDE = 35f;
    static final float HEAD_PIVOT_SNEAKING = -0.46390625f;
    static final float HEAD_DROP_SNEAKING = -0.07109375f;
    static final float BODY_PIVOT_SNEAKING = -0.4053125f;
    static final float BODY_DROP_SNEAKING = -0.0125f;
    // HumanoidModel leans the body 0.5 radians
    static final float BODY_LEAN_SNEAKING = 28.64789f;

    private final MotionEngine engine;
    private volatile Location location;
    // a glide step on the motion thread can land right after a teleport and put location() back on the glide,
    // so every move gets a number and only the newest glide can write it
    private final Object moveLock = new Object();
    private int moves;
    private final DisplayState state;
    private final Map<String, Integer> partIndex = new HashMap<>();
    private final List<String> parts;
    private final Set<String> hitboxes = ConcurrentHashMap.newKeySet();
    private volatile Consumer<Click> onClick;
    private volatile Consumer<DisplayEvent> onEvent;
    private final Map<String, double[]> seats = new ConcurrentHashMap<>();
    private final Map<String, Entity> seatEntities = new ConcurrentHashMap<>();
    private volatile Entity vehicle;
    private volatile Entity followed;
    private volatile Entity watched;
    // main thread only
    private int watchTick;
    private volatile Vector followOffset;
    private volatile int followGlide;
    // main thread only
    private int followTick;
    private double followX;
    private double followY;
    private double followZ;
    private float followYaw;
    private volatile Follow follow = Follow.BODY;
    // null is the default for the follow, see effectiveSelfView
    private volatile SelfView selfView;
    // main thread only, what the last ride update sent
    private UUID lastWorld;
    private double lastX;
    private double lastY;
    private double lastZ;
    private RidePose lastPose = RidePose.turn(0f);
    // how fast the head turns, in degrees per tick, smoothed. main thread only
    private float headSpeedYaw;
    private float headSpeedPitch;
    private float lastHeadYaw = Float.NaN;
    private float lastHeadPitch;

    volatile boolean playing;
    private volatile boolean visibleByDefault = true;
    // copy of the engine sets for clicks on the main thread
    private final Set<UUID> shownTo = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hiddenFrom = ConcurrentHashMap.newKeySet();
    private volatile double speed = 1.0;
    private volatile boolean removed;

    MotionDisplay(MotionEngine engine, Location location, PartState[] parts, double range) {
        this.engine = engine;
        this.location = location;
        this.state = new DisplayState(location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(),
                parts, range, this);
        String[] names = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            names[i] = parts[i].name;
            partIndex.put(parts[i].name, i);
        }
        this.parts = List.of(names);
    }

    DisplayState state() {
        return state;
    }

    /** Plays on the whole display and replaces what plays there now. It blends from the current pose over 2 ticks. */
    public MotionDisplay play(Animation animation) {
        return play(animation, null);
    }

    /**
     * Same as {@link #play(Animation)}. {@code onEnd} runs once on the main thread when the animation reaches its end.
     * It never runs for an endless animation, or after {@link #stop()} or a new play.
     */
    public MotionDisplay play(Animation animation, Runnable onEnd) {
        check(animation);
        playing = true;
        engine.submit(() -> engine.play(state, 0, animation, onEnd));
        return this;
    }

    /** Plays on one part. Its children follow it. */
    public MotionDisplay play(String part, Animation animation) {
        return play(part, animation, null);
    }

    /** Plays on one part and runs {@code onEnd} on the main thread when it ends. */
    public MotionDisplay play(String part, Animation animation, Runnable onEnd) {
        int track = track(part);
        check(animation);
        engine.submit(() -> engine.play(state, track, animation, onEnd));
        return this;
    }

    /**
     * Plays on the whole display and fades into it from what plays now over {@code ticks}, so a switch
     * from walking to waving is soft and not a jump. With nothing playing before, it fades in from the rest pose.
     */
    public MotionDisplay crossfade(Animation animation, int ticks) {
        checkFade(ticks);
        check(animation);
        playing = true;
        engine.submit(() -> engine.play(state, 0, animation, null, ticks));
        return this;
    }

    /** Same as {@link #crossfade(Animation, int)} on one part. */
    public MotionDisplay crossfade(String part, Animation animation, int ticks) {
        int track = track(part);
        checkFade(ticks);
        check(animation);
        engine.submit(() -> engine.play(state, track, animation, null, ticks));
        return this;
    }

    private static void checkFade(int ticks) {
        if (ticks < 0 || ticks > MAX_FADE_TICKS) {
            throw new IllegalArgumentException("a crossfade is 0 to " + MAX_FADE_TICKS + " ticks, got " + ticks);
        }
    }

    /**
     * Makes the whole display bigger or smaller around its location, on top of what plays. 1 is the normal size,
     * 2 twice as big, 0.5 half. Hitboxes and seats keep their size.
     */
    public MotionDisplay scale(float size) {
        return scale(size, 0);
    }

    /** Same as {@link #scale(float)} but it grows or shrinks smoothly over {@code ticks}. */
    public MotionDisplay scale(float size, int ticks) {
        return scale(MotionEngine.ALL_PARTS, size, ticks);
    }

    /** The size of one part. Its children grow with it. */
    public MotionDisplay scale(String part, float size) {
        return scale(part, size, 0);
    }

    /** The size of one part, changed smoothly over {@code ticks}. */
    public MotionDisplay scale(String part, float size, int ticks) {
        return scale(index(part), size, ticks);
    }

    private MotionDisplay scale(int part, float size, int ticks) {
        if (!(size >= 0f && size <= MAX_SCALE)) {
            throw new IllegalArgumentException("size is 0 to " + MAX_SCALE + ", got " + size);
        }
        if (ticks < 0 || ticks > MAX_SCALE_TICKS) {
            throw new IllegalArgumentException("a size change takes 0 to " + MAX_SCALE_TICKS + " ticks, got " + ticks);
        }
        requireAlive();
        engine.submit(() -> engine.scale(state, part, size, ticks));
        return this;
    }

    /**
     * How fast animations play on this display. 1 is normal, 0.5 half as fast, 2 twice as fast and 0 holds them
     * where they are. A slow animation still moves smoothly, it blends between its frames.
     */
    public MotionDisplay speed(double speed) {
        if (!(speed >= 0.0 && speed <= MAX_SPEED)) {
            throw new IllegalArgumentException("speed is 0 to " + MAX_SPEED + ", got " + speed);
        }
        requireAlive();
        this.speed = speed;
        engine.submit(() -> engine.speed(state, speed));
        return this;
    }

    /** The speed set with {@link #speed(double)}, 1 by default. */
    public double speed() {
        return speed;
    }

    /** Freezes every part on its current pose. */
    public MotionDisplay stop() {
        requireAlive();
        playing = false;
        engine.submit(() -> engine.stop(state, MotionEngine.ALL_TRACKS));
        return this;
    }

    /** Freezes one part on its current pose. */
    public MotionDisplay stop(String part) {
        int track = track(part);
        requireAlive();
        engine.submit(() -> engine.stop(state, track));
        return this;
    }

    /** Outlines every part in this color. {@code null} turns the outline off. Text parts do not glow. */
    public MotionDisplay glow(Color color) {
        return glow(MotionEngine.ALL_PARTS, color);
    }

    /** Outlines one part. {@code null} turns it off. */
    public MotionDisplay glow(String part, Color color) {
        return glow(index(part), color);
    }

    /** Draws every part with a fixed light level. {@code null} uses the light of the world. */
    public MotionDisplay brightness(Display.Brightness brightness) {
        return brightness(MotionEngine.ALL_PARTS, brightness);
    }

    /** Fixed light for one part. {@code null} uses the light of the world. */
    public MotionDisplay brightness(String part, Display.Brightness brightness) {
        return brightness(index(part), brightness);
    }

    /** Makes every part turn to the camera. {@code FIXED} is the default. */
    public MotionDisplay billboard(Display.Billboard billboard) {
        return billboard(MotionEngine.ALL_PARTS, billboard);
    }

    /** Makes one part turn to the camera. */
    public MotionDisplay billboard(String part, Display.Billboard billboard) {
        return billboard(index(part), billboard);
    }

    /** Background behind every text part, alpha included. {@code null} is the vanilla dark box. Use alpha 0 for none. */
    public MotionDisplay background(Color color) {
        return background(MotionEngine.ALL_PARTS, color);
    }

    /** Background behind one text part. */
    public MotionDisplay background(String part, Color color) {
        return background(index(part), color);
    }

    /** Wraps the lines of every text part after this many pixels. The default is 200. */
    public MotionDisplay lineWidth(int width) {
        return lineWidth(MotionEngine.ALL_PARTS, width);
    }

    /** Line width of one text part. */
    public MotionDisplay lineWidth(String part, int width) {
        return lineWidth(textPart(part), width);
    }

    /** How solid the text of every text part is, 0 to 255. 255 is the default. */
    public MotionDisplay textOpacity(int alpha) {
        return textOpacity(MotionEngine.ALL_PARTS, alpha);
    }

    /** Text opacity of one text part. */
    public MotionDisplay textOpacity(String part, int alpha) {
        return textOpacity(textPart(part), alpha);
    }

    /** Draws a shadow behind the letters of every text part. */
    public MotionDisplay textShadow(boolean shadow) {
        return textStyle(MotionEngine.ALL_PARTS, p -> p.textShadow = shadow);
    }

    /** Letter shadow of one text part. */
    public MotionDisplay textShadow(String part, boolean shadow) {
        return textStyle(textPart(part), p -> p.textShadow = shadow);
    }

    /** Lets every text part be seen through blocks. */
    public MotionDisplay seeThrough(boolean seeThrough) {
        return textStyle(MotionEngine.ALL_PARTS, p -> p.seeThrough = seeThrough);
    }

    /** Lets one text part be seen through blocks. */
    public MotionDisplay seeThrough(String part, boolean seeThrough) {
        return textStyle(textPart(part), p -> p.seeThrough = seeThrough);
    }

    /** Aligns the lines of every text part. {@code CENTER} is the default. */
    public MotionDisplay alignment(TextDisplay.TextAlignment alignment) {
        Objects.requireNonNull(alignment, "alignment");
        return textStyle(MotionEngine.ALL_PARTS, p -> p.alignment = alignment);
    }

    /** Aligns the lines of one text part. */
    public MotionDisplay alignment(String part, TextDisplay.TextAlignment alignment) {
        Objects.requireNonNull(alignment, "alignment");
        return textStyle(textPart(part), p -> p.alignment = alignment);
    }

    /**
     * Draws a round shadow on the ground under every part. Radius is 0 to 64 blocks, 0 is no shadow.
     * Strength is 0 or more, 1 is the default.
     * @throws IllegalArgumentException if a number is out of range
     */
    public MotionDisplay shadow(float radius, float strength) {
        return shadow(MotionEngine.ALL_PARTS, radius, strength);
    }

    /** Shadow of one part. */
    public MotionDisplay shadow(String part, float radius, float strength) {
        return shadow(index(part), radius, strength);
    }

    /**
     * Swaps the item of every item part.
     * @throws IllegalArgumentException if the display has no item part
     */
    public MotionDisplay item(ItemStack item) {
        return item(MotionEngine.ALL_PARTS, item);
    }

    /** Swaps the item of one item part. */
    public MotionDisplay item(String part, ItemStack item) {
        int index = index(part);
        if (state.parts[index].kind != PartState.ITEM) {
            throw new IllegalArgumentException("part '" + part + "' is not an item part");
        }
        return item(index, item);
    }

    /**
     * Swaps the block of every block part.
     * @throws IllegalArgumentException if the display has no block part
     */
    public MotionDisplay block(BlockData block) {
        return block(MotionEngine.ALL_PARTS, block);
    }

    /** Swaps the block of one block part. */
    public MotionDisplay block(String part, BlockData block) {
        int index = index(part);
        if (state.parts[index].kind != PartState.BLOCK) {
            throw new IllegalArgumentException("part '" + part + "' is not a block part");
        }
        return block(index, block);
    }

    private MotionDisplay lineWidth(int part, int width) {
        if (width < 1) {
            throw new IllegalArgumentException("line width must be at least 1, got " + width);
        }
        return textStyle(part, p -> p.lineWidth = width);
    }

    private MotionDisplay textOpacity(int part, int alpha) {
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("text opacity is 0 to 255, got " + alpha);
        }
        return textStyle(part, p -> p.textOpacity = alpha);
    }

    // only the text parts take it, the others stay as they are
    private MotionDisplay textStyle(int part, Consumer<PartState> change) {
        requireAlive();
        engine.submit(() -> engine.look(state, part, p -> {
            if (p.kind == PartState.TEXT) {
                change.accept(p);
            }
        }));
        return this;
    }

    private MotionDisplay shadow(int part, float radius, float strength) {
        if (!(radius >= 0f && radius <= MAX_SHADOW_RADIUS)) {
            throw new IllegalArgumentException("shadow radius is 0 to " + MAX_SHADOW_RADIUS + ", got " + radius);
        }
        if (!(strength >= 0f && strength <= Float.MAX_VALUE)) {
            throw new IllegalArgumentException("shadow strength must be 0 or more, got " + strength);
        }
        requireAlive();
        engine.submit(() -> engine.look(state, part, p -> {
            if (p.isDisplay()) {
                p.shadowRadius = radius;
                p.shadowStrength = strength;
            }
        }));
        return this;
    }

    private MotionDisplay item(int part, ItemStack item) {
        Objects.requireNonNull(item, "item");
        if (part == MotionEngine.ALL_PARTS && !hasKind(PartState.ITEM)) {
            throw new IllegalArgumentException("this display has no item part");
        }
        requireAlive();
        var converted = SpigotConversionUtil.fromBukkitItemStack(item);
        engine.submit(() -> engine.content(state, part, PartState.ITEM, p -> p.item = converted));
        return this;
    }

    private MotionDisplay block(int part, BlockData block) {
        Objects.requireNonNull(block, "block");
        if (part == MotionEngine.ALL_PARTS && !hasKind(PartState.BLOCK)) {
            throw new IllegalArgumentException("this display has no block part");
        }
        requireAlive();
        int blockState = SpigotConversionUtil.fromBukkitBlockData(block).getGlobalId();
        engine.submit(() -> engine.content(state, part, PartState.BLOCK, p -> p.blockState = blockState));
        return this;
    }

    private boolean hasKind(int kind) {
        for (PartState part : state.parts) {
            if (part.kind == kind) {
                return true;
            }
        }
        return false;
    }

    private MotionDisplay background(int part, Color color) {
        requireAlive();
        int argb = color == null ? PartState.DEFAULT_BACKGROUND : color.asARGB();
        engine.submit(() -> engine.look(state, part, p -> p.background = argb));
        return this;
    }

    /**
     * Changes the text of every text part.
     * @throws IllegalArgumentException if the display has no text part or the text is too big
     */
    public MotionDisplay text(Component text) {
        return text(MotionEngine.ALL_PARTS, text);
    }

    /** Changes the text of one text part. */
    public MotionDisplay text(String part, Component text) {
        int index = index(part);
        if (state.parts[index].kind != PartState.TEXT) {
            throw new IllegalArgumentException("part '" + part + "' is not a text part");
        }
        return text(index, text);
    }

    /**
     * Gives one player its own text on every text part, the other players keep the shared text.
     * Good for names, coins or placeholders. {@code null} gives the player the shared text again.
     * The text goes away when the player leaves the server.
     * @throws IllegalArgumentException if the display has no text part or the text is too big
     */
    public MotionDisplay text(Player player, Component text) {
        return textFor(player, MotionEngine.ALL_PARTS, text);
    }

    /** Gives one player its own text on one text part, see {@link #text(Player, Component)}. */
    public MotionDisplay text(Player player, String part, Component text) {
        int index = index(part);
        if (state.parts[index].kind != PartState.TEXT) {
            throw new IllegalArgumentException("part '" + part + "' is not a text part");
        }
        return textFor(player, index, text);
    }

    private MotionDisplay textFor(Player player, int part, Component text) {
        Objects.requireNonNull(player, "player");
        if (text != null) {
            ModelBuilder.checkText(text);
        }
        if (part == MotionEngine.ALL_PARTS && !hasText()) {
            throw new IllegalArgumentException("this display has no text part");
        }
        requireAlive();
        UUID id = player.getUniqueId();
        engine.submit(() -> engine.textFor(state, part, id, text));
        return this;
    }

    private MotionDisplay glow(int part, Color color) {
        requireAlive();
        boolean on = color != null;
        int rgb = on ? color.asRGB() : -1;
        engine.submit(() -> engine.look(state, part, p -> {
            p.glowing = on;
            p.glowColor = rgb;
        }));
        return this;
    }

    private MotionDisplay brightness(int part, Display.Brightness brightness) {
        requireAlive();
        // packed same way vanilla does it, -1 means use the world light
        int packed = brightness == null ? -1
                : brightness.getBlockLight() << 4 | brightness.getSkyLight() << 20;
        engine.submit(() -> engine.look(state, part, p -> p.brightness = packed));
        return this;
    }

    private MotionDisplay billboard(int part, Display.Billboard billboard) {
        Objects.requireNonNull(billboard, "billboard");
        requireAlive();
        int id = billboard.ordinal();
        engine.submit(() -> engine.look(state, part, p -> p.billboard = id));
        return this;
    }

    private MotionDisplay text(int part, Component text) {
        ModelBuilder.checkText(text);
        if (part == MotionEngine.ALL_PARTS && !hasText()) {
            throw new IllegalArgumentException("this display has no text part");
        }
        requireAlive();
        engine.submit(() -> engine.text(state, part, text));
        return this;
    }

    /**
     * Types the text one char at a time, a char every {@code ticksPerChar}. Colors and styles stay.
     * Same as {@link #type(String, Component, int)} on every text part.
     */
    public MotionDisplay type(Component text, int ticksPerChar) {
        return type(MotionEngine.ALL_PARTS, text, ticksPerChar, null);
    }

    /** Types the text into one text part, a char every {@code ticksPerChar}. */
    public MotionDisplay type(String part, Component text, int ticksPerChar) {
        return type(textPart(part), text, ticksPerChar, null);
    }

    /** Same as {@link #type(String, Component, int)}, and runs {@code onEnd} on the main thread when the last char shows. */
    public MotionDisplay type(String part, Component text, int ticksPerChar, Runnable onEnd) {
        return type(textPart(part), text, ticksPerChar, onEnd);
    }

    private MotionDisplay type(int part, Component text, int ticksPerChar, Runnable onEnd) {
        ModelBuilder.checkText(text);
        if (ticksPerChar < 1) {
            throw new IllegalArgumentException("ticks per char must be 1 or more, got " + ticksPerChar);
        }
        if (part == MotionEngine.ALL_PARTS && !hasText()) {
            throw new IllegalArgumentException("this display has no text part");
        }
        requireAlive();
        TextCut cut = new TextCut(text);
        int ticks = Math.max(1, cut.length() * ticksPerChar);
        checkTextTicks(ticks);
        engine.submit(() -> engine.animateText(state, part, ticks, ticksPerChar,
                tick -> tick / ticksPerChar + 1 >= cut.length() ? text : cut.first(tick / ticksPerChar + 1), onEnd));
        return this;
    }

    /**
     * Counts a number up (or down) in a text part over {@code ticks}, fast at first and slow at the end.
     * {@code format} turns the number into the text, for example {@code n -> Component.text(n + " coins")}.
     */
    public MotionDisplay count(String part, long from, long to, int ticks, LongFunction<Component> format) {
        int index = textPart(part);
        Objects.requireNonNull(format, "format");
        if (ticks < 1) {
            throw new IllegalArgumentException("ticks must be 1 or more, got " + ticks);
        }
        checkTextTicks(ticks);
        requireAlive();
        engine.submit(() -> engine.animateText(state, index, ticks, COUNT_EVERY_TICKS, tick -> {
            float t = Math.min(1f, tick / (float) ticks);
            float eased = Ease.OUT.apply(t);
            return format.apply(from + Math.round((to - from) * (double) eased));
        }, null));
        return this;
    }

    private int textPart(String part) {
        int index = index(part);
        if (state.parts[index].kind != PartState.TEXT) {
            throw new IllegalArgumentException("part '" + part + "' is not a text part");
        }
        return index;
    }

    private static void checkTextTicks(int ticks) {
        if (ticks > Frames.MAX_TICKS) {
            throw new IllegalArgumentException("a text animation can be at most " + Frames.MAX_TICKS + " ticks, got " + ticks);
        }
    }

    private boolean hasText() {
        for (PartState part : state.parts) {
            if (part.kind == PartState.TEXT) {
                return true;
            }
        }
        return false;
    }

    /** Moves the whole display at once. Another world works too. */
    public MotionDisplay teleport(Location to) {
        return teleport(to, 0);
    }

    /**
     * Moves the whole display and lets the client glide there over {@code ticks}, 0 to 59.
     * Call it again before the glide ends for smooth follow movement.
     */
    public MotionDisplay teleport(Location to, int ticks) {
        Objects.requireNonNull(to, "location");
        if (to.getWorld() == null) {
            throw new IllegalArgumentException("location has no world");
        }
        if (ticks < 0 || ticks > MAX_TELEPORT_TICKS) {
            throw new IllegalArgumentException("ticks must be 0 to " + MAX_TELEPORT_TICKS + " (the client limit), got " + ticks);
        }
        requireAlive();
        if (vehicle != null) {
            vehicle = null;
            engine.riding.remove(this);
            engine.submit(() -> engine.dismount(state, false));
        }
        if (followed != null) {
            unfollow();
        }
        place(to, ticks);
        return this;
    }

    private void place(Location to, int ticks) {
        Location copy = to.clone();
        moved(copy);
        if (!seatEntities.isEmpty()) {
            engine.mainThread.accept(this::moveSeats);
        }
        UUID world = copy.getWorld().getUID();
        double x = copy.getX();
        double y = copy.getY();
        double z = copy.getZ();
        float yaw = copy.getYaw();
        float pitch = copy.getPitch();
        engine.submit(() -> engine.teleport(state, world, x, y, z, yaw, pitch, ticks));
    }

    /** Glides the whole display from where it is now to {@code to} over {@code ticks}, keeping its rotation. */
    public MotionDisplay moveTo(Location to, int ticks) {
        return moveTo(to, ticks, Ease.LINEAR);
    }

    /**
     * Glides the whole display from where it is now to {@code to} over {@code ticks} with {@code ease},
     * keeping its rotation. A new {@code moveTo}, {@link #moveAlong(List, int)}, {@link #teleport(Location)} or
     * {@link #follow(Entity)} stops the old one.
     */
    public MotionDisplay moveTo(Location to, int ticks, Ease ease) {
        return moveTo(to, ticks, ease, null);
    }

    /**
     * Same as {@link #moveTo(Location, int, Ease)}. {@code onEnd} runs once on the main thread when it gets there.
     * It never runs when a new move, a ride or a follow stops the glide first.
     */
    public MotionDisplay moveTo(Location to, int ticks, Ease ease, Runnable onEnd) {
        Objects.requireNonNull(to, "location");
        Objects.requireNonNull(ease, "ease");
        if (to.getWorld() == null || !to.getWorld().getUID().equals(location.getWorld().getUID())) {
            throw new IllegalArgumentException("location must be in the same world");
        }
        checkGlideTicks(ticks);
        requireAlive();
        double x = to.getX();
        double y = to.getY();
        double z = to.getZ();
        int glide = startGlide();
        engine.submit(() -> engine.glide(state, x, y, z, ticks, ease, glide, onEnd));
        return this;
    }

    /** Turns the whole display at once to face {@code target}, see {@link #lookAt(Location, int)}. */
    public MotionDisplay lookAt(Location target) {
        return lookAt(target, 0);
    }

    /**
     * Turns the whole display to face {@code target}, yaw and pitch like a mob looks, and lets the client turn it
     * over {@code ticks}, 0 to 59. It stays where it is, a {@link #moveTo(Location, int)} or
     * {@link #moveAlong(List, int)} keeps going. While it rides or follows, those turn it instead.
     */
    public MotionDisplay lookAt(Location target, int ticks) {
        Objects.requireNonNull(target, "target");
        if (target.getWorld() == null || !target.getWorld().getUID().equals(location.getWorld().getUID())) {
            throw new IllegalArgumentException("target must be in the same world");
        }
        if (ticks < 0 || ticks > MAX_TELEPORT_TICKS) {
            throw new IllegalArgumentException("ticks must be 0 to " + MAX_TELEPORT_TICKS + " (the client limit), got " + ticks);
        }
        requireAlive();
        watched = null;
        engine.watching.remove(this);
        turnTo(target, ticks, 0f);
        return this;
    }

    /**
     * Keeps facing {@code target}, a living entity at its eyes. Every 2 ticks it turns if the target moved, the
     * players' games turn it smooth. A player keeps it after death, other targets stop it when they leave the world.
     * {@link #lookAt(Location)} or {@link #stopLooking()} stops it. Main thread.
     * @throws IllegalArgumentException if the entity is not in the world
     */
    public MotionDisplay lookAt(Entity target) {
        Objects.requireNonNull(target, "target");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("call lookAt on the main thread");
        }
        if (!target.isValid()) {
            throw new IllegalArgumentException("the target is not in the world");
        }
        return watch(target);
    }

    MotionDisplay watch(Entity target) {
        requireAlive();
        watched = target;
        watchTick = 0;
        engine.watching.add(this);
        watchTarget(true);
        return this;
    }

    /** Stops {@link #lookAt(Entity)}. It keeps the rotation it has now. */
    public MotionDisplay stopLooking() {
        watched = null;
        engine.watching.remove(this);
        return this;
    }

    /** What it keeps looking at, or {@code null}. */
    public Entity lookingAt() {
        return watched;
    }

    // main thread, every tick, but it only turns every 2 ticks like the frames
    void watchTarget(boolean now) {
        Entity target = watched;
        if (target == null) {
            return;
        }
        if (isRemoved()) {
            engine.watching.remove(this);
            return;
        }
        if (!target.isValid()) {
            if (!(target instanceof Player player && player.isOnline())) {
                stopLooking();
            }
            return;
        }
        if (!now && ++watchTick % Frames.STEP_TICKS != 0) {
            return;
        }
        Location eyes = target instanceof LivingEntity living ? living.getEyeLocation() : target.getLocation();
        if (eyes.getWorld() == null || !eyes.getWorld().getUID().equals(location.getWorld().getUID())) {
            return;
        }
        turnTo(eyes, now ? 0 : Frames.STEP_TICKS, now ? 0f : MIN_LOOK_TURN);
    }

    // skips turns smaller than minTurn degrees, a player that stands still would get a packet every 2 ticks for nothing
    private void turnTo(Location target, int ticks, float minTurn) {
        Location at;
        // no moves++ here, a glide that runs stays the newest and keeps writing the spot with this rotation
        synchronized (moveLock) {
            at = location.clone();
            Vector direction = target.toVector().subtract(at.toVector());
            if (direction.lengthSquared() < 1e-12) {
                return;
            }
            at.setDirection(direction);
            float yawTurn = Math.abs(Location.normalizeYaw(at.getYaw() - location.getYaw()));
            if (yawTurn < minTurn && Math.abs(at.getPitch() - location.getPitch()) < minTurn) {
                return;
            }
            location = at;
        }
        if (!seatEntities.isEmpty()) {
            engine.mainThread.accept(this::moveSeats);
        }
        float yaw = at.getYaw();
        float pitch = at.getPitch();
        engine.submit(() -> engine.turn(state, yaw, pitch, ticks));
    }

    /** Glides the whole display along a smooth curve through the points, see {@link #moveAlong(List, int, Ease)}. */
    public MotionDisplay moveAlong(List<Location> points, int ticks) {
        return moveAlong(points, ticks, Ease.LINEAR);
    }

    /**
     * Glides the whole display from where it is now along a smooth curve through {@code points} over {@code ticks},
     * keeping its rotation. It ends on the last point. With {@code LINEAR} it moves at the same speed the whole way,
     * other eases change the speed like on {@link #moveTo(Location, int, Ease)}. Good for flights, cutscenes and
     * projectiles. A new {@code moveAlong}, {@link #moveTo(Location, int)}, {@link #teleport(Location)} or
     * {@link #follow(Entity)} stops the old one.
     * @throws IllegalArgumentException if there are no points, more than {@link #MAX_PATH_POINTS}, or a point is in another world
     */
    public MotionDisplay moveAlong(List<Location> points, int ticks, Ease ease) {
        return moveAlong(points, ticks, ease, null);
    }

    /**
     * Same as {@link #moveAlong(List, int, Ease)}. {@code onEnd} runs once on the main thread when it gets to the
     * last point. It never runs when a new move, a ride or a follow stops the glide first.
     */
    public MotionDisplay moveAlong(List<Location> points, int ticks, Ease ease, Runnable onEnd) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(ease, "ease");
        if (points.isEmpty() || points.size() > MAX_PATH_POINTS) {
            throw new IllegalArgumentException("a path has 1 to " + MAX_PATH_POINTS + " points, got " + points.size());
        }
        UUID world = location.getWorld().getUID();
        double[] xyz = new double[points.size() * 3];
        for (int i = 0; i < points.size(); i++) {
            Location point = Objects.requireNonNull(points.get(i), "point");
            if (point.getWorld() == null || !point.getWorld().getUID().equals(world)) {
                throw new IllegalArgumentException("every point must be in the same world as the display");
            }
            xyz[i * 3] = point.getX();
            xyz[i * 3 + 1] = point.getY();
            xyz[i * 3 + 2] = point.getZ();
        }
        checkGlideTicks(ticks);
        requireAlive();
        int glide = startGlide();
        engine.submit(() -> engine.glideAlong(state, xyz, ticks, ease, glide, onEnd));
        return this;
    }

    private static void checkGlideTicks(int ticks) {
        if (ticks < 1 || ticks > Frames.MAX_TICKS) {
            throw new IllegalArgumentException("ticks must be 1 to " + Frames.MAX_TICKS + ", got " + ticks);
        }
    }

    private int startGlide() {
        if (vehicle != null) {
            vehicle = null;
            engine.riding.remove(this);
            engine.submit(() -> engine.dismount(state, false));
        }
        if (followed != null) {
            unfollow();
        }
        synchronized (moveLock) {
            return ++moves;
        }
    }

    private void moved(Location at) {
        synchronized (moveLock) {
            moves++;
            location = at;
        }
    }

    void movedTo(int glide, double x, double y, double z) {
        synchronized (moveLock) {
            if (glide != moves) {
                return;
            }
            Location at = location;
            location = new Location(at.getWorld(), x, y, z, at.getYaw(), at.getPitch());
        }
        if (!seatEntities.isEmpty()) {
            engine.mainThread.accept(this::moveSeats);
        }
    }

    /** Floats after the entity like a pet: a bit above and behind it. Same as {@code follow(target, DEFAULT_FOLLOW_OFFSET, DEFAULT_FOLLOW_GLIDE)}. */
    public MotionDisplay follow(Entity target) {
        return follow(target, DEFAULT_FOLLOW_OFFSET, DEFAULT_FOLLOW_GLIDE);
    }

    /**
     * Floats after the entity. Every 2 ticks it moves to the spot at {@code offset} from the entity and the
     * players' games glide it there over {@code glideTicks}, so it trails a bit behind. The offset turns with the
     * body: x to the right, y up, z forward (negative is behind). Unlike {@link #ride(Entity)} it lags a bit, which
     * looks alive for pets and drones. {@link #teleport(Location)} or {@link #unfollow()} stops it. Main thread.
     * @throws IllegalArgumentException if the glide is not 1 to 59 ticks or the entity is not in the world
     */
    public MotionDisplay follow(Entity target, Vector offset, int glideTicks) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(offset, "offset");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("call follow on the main thread");
        }
        if (glideTicks < 1 || glideTicks > MAX_TELEPORT_TICKS) {
            throw new IllegalArgumentException("glide must be 1 to " + MAX_TELEPORT_TICKS + " ticks, got " + glideTicks);
        }
        if (!target.isValid()) {
            throw new IllegalArgumentException("the target is not in the world");
        }
        requireAlive();
        if (vehicle != null) {
            dismount();
        }
        followed = target;
        followOffset = offset.clone();
        followGlide = glideTicks;
        followTick = 0;
        followX = Double.NaN;
        engine.following.add(this);
        followTarget(new Location(null, 0, 0, 0));
        return this;
    }

    /** Stops {@link #follow(Entity)}. It stays where it is. */
    public MotionDisplay unfollow() {
        followed = null;
        engine.following.remove(this);
        return this;
    }

    /** What it follows, or {@code null}. */
    public Entity followed() {
        return followed;
    }

    // main thread, every tick, but it only moves every 2 ticks like the frames
    void followTarget(Location scratch) {
        Entity target = followed;
        if (target == null) {
            return;
        }
        if (isRemoved()) {
            engine.following.remove(this);
            return;
        }
        if (!target.isValid()) {
            if (target instanceof Player player && player.isOnline()) {
                return;
            }
            unfollow();
            return;
        }
        boolean first = Double.isNaN(followX);
        if (!first && ++followTick % Frames.STEP_TICKS != 0) {
            return;
        }
        target.getLocation(scratch);
        float yaw = target instanceof Player player ? engine.bodies.yaw(player)
                : target instanceof LivingEntity living ? living.getBodyYaw() : scratch.getYaw();
        Vector offset = followOffset;
        // displays turn the other way round on x, so right is minus x before the turn
        double[] turned = Hitbox.turn(-offset.getX(), offset.getY(), offset.getZ(), yaw, 0f);
        double x = scratch.getX() + turned[0];
        double y = scratch.getY() + turned[1];
        double z = scratch.getZ() + turned[2];
        if (!first) {
            double dx = x - followX;
            double dy = y - followY;
            double dz = z - followZ;
            if (dx * dx + dy * dy + dz * dz < FOLLOW_STILL * FOLLOW_STILL && Math.abs(Bodies.wrap(yaw - followYaw)) < 1f
                    && scratch.getWorld().equals(location.getWorld())) {
                return;
            }
        }
        followX = x;
        followY = y;
        followZ = z;
        followYaw = yaw;
        place(new Location(scratch.getWorld(), x, y, z, yaw, 0f), first ? 0 : followGlide);
    }

    /**
     * Makes the display ride an entity, like a hat or a backpack, and turn with its body.
     * Same as {@code ride(vehicle, Follow.BODY)}.
     */
    public MotionDisplay ride(Entity vehicle) {
        return ride(vehicle, Follow.BODY);
    }

    /**
     * Makes the display ride an entity. The players' games move it with the entity every frame,
     * so walking costs the server nothing. Only a turn is sent, and only when it changes.
     * The display location is where a passenger sits: the top of the hitbox, 1.8 above the feet of a player. Move parts down
     * with their pose for a backpack. Real passengers of the entity stay on it.
     * Call it on the main thread. {@link #teleport(Location)} or {@link #dismount()} stops riding.
     * @throws IllegalArgumentException if the entity is not in the world
     */
    public MotionDisplay ride(Entity vehicle, Follow follow) {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(follow, "follow");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("call ride on the main thread");
        }
        requireAlive();
        if (!vehicle.isValid()) {
            throw new IllegalArgumentException("the vehicle is not in the world");
        }
        Location at = vehicle.getLocation();
        this.vehicle = vehicle;
        this.follow = follow;
        lastPose = RidePose.turn(at.getYaw());
        RidePose pose = selfPose(vehicle, at, poseOf(vehicle, at));
        at.setYaw(pose.yaw());
        at.setPitch(0f);
        moved(at.clone());
        remember(at, pose);
        int id = vehicle.getEntityId();
        int[] passengers = vehicle.getPassengers().stream().mapToInt(Entity::getEntityId).toArray();
        UUID world = at.getWorld().getUID();
        double x = at.getX();
        double y = at.getY();
        double z = at.getZ();
        engine.riding.add(this);
        UUID wearer = vehicle instanceof Player player ? player.getUniqueId() : null;
        engine.submit(() -> engine.ride(state, id, passengers, world, x, y, z, pose, wearer));
        // after ride, a ride from one entity to the next dismounts first and that clears the old wearer
        selfHide(vehicle);
        return this;
    }

    /**
     * Adds a place to sit at this offset from the display location. The offset turns with the display.
     * The hips of a sitting player end up about 0.1 above the seat point, so for a seat surface at 0.45 use 0.35.
     * Nothing happens until you call {@link #sit(Player, String)}, for example from {@link #onClick(Consumer)}.
     */
    public MotionDisplay seat(String name, double x, double y, double z) {
        Objects.requireNonNull(name, "name");
        requireAlive();
        seats.put(name, new double[]{x, y, z});
        return this;
    }

    /**
     * Puts the player on a seat. The player gets off with sneak, like from a horse.
     * The seat is a real invisible entity, the server needs one for a player to ride.
     * It is made the first time someone sits, moves with {@link #teleport(Location)} and goes away with the display.
     * Call it on the main thread.
     * @return false when someone already sits there
     * @throws IllegalArgumentException if there is no seat with that name
     */
    public boolean sit(Player player, String seat) {
        Objects.requireNonNull(player, "player");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("call sit on the main thread");
        }
        requireAlive();
        double[] offset = seats.get(seat);
        if (offset == null) {
            throw new IllegalArgumentException("no seat named '" + seat + "', seats are " + seats.keySet());
        }
        Entity entity = seatEntities.get(seat);
        if (entity == null || !entity.isValid()) {
            entity = location.getWorld().spawn(seatSpot(offset), ItemDisplay.class, made -> made.setPersistent(false));
            seatEntities.put(seat, entity);
            engine.seats.add(entity);
        }
        if (!entity.getPassengers().isEmpty()) {
            return false;
        }
        return entity.addPassenger(player);
    }

    private Location seatSpot(double[] offset) {
        Location at = location;
        double[] turned = Hitbox.turn(offset[0], offset[1], offset[2], at.getYaw(), at.getPitch());
        Location spot = at.clone().add(turned[0], turned[1], turned[2]);
        spot.setPitch(0f);
        return spot;
    }

    // main thread
    private void moveSeats() {
        for (Map.Entry<String, Entity> seat : seatEntities.entrySet()) {
            double[] offset = seats.get(seat.getKey());
            if (offset != null && seat.getValue().isValid()) {
                seat.getValue().teleport(seatSpot(offset));
            }
        }
    }

    // main thread
    private void removeSeats() {
        for (Entity entity : seatEntities.values()) {
            engine.seats.remove(entity);
            entity.remove();
        }
        seatEntities.clear();
    }

    /** Stops riding. The display stays where the entity was. */
    public MotionDisplay dismount() {
        if (vehicle == null || isRemoved()) {
            return this;
        }
        vehicle = null;
        engine.riding.remove(this);
        engine.submit(() -> engine.dismount(state, true));
        return this;
    }

    /**
     * What the player that wears this display sees of it, other players always see it.
     * {@code null} goes back to the default: {@link SelfView#HIDE_LOOKING_DOWN} for {@link Follow#BODY},
     * {@link SelfView#SHOW} otherwise.
     */
    public MotionDisplay selfView(SelfView view) {
        requireAlive();
        SelfView before = effectiveSelfView();
        selfView = view;
        SelfView after = effectiveSelfView();
        if (before != after) {
            selfHide(vehicle);
        }
        return this;
    }

    private void selfHide(Entity wearer) {
        UUID id = effectiveSelfView() == SelfView.HIDE && wearer instanceof Player player ? player.getUniqueId() : null;
        engine.submit(() -> engine.selfHidden(state, id));
    }

    /** What the wearer sees now, the default for the follow when nothing was set. */
    public SelfView selfView() {
        return effectiveSelfView();
    }

    private SelfView effectiveSelfView() {
        SelfView view = selfView;
        if (view != null) {
            return view;
        }
        return follow == Follow.BODY ? SelfView.HIDE_LOOKING_DOWN : SelfView.SHOW;
    }

    private RidePose selfPose(Entity entity, Location at, RidePose pose) {
        if (entity instanceof Player && effectiveSelfView() == SelfView.HIDE_LOOKING_DOWN) {
            return pose.hiddenForWearer(at.getPitch() > LOOK_DOWN_HIDE);
        }
        return pose;
    }

    /** What the display rides, or {@code null}. */
    public Entity vehicle() {
        return vehicle;
    }

    // main thread, every tick. only sends something when the vehicle moved a bit or turned
    void followVehicle(Location scratch) {
        Entity ridden = vehicle;
        if (ridden == null) {
            return;
        }
        if (isRemoved()) {
            engine.riding.remove(this);
            return;
        }
        if (!ridden.isValid()) {
            // a dead player comes back on respawn, keep the hat for them
            if (ridden instanceof Player player && player.isOnline()) {
                return;
            }
            dismount();
            return;
        }
        ridden.getLocation(scratch);
        RidePose pose = selfPose(ridden, scratch, poseOf(ridden, scratch));
        UUID world = scratch.getWorld().getUID();
        double dx = scratch.getX() - lastX;
        double dy = scratch.getY() - lastY;
        double dz = scratch.getZ() - lastZ;
        boolean moved = dx * dx + dy * dy + dz * dz > RIDE_MOVE * RIDE_MOVE || !world.equals(lastWorld);
        boolean turned = Math.abs(Bodies.wrap(pose.yaw() - lastPose.yaw())) >= RIDE_TURN
                || Math.abs(Bodies.wrap(pose.own() - lastPose.own())) >= RIDE_TURN
                || Math.abs(pose.tilt() - lastPose.tilt()) >= RIDE_TURN
                || Math.abs(pose.ownTilt() - lastPose.ownTilt()) >= RIDE_TURN
                || pose.ownHidden() != lastPose.ownHidden()
                || pose.pivot() != lastPose.pivot() || pose.drop() != lastPose.drop();
        if (!moved && !turned) {
            return;
        }
        RidePose send = turned ? pose : lastPose;
        Location at = scratch.clone();
        at.setYaw(send.yaw());
        at.setPitch(0f);
        location = at;
        remember(at, send);
        double x = at.getX();
        double y = at.getY();
        double z = at.getZ();
        engine.submitQuietly(() -> engine.follow(state, world, x, y, z, send));
    }

    private void remember(Location at, RidePose pose) {
        lastWorld = at.getWorld().getUID();
        lastX = at.getX();
        lastY = at.getY();
        lastZ = at.getZ();
        lastPose = pose;
    }

    // a player is done like the model: the head turns and tilts around the neck, the body
    // follows our copy of the client body turn and leans when sneaking. other entities only turn
    private RidePose poseOf(Entity entity, Location at) {
        if (follow == Follow.NONE) {
            return RidePose.turn(lastPose.yaw());
        }
        if (entity instanceof Player player) {
            boolean sneaking = player.isSneaking();
            if (follow == Follow.HEAD) {
                return headPose(player, at, sneaking);
            }
            float body = engine.bodies.yaw(player);
            float own = engine.bodies.own(player);
            return sneaking
                    ? new RidePose(body, BODY_LEAN_SNEAKING, BODY_PIVOT_SNEAKING, BODY_DROP_SNEAKING, own)
                    : new RidePose(body, 0f, 0f, 0f, own);
        }
        if (follow == Follow.BODY && entity instanceof LivingEntity living) {
            return RidePose.turn(living.getBodyYaw());
        }
        return RidePose.turn(at.getYaw());
    }

    // the wearer moves its head first and the server hears about it a ping later, so its own hat is
    // put where the head will be by the time the packet lands: the turn speed times ping plus a tick and
    // a half. the speed is smoothed and dies down fast when the head stops, so it does not swing past
    private RidePose headPose(Player player, Location at, boolean sneaking) {
        float yaw = at.getYaw();
        float pitch = at.getPitch();
        if (!Float.isNaN(lastHeadYaw)) {
            headSpeedYaw += (Bodies.wrap(yaw - lastHeadYaw) - headSpeedYaw) * HEAD_SPEED_SMOOTHING;
            headSpeedPitch += (pitch - lastHeadPitch - headSpeedPitch) * HEAD_SPEED_SMOOTHING;
        }
        lastHeadYaw = yaw;
        lastHeadPitch = pitch;
        float lead = Math.min(MAX_LEAD_TICKS, player.getPing() / 50f + LEAD_EXTRA_TICKS);
        float ownYaw = yaw + clamp(headSpeedYaw * lead, MAX_LEAD_DEGREES);
        float ownPitch = Math.max(-90f, Math.min(90f, pitch + clamp(headSpeedPitch * lead, MAX_LEAD_DEGREES)));
        return new RidePose(yaw, pitch, sneaking ? HEAD_PIVOT_SNEAKING : HEAD_PIVOT,
                sneaking ? HEAD_DROP_SNEAKING : 0f, ownYaw, ownPitch);
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    /** The mannequin of a display made with {@link DisplayMotion#npc(Location, Skin)}. */
    public Npc npc() {
        return npc(DisplayMotion.MAIN_PART);
    }

    /** The mannequin part with this name. */
    public Npc npc(String part) {
        int index = index(part);
        if (state.parts[index].kind != PartState.NPC) {
            throw new IllegalArgumentException("part '" + part + "' is not a mannequin");
        }
        return new Npc(this, engine, state, index);
    }

    /** Lets this player see the display, even when it is not visible by default. */
    public MotionDisplay show(Player player) {
        return visibility(player, true);
    }

    /** Hides the display from this player. */
    public MotionDisplay hide(Player player) {
        return visibility(player, false);
    }

    /** When false, only players given to {@link #show(Player)} see it. */
    public MotionDisplay visibleByDefault(boolean visible) {
        requireAlive();
        visibleByDefault = visible;
        engine.submit(() -> engine.visibleByDefault(state, visible));
        return this;
    }

    /** True unless {@link #visibleByDefault(boolean)} was set to false. */
    public boolean isVisibleByDefault() {
        return visibleByDefault;
    }

    private MotionDisplay visibility(Player player, boolean visible) {
        UUID id = Objects.requireNonNull(player, "player").getUniqueId();
        requireAlive();
        (visible ? hiddenFrom : shownTo).remove(id);
        (visible ? shownTo : hiddenFrom).add(id);
        engine.submit(() -> engine.visibility(state, id, visible));
        return this;
    }

    /**
     * Level of detail: players farther than this many blocks get no more animation frames, so they see the
     * display stand still, and do not get parts marked with {@link ModelBuilder#detail(String...)}. When they
     * come closer they get the current pose at once. It saves a lot of packets and fps on a busy server.
     * {@code 0} turns it off, which is the default.
     */
    public MotionDisplay lod(double blocks) {
        if (!(blocks >= 0)) {
            throw new IllegalArgumentException("lod must be 0 or more blocks, got " + blocks);
        }
        requireAlive();
        engine.submit(() -> engine.lod(state, blocks));
        return this;
    }

    /** Players closer than this, in the same world, see the display. The default is 48. */
    public MotionDisplay viewRange(double blocks) {
        if (!(blocks > 0)) {
            throw new IllegalArgumentException("view range must be more than 0, got " + blocks);
        }
        requireAlive();
        engine.submit(() -> engine.viewRange(state, blocks));
        return this;
    }

    /**
     * Makes the display clickable with one box, {@code width} blocks wide and {@code height} blocks high.
     * The bottom center of the box is the display location. Its name is {@code main}.
     * Use {@link #onClick(Consumer)} to hear the clicks.
     */
    public MotionDisplay hitbox(float width, float height) {
        return hitbox(DisplayMotion.MAIN_PART, width, height, 0, 0, 0);
    }

    /**
     * Adds a named clickable box. Its bottom center sits at this offset from the display location,
     * and the offset turns with the display. The box itself always stays upright, moves with
     * {@link #teleport(Location)} and does not follow animations.
     * @throws IllegalArgumentException if the name is taken or a size is not between 0 and {@link #MAX_HITBOX_SIZE}
     */
    public MotionDisplay hitbox(String name, float width, float height, double x, double y, double z) {
        Objects.requireNonNull(name, "name");
        checkSize("width", width);
        checkSize("height", height);
        requireAlive();
        if (!hitboxes.add(name)) {
            throw new IllegalArgumentException("there is already a hitbox named '" + name + "'");
        }
        Hitbox hitbox = new Hitbox(name, EntityIds.next(location.getWorld()), width, height, x, y, z, this);
        engine.submit(() -> engine.addHitbox(state, hitbox));
        return this;
    }

    /**
     * Runs when a player left or right clicks one of the hitboxes. It runs on the main thread,
     * so the Bukkit API is safe there. A new call replaces the old handler, {@code null} removes it.
     */
    public MotionDisplay onClick(Consumer<Click> handler) {
        requireAlive();
        onClick = handler;
        return this;
    }

    /**
     * Runs when a playing animation reaches an event added with {@link Animation#event(int, String)}.
     * It runs on the main thread. A new call replaces the old handler, {@code null} removes it.
     */
    public MotionDisplay onEvent(Consumer<DisplayEvent> handler) {
        requireAlive();
        onEvent = handler;
        return this;
    }

    boolean hearsEvents() {
        return onEvent != null;
    }

    // main thread only
    void reached(String part, String[] names) {
        Consumer<DisplayEvent> handler = onEvent;
        if (handler == null || isRemoved()) {
            return;
        }
        for (String name : names) {
            handler.accept(new DisplayEvent(this, name, part));
        }
    }

    /** The hitbox names, in no order. */
    public Set<String> hitboxes() {
        return Set.copyOf(hitboxes);
    }

    // main thread only
    void click(UUID id, Hitbox hitbox, boolean left) {
        Consumer<Click> handler = onClick;
        if (handler == null || isRemoved()) {
            engine.debug("click on '" + hitbox.name + "' dropped: " + (handler == null ? "no onClick handler" : "display removed"));
            return;
        }
        // ids go up one by one, a hacked client can guess the box of a quest npc it never got
        if (hiddenFrom.contains(id) || !visibleByDefault && !shownTo.contains(id)) {
            engine.debug("click on '" + hitbox.name + "' dropped: the player does not see this display");
            return;
        }
        Player player = Bukkit.getPlayer(id);
        if (player == null || !inReach(player, hitbox)) {
            engine.debug("click on '" + hitbox.name + "' dropped: " + (player == null ? "player not online" : "too far away"));
            return;
        }
        handler.accept(new Click(player, this, hitbox.name, left));
    }

    // the client can send a click for any entity id, so check it could really reach the box
    private boolean inReach(Player player, Hitbox hitbox) {
        Location at = location;
        Location eye = player.getEyeLocation();
        if (eye.getWorld() == null || !eye.getWorld().equals(at.getWorld())) {
            return false;
        }
        double[] offset = hitbox.offset(at.getYaw(), at.getPitch());
        double dx = at.getX() + offset[0] - eye.getX();
        double dy = at.getY() + offset[1] + hitbox.height / 2 - eye.getY();
        double dz = at.getZ() + offset[2] - eye.getZ();
        double reach = CLICK_REACH + Math.max(hitbox.width, hitbox.height);
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private static void checkSize(String what, float size) {
        if (!(size > 0) || size > MAX_HITBOX_SIZE) {
            throw new IllegalArgumentException(what + " must be more than 0 and at most " + MAX_HITBOX_SIZE + ", got " + size);
        }
    }

    /** Removes the display for every player. Safe to call more than once. */
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        playing = false;
        vehicle = null;
        engine.riding.remove(this);
        followed = null;
        engine.following.remove(this);
        watched = null;
        engine.watching.remove(this);
        if (!seatEntities.isEmpty()) {
            if (Bukkit.isPrimaryThread()) {
                removeSeats();
            } else {
                engine.mainThread.accept(this::removeSeats);
            }
        }
        engine.submitQuietly(() -> engine.remove(state));
    }

    /** True while an animation plays on the whole display. */
    public boolean isPlaying() {
        return playing && !isRemoved();
    }

    /** True after {@link #remove()} or after the library closed. */
    public boolean isRemoved() {
        return removed || engine.isClosed();
    }

    /** A copy of where the display is now. */
    public Location location() {
        return location.clone();
    }

    /** The part names, in the order they were added. A single item, block or text has one part, {@code main}. */
    public List<String> parts() {
        return parts;
    }

    private void check(Animation animation) {
        Objects.requireNonNull(animation, "animation");
        Frames.check(animation);
        requireAlive();
    }

    private int track(String part) {
        return index(part) + 1;
    }

    private int index(String part) {
        Integer index = partIndex.get(Objects.requireNonNull(part, "part"));
        if (index == null) {
            throw new IllegalArgumentException("no part named '" + part + "', parts are " + parts);
        }
        return index;
    }

    private void requireAlive() {
        if (isRemoved()) {
            throw new IllegalStateException("this display was removed");
        }
    }
}
