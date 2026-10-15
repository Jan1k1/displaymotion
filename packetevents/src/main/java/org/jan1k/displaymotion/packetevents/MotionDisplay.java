package org.jan1k.displaymotion.packetevents;

import org.bukkit.Location;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Frames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MotionDisplay {

    private final MotionEngine engine;
    private final Location location;
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
        Integer index = partIndex.get(Objects.requireNonNull(part, "part"));
        if (index == null) {
            throw new IllegalArgumentException("no part named '" + part + "', parts are " + parts);
        }
        return index + 1;
    }

    private void requireAlive() {
        if (isRemoved()) {
            throw new IllegalStateException("this display was removed");
        }
    }
}
