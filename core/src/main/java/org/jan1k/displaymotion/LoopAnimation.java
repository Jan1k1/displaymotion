package org.jan1k.displaymotion;

final class LoopAnimation implements Animation {

    private final Animation inner;
    private final int length;

    LoopAnimation(Animation inner) {
        this.inner = inner;
        this.length = inner.introTicks();
        if (length <= 0) {
            throw new IllegalArgumentException("cannot loop an animation that is 0 ticks long");
        }
    }

    @Override
    public Pose poseAt(float tick) {
        float t = tick % length;
        return inner.poseAt(t < 0f ? t + length : t);
    }

    @Override
    public int introTicks() {
        return 0;
    }

    @Override
    public int loopTicks() {
        return length;
    }
}
