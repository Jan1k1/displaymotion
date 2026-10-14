package org.jan1k.displaymotion;

public final class Ease {

    private Ease() {
    }

    public static float out(float t) {
        t = Math.clamp(t, 0f, 1f);
        return 1f - (1f - t) * (1f - t);
    }
}
