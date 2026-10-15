package org.jan1k.displaymotion.packetevents;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Frames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MotionDisplay {

    public static final int MAX_TELEPORT_TICKS = 59;

    private final MotionEngine engine;
    private volatile Location location;
    private final DisplayState state;
    private final Map<String, Integer> partIndex = new HashMap<>();
    private final List<String> parts;

    volatile boolean playing;
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

    public MotionDisplay play(Animation animation) {
        return play(animation, null);
    }

    public MotionDisplay play(Animation animation, Runnable onEnd) {
        check(animation);
        playing = true;
        engine.submit(() -> engine.play(state, 0, animation, onEnd));
        return this;
    }

    public MotionDisplay play(String part, Animation animation) {
        return play(part, animation, null);
    }

    public MotionDisplay play(String part, Animation animation, Runnable onEnd) {
        int track = track(part);
        check(animation);
        engine.submit(() -> engine.play(state, track, animation, onEnd));
        return this;
    }

    public MotionDisplay stop() {
        requireAlive();
        playing = false;
        engine.submit(() -> engine.stop(state, MotionEngine.ALL_TRACKS));
        return this;
    }

    public MotionDisplay stop(String part) {
        int track = track(part);
        requireAlive();
        engine.submit(() -> engine.stop(state, track));
        return this;
    }

    public MotionDisplay glow(Color color) {
        return glow(MotionEngine.ALL_PARTS, color);
    }

    public MotionDisplay glow(String part, Color color) {
        return glow(index(part), color);
    }

    public MotionDisplay brightness(Display.Brightness brightness) {
        return brightness(MotionEngine.ALL_PARTS, brightness);
    }

    public MotionDisplay brightness(String part, Display.Brightness brightness) {
        return brightness(index(part), brightness);
    }

    public MotionDisplay billboard(Display.Billboard billboard) {
        return billboard(MotionEngine.ALL_PARTS, billboard);
    }

    public MotionDisplay billboard(String part, Display.Billboard billboard) {
        return billboard(index(part), billboard);
    }

    public MotionDisplay text(Component text) {
        return text(MotionEngine.ALL_PARTS, text);
    }

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

    public MotionDisplay teleport(Location to) {
        return teleport(to, 0);
    }

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

    public MotionDisplay viewRange(double blocks) {
        if (!(blocks > 0)) {
            throw new IllegalArgumentException("view range must be more than 0, got " + blocks);
        }
        requireAlive();
        engine.submit(() -> engine.viewRange(state, blocks));
        return this;
    }

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        playing = false;
        engine.submitQuietly(() -> engine.remove(state));
    }

    public boolean isPlaying() {
        return playing && !isRemoved();
    }

    public boolean isRemoved() {
        return removed || engine.isClosed();
    }

    public Location location() {
        return location.clone();
    }

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
