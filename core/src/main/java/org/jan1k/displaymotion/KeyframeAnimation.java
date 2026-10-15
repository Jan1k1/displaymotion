package org.jan1k.displaymotion;

final class KeyframeAnimation implements Animation {

    record Key(int tick, Pose pose, Ease ease) {
    }

    private final Key[] keys;

    KeyframeAnimation(Key[] keys) {
        this.keys = keys;
    }

    @Override
    public Pose poseAt(float tick) {
        if (tick <= keys[0].tick()) {
            return keys[0].pose();
        }
        for (int i = 1; i < keys.length; i++) {
            Key to = keys[i];
            if (tick < to.tick()) {
                Key from = keys[i - 1];
                float t = (tick - from.tick()) / (to.tick() - from.tick());
                return from.pose().lerp(to.pose(), to.ease().apply(t));
            }
        }
        return keys[keys.length - 1].pose();
    }

    @Override
    public int introTicks() {
        return keys[keys.length - 1].tick();
    }

    @Override
    public int loopTicks() {
        return 0;
    }
}
