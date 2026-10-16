package org.jan1k.displaymotion;

/**
 * How a keyframe blends in. {@code OUT} starts fast, {@code IN} ends fast.
 * {@code BACK} and {@code ELASTIC} go a bit past the target and come back, {@code BOUNCE} bounces on it.
 * The curves are the usual ones from easings.net.
 */
public enum Ease {
    LINEAR,
    IN,
    OUT,
    IN_OUT,
    /** Pulls back a little first, then goes. */
    BACK_IN,
    /** Goes a bit too far, then settles. Nice for things that pop in. */
    BACK_OUT,
    /** Shoots past and wobbles a few times before it stops. */
    ELASTIC_OUT,
    /** Starts with small bounces, then goes. */
    BOUNCE_IN,
    /** Lands and bounces like a dropped ball. */
    BOUNCE_OUT;

    private static final float BACK = 1.70158f;
    private static final float ELASTIC = (float) (2 * Math.PI / 3);

    /** Maps time 0 to 1 onto progress, 0 at the start and 1 at the end. BACK and ELASTIC go below 0 or above 1 in between. */
    public float apply(float t) {
        t = Math.clamp(t, 0f, 1f);
        return switch (this) {
            case LINEAR -> t;
            case IN -> t * t;
            case OUT -> 1f - (1f - t) * (1f - t);
            case IN_OUT -> t < 0.5f ? 2f * t * t : 1f - 2f * (1f - t) * (1f - t);
            case BACK_IN -> (BACK + 1f) * t * t * t - BACK * t * t;
            case BACK_OUT -> 1f + (BACK + 1f) * (t - 1f) * (t - 1f) * (t - 1f) + BACK * (t - 1f) * (t - 1f);
            case ELASTIC_OUT -> t == 0f || t == 1f ? t
                    : (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * ELASTIC) + 1);
            case BOUNCE_IN -> 1f - bounce(1f - t);
            case BOUNCE_OUT -> bounce(t);
        };
    }

    private static float bounce(float t) {
        float n = 7.5625f;
        float d = 2.75f;
        if (t < 1f / d) {
            return n * t * t;
        }
        if (t < 2f / d) {
            t -= 1.5f / d;
            return n * t * t + 0.75f;
        }
        if (t < 2.5f / d) {
            t -= 2.25f / d;
            return n * t * t + 0.9375f;
        }
        t -= 2.625f / d;
        return n * t * t + 0.984375f;
    }
}
