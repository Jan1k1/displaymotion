package org.jan1k.displaymotion;

import java.util.List;

/**
 * A pose over time. Build one with {@link Animations} or {@link #keyframes()}.
 * Animations are immutable. Keep them in {@code static final} fields, they are baked once per object.
 */
public interface Animation {

    /** The pose at this tick. Must be a pure function. */
    Pose poseAt(float tick);

    /** Ticks before the loop starts, or the whole length when it does not loop. */
    int introTicks();

    /** Length of the part that repeats forever, or 0. */
    int loopTicks();

    /** True when the animation never ends. */
    default boolean isEndless() {
        return loopTicks() > 0;
    }

    /** Plays this, then {@code next}. {@code next} starts where this one ended. */
    default Animation then(Animation next) {
        return sequence(this, next);
    }

    /** Plays both at the same time. Offsets and angles add, scales multiply. */
    default Animation with(Animation other) {
        return together(this, other);
    }

    /** Repeats this forever. */
    default Animation loop() {
        return isEndless() ? this : new LoopAnimation(this);
    }

    /** Starts your own animation from keyframes. */
    static KeyframeBuilder keyframes() {
        return new KeyframeBuilder();
    }

    /** Plays the parts one after another, each starting where the last ended. */
    static Animation sequence(Animation... parts) {
        return new SequenceAnimation(List.of(parts));
    }

    /** Plays the parts at the same time. */
    static Animation together(Animation... parts) {
        return new TogetherAnimation(List.of(parts));
    }
}
