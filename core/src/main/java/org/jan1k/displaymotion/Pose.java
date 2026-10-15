package org.jan1k.displaymotion;

public record Pose(
        float x, float y, float z,
        float pitch, float yaw, float roll,
        float scaleX, float scaleY, float scaleZ
) {

    public static final Pose IDENTITY = new Pose(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);

    public Pose withOffset(float x, float y, float z) {
        return new Pose(x, y, z, pitch, yaw, roll, scaleX, scaleY, scaleZ);
    }

    public Pose withRotation(float pitch, float yaw, float roll) {
        return new Pose(x, y, z, pitch, yaw, roll, scaleX, scaleY, scaleZ);
    }

    public Pose withScale(float scale) {
        return withScale(scale, scale, scale);
    }

    public Pose withScale(float scaleX, float scaleY, float scaleZ) {
        return new Pose(x, y, z, pitch, yaw, roll, scaleX, scaleY, scaleZ);
    }

    public Pose lerp(Pose to, float t) {
        return new Pose(
                x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t,
                pitch + (to.pitch - pitch) * t, yaw + (to.yaw - yaw) * t, roll + (to.roll - roll) * t,
                scaleX + (to.scaleX - scaleX) * t, scaleY + (to.scaleY - scaleY) * t, scaleZ + (to.scaleZ - scaleZ) * t
        );
    }

    public Pose combine(Pose other) {
        return new Pose(
                x + other.x, y + other.y, z + other.z,
                pitch + other.pitch, yaw + other.yaw, roll + other.roll,
                scaleX * other.scaleX, scaleY * other.scaleY, scaleZ * other.scaleZ
        );
    }
}
