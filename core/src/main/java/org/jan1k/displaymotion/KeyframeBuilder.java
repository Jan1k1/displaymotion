package org.jan1k.displaymotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class KeyframeBuilder {

    private final List<KeyframeAnimation.Key> keys = new ArrayList<>();

    KeyframeBuilder() {
    }

    public KeyframeBuilder at(int tick, Pose pose) {
        return at(tick, pose, Ease.LINEAR);
    }

    public KeyframeBuilder at(int tick, Pose pose, Ease ease) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(ease, "ease");
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be 0 or more, got " + tick);
        }
        if (!keys.isEmpty() && tick <= keys.getLast().tick()) {
            throw new IllegalArgumentException("keyframe ticks must go up, got " + tick + " after " + keys.getLast().tick());
        }
        keys.add(new KeyframeAnimation.Key(tick, pose, ease));
        return this;
    }

    public Animation build() {
        if (keys.isEmpty()) {
            throw new IllegalStateException("add at least one keyframe");
        }
        return new KeyframeAnimation(keys.toArray(KeyframeAnimation.Key[]::new));
    }
}
