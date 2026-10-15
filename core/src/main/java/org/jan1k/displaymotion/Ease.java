package org.jan1k.displaymotion;

public enum Ease {
    LINEAR,
    IN,
    OUT,
    IN_OUT;

    public float apply(float t) {
        t = Math.clamp(t, 0f, 1f);
        return switch (this) {
            case LINEAR -> t;
            case IN -> t * t;
            case OUT -> 1f - (1f - t) * (1f - t);
            case IN_OUT -> t < 0.5f ? 2f * t * t : 1f - 2f * (1f - t) * (1f - t);
        };
    }
}
