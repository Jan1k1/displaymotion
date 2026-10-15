package org.jan1k.displaymotion;

/** Ready-made animations. Combine them with {@code then}, {@code with} and {@code loop}. */
public final class Animations {

    public static final int MIN_TICKS_PER_TURN = 6;

    private Animations() {
    }

    /** Grows from nothing to full size, fast at first. */
    public static Animation popIn(int ticks) {
        requirePositive(ticks);
        return Animation.keyframes()
                .at(0, Pose.IDENTITY.withScale(0f))
                .at(ticks, Pose.IDENTITY, Ease.OUT)
                .build();
    }

    /** Shrinks from full size to nothing, fast at the end. */
    public static Animation popOut(int ticks) {
        requirePositive(ticks);
        return Animation.keyframes()
                .at(0, Pose.IDENTITY)
                .at(ticks, Pose.IDENTITY.withScale(0f), Ease.IN)
                .build();
    }

    /** Turns around the Y axis forever. At least 6 ticks per turn. */
    public static Animation spin(int ticksPerTurn) {
        if (ticksPerTurn < MIN_TICKS_PER_TURN) {
            throw new IllegalArgumentException("a spin needs at least " + MIN_TICKS_PER_TURN
                    + " ticks per turn, or the client cannot tell the direction, got " + ticksPerTurn);
        }
        return turn(0f, 360f, 0f, ticksPerTurn).loop();
    }

    /** Turns by these angles in degrees, once. Add {@code .loop()} to repeat it. */
    public static Animation turn(float pitch, float yaw, float roll, int ticks) {
        requirePositive(ticks);
        return Animation.keyframes()
                .at(0, Pose.IDENTITY)
                .at(ticks, Pose.IDENTITY.withRotation(pitch, yaw, roll))
                .build();
    }

    /** Moves up by {@code height} and back down, forever. */
    public static Animation bob(float height, int ticks) {
        if (ticks < 2) {
            throw new IllegalArgumentException("ticks must be 2 or more, got " + ticks);
        }
        return Animation.keyframes()
                .at(0, Pose.IDENTITY)
                .at(ticks / 2, Pose.IDENTITY.withOffset(0f, height, 0f), Ease.IN_OUT)
                .at(ticks, Pose.IDENTITY, Ease.IN_OUT)
                .build()
                .loop();
    }

    /** Moves by this offset, once. */
    public static Animation slide(float x, float y, float z, int ticks, Ease ease) {
        requirePositive(ticks);
        return Animation.keyframes()
                .at(0, Pose.IDENTITY)
                .at(ticks, Pose.IDENTITY.withOffset(x, y, z), ease)
                .build();
    }

    /** Stays still for a time. Use it inside {@code then} for a pause. */
    public static Animation hold(int ticks) {
        requirePositive(ticks);
        return Animation.keyframes()
                .at(0, Pose.IDENTITY)
                .at(ticks, Pose.IDENTITY)
                .build();
    }

    private static void requirePositive(int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks must be more than 0, got " + ticks);
        }
    }
}
