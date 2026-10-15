package org.jan1k.displaymotion.packetevents;

import java.util.UUID;

final class Hitbox {

    final String name;
    final int entityId;
    final UUID uuid = UUID.randomUUID();
    final float width;
    final float height;
    final double x;
    final double y;
    final double z;
    final MotionDisplay handle;

    Hitbox(String name, int entityId, float width, float height, double x, double y, double z, MotionDisplay handle) {
        this.name = name;
        this.entityId = entityId;
        this.width = width;
        this.height = height;
        this.x = x;
        this.y = y;
        this.z = z;
        this.handle = handle;
    }

    // the client turns a display by yaw and then pitch, so the offset has to turn the same way
    // or the box ends up next to the model when its turned
    double[] offset(float yaw, float pitch) {
        double a = Math.toRadians(-yaw);
        double b = Math.toRadians(pitch);
        double y1 = y * Math.cos(b) - z * Math.sin(b);
        double z1 = y * Math.sin(b) + z * Math.cos(b);
        return new double[]{x * Math.cos(a) + z1 * Math.sin(a), y1, -x * Math.sin(a) + z1 * Math.cos(a)};
    }
}
