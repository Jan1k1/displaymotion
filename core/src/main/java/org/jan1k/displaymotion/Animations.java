package org.jan1k.displaymotion;

public final class Animations {

    public static final int MIN_TICKS_PER_TURN = 6;

    private Animations() {
    }

    public static Animation popIn(int ticks) {
        requirePositive(ticks);
        return Animation.keyframes()
                .at(0, Pose.IDENTITY.withScale(0f))
                .at(ticks, Pose.IDENTITY, Ease.OUT)
                .build();
    }

    public static Animation popOut(int ticks) {
        requirePositive(ticks);
        return Animation.keyframes()
                .at(0, Pose.IDENTITY)
                .at(ticks, Pose.IDENTITY.withScale(0f), Ease.IN)
                .build();
    }

    public static Animation spin(int ticksPerTurn) {
        if (ticksPerTurn < MIN_TICKS_PER_TURN) {
            throw new IllegalArgumentException("a spin needs at least " + MIN_TICKS_PER_TURN
                    + " ticks per turn, or the client cannot tell the direction, got " + ticksPerTurn);
        }
        return turn(0f, 360f, 0f, ticksPerTurn).loop();
    }

    public static Animation turn(float pitch, float yaw, float roll, int ticks) {
        requirePositive(ticks);
        return Animation.keyframes()
                .at(0, Pose.IDENTITY)
                .at(ticks, Pose.IDENTITY.withRotation(pitch, yaw, roll))
                .build();
    }

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

    public static Animation slide(float x, float y, float z, int ticks, Ease ease) {
        requirePositive(ticks);
        return Animation.keyframes()
                .at(0, Pose.IDENTITY)
                .at(ticks, Pose.IDENTITY.withOffset(x, y, z), ease)
                .build();
    }

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
