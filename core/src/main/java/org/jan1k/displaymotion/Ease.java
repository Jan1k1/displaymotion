package org.jan1k.displaymotion;

/** How a keyframe blends in. {@code OUT} starts fast, {@code IN} ends fast. */
public enum Ease {
    LINEAR,
    IN,
    OUT,
    IN_OUT;

    /** Maps time 0 to 1 onto progress 0 to 1. */
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
