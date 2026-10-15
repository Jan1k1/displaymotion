package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import org.bukkit.Location;
import org.jan1k.displaymotion.Animation;

import java.util.Objects;

public final class MotionDisplay {

    private final MotionEngine engine;
    private final Location location;
    private final DisplayState state;

    volatile boolean playing;
    private volatile boolean removed;

    MotionDisplay(MotionEngine engine, Location location, int entityId,
                  ItemStack item, int itemTransform, int blockState, double range) {
        this.engine = engine;
        this.location = location;
        this.state = new DisplayState(entityId, location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ(),
                item, itemTransform, blockState, range, this);
    }

    DisplayState state() {
        return state;
    }

    public MotionDisplay play(Animation animation) {
        Objects.requireNonNull(animation, "animation");
        requireAlive();
        playing = true;
        engine.submit(() -> engine.play(state, animation));
        return this;
    }

    public MotionDisplay stop() {
        requireAlive();
        playing = false;
        engine.submit(() -> engine.stop(state));
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

    public MotionDisplay onEnd(Runnable action) {
        requireAlive();
        engine.submit(() -> state.onEnd = action);
        return this;
    }

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        playing = false;
        if (!engine.isClosed()) {
            engine.submit(() -> engine.remove(state));
        }
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isRemoved() {
        return removed || engine.isClosed();
    }

    public Location location() {
        return location.clone();
    }

    public int entityId() {
        return state.entityId;
    }

    private void requireAlive() {
        if (isRemoved()) {
            throw new IllegalStateException("this display was removed");
        }
    }
}
