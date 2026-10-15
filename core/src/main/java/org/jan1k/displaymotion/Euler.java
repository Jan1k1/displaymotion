package org.jan1k.displaymotion;

/** Converts rotations from other tools into the yaw, pitch, roll order a {@link Pose} uses. */
public final class Euler {

    private Euler() {
    }

    /**
     * Blockbench (and three.js with order ZYX) turns around X first, then Y, then Z.
     * Returns the same rotation as {@code {pitch, yaw, roll}} in degrees for {@link Pose#withRotation}.
     */
    public static float[] fromZyx(float x, float y, float z) {
        double cx = Math.cos(Math.toRadians(x));
        double sx = Math.sin(Math.toRadians(x));
        double cy = Math.cos(Math.toRadians(y));
        double sy = Math.sin(Math.toRadians(y));
        double cz = Math.cos(Math.toRadians(z));
        double sz = Math.sin(Math.toRadians(z));

        // rows of Rz * Ry * Rx, only the cells we need
        double m00 = cz * cy;
        double m02 = cz * sy * cx + sz * sx;
        double m10 = sz * cy;
        double m11 = sz * sy * sx + cz * cx;
        double m12 = sz * sy * cx - cz * sx;
        double m20 = -sy;
        double m22 = cy * cx;

        double pitch = Math.asin(Math.clamp(-m12, -1.0, 1.0));
        double yaw;
        double roll;
        if (Math.abs(m12) < 0.999999) {
            yaw = Math.atan2(m02, m22);
            roll = Math.atan2(m10, m11);
        } else {
            // straight up or down, yaw and roll are the same axis now so put it all in yaw
            yaw = Math.atan2(-m20, m00);
            roll = 0;
        }
        return new float[]{(float) Math.toDegrees(pitch), (float) Math.toDegrees(yaw), (float) Math.toDegrees(roll)};
    }
}
