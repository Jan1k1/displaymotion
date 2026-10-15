package org.jan1k.displaymotion;

import java.util.List;

public interface Animation {

    Pose poseAt(float tick);

    int introTicks();

    int loopTicks();

    default boolean isEndless() {
        return loopTicks() > 0;
    }

    default Animation then(Animation next) {
        return sequence(this, next);
    }

    default Animation with(Animation other) {
        return together(this, other);
    }

    default Animation loop() {
        return isEndless() ? this : new LoopAnimation(this);
    }

    static KeyframeBuilder keyframes() {
        return new KeyframeBuilder();
    }

    static Animation sequence(Animation... parts) {
        return new SequenceAnimation(List.of(parts));
    }

    static Animation together(Animation... parts) {
        return new TogetherAnimation(List.of(parts));
    }
}
