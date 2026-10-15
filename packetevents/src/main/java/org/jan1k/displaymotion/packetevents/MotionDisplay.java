package org.jan1k.displaymotion.packetevents;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Frames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A spawned display or model. It is not a real entity, only packets.
 * Every method is safe from any thread. After {@link #remove()} the methods throw {@link IllegalStateException}, except {@code remove()}.
 */
public final class MotionDisplay {

    public static final int MAX_TELEPORT_TICKS = 59;

    private final MotionEngine engine;
    private volatile Location location;
    private final DisplayState state;
    private final Map<String, Integer> partIndex = new HashMap<>();
    private final List<String> parts;

    volatile boolean playing;
    private volatile boolean visibleByDefault = true;
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

    /**
     * Changes the text of every text part.
     * @throws IllegalArgumentException if the display has no text part or the text is too big
     */
    /** Background behind every text part, alpha included. {@code null} is the vanilla dark box. Use alpha 0 for none. */
    public MotionDisplay background(Color color) {
        return background(MotionEngine.ALL_PARTS, color);
    }

    /** Background behind one text part. */
    public MotionDisplay background(String part, Color color) {
        return background(index(part), color);
    }

    private MotionDisplay background(int part, Color color) {
        requireAlive();
        int argb = color == null ? PartState.DEFAULT_BACKGROUND : color.asARGB();
        engine.submit(() -> engine.look(state, part, p -> p.background = argb));
        return this;
    }

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
        // packed the same way vanilla does it, -1 means use the world light
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
        Location copy = to.clone();
        location = copy;
        UUID world = copy.getWorld().getUID();
        double x = copy.getX();
        double y = copy.getY();
        double z = copy.getZ();
        float yaw = copy.getYaw();
        float pitch = copy.getPitch();
        engine.submit(() -> engine.teleport(state, world, x, y, z, yaw, pitch, ticks));
        return this;
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
        engine.submit(() -> engine.visibility(state, id, visible));
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

    /** Removes the display for every player. Safe to call more than once. */
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        playing = false;
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
