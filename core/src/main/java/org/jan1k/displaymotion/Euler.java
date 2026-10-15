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
        return fromMatrix(zyxMatrix(x, y, z));
    }

    /** The rotation matrix of Rz * Ry * Rx, row by row, angles in degrees. */
    public static double[] zyxMatrix(double x, double y, double z) {
        double cx = Math.cos(Math.toRadians(x));
        double sx = Math.sin(Math.toRadians(x));
        double cy = Math.cos(Math.toRadians(y));
        double sy = Math.sin(Math.toRadians(y));
        double cz = Math.cos(Math.toRadians(z));
        double sz = Math.sin(Math.toRadians(z));
        return new double[]{
                cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
                sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
                -sy, cy * sx, cy * cx
        };
    }

    /** Reads {@code {pitch, yaw, roll}} in degrees out of a rotation matrix given row by row. */
    public static float[] fromMatrix(double[] m) {
        double m12 = m[5];
        double pitch = Math.asin(Math.clamp(-m12, -1.0, 1.0));
        double yaw;
        double roll;
        if (Math.abs(m12) < 0.999999) {
            yaw = Math.atan2(m[2], m[8]);
            roll = Math.atan2(m[3], m[4]);
        } else {
            // straight up or down, yaw and roll are the same axis now so put it all in yaw
            yaw = Math.atan2(-m[6], m[0]);
            roll = 0;
        }
        return new float[]{(float) Math.toDegrees(pitch), (float) Math.toDegrees(yaw), (float) Math.toDegrees(roll)};
    }
}
