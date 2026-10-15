package org.jan1k.displaymotion.packetevents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class DisplayState {

    // hide a bit further then we show so walking on the edge doesnt spam spawn and destroy
    static final double HIDE_MARGIN = 4.0;

    UUID world;
    double x;
    double y;
    double z;
    float yaw;
    float pitch;
    int moveTicks;
    final PartState[] parts;
    final int[] entityIds;
    final Track[] tracks;
    final long[] requests;
    final MotionDisplay handle;
    final List<Viewer> viewers = new ArrayList<>();
    final Set<UUID> shownTo = new HashSet<>();
    final Set<UUID> hiddenFrom = new HashSet<>();
    boolean visibleByDefault = true;

    double range;
    int index;
    long showAfter;
    boolean fresh = true;
    boolean removed;
    boolean active;
    boolean stale;

    Object[] spawnBuffers;
    Object destroyBuffer;
    boolean encoded;

    DisplayState(UUID world, double x, double y, double z, float yaw, float pitch,
                 PartState[] parts, double range, MotionDisplay handle) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.parts = parts;
        this.entityIds = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            entityIds[i] = parts[i].entityId;
        }
        this.tracks = new Track[parts.length + 1];
        this.requests = new long[parts.length + 1];
        this.range = range;
        this.handle = handle;
    }

    boolean canSee(Viewer viewer, boolean shown) {
        if (!allowed(viewer.id) || !world.equals(viewer.world)) {
            return false;
        }
        double limit = shown ? range + HIDE_MARGIN : range;
        double dx = viewer.x - x;
        double dy = viewer.y - y;
        double dz = viewer.z - z;
        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }

    boolean allowed(UUID player) {
        if (hiddenFrom.contains(player)) {
            return false;
        }
        return visibleByDefault || shownTo.contains(player);
    }

    double clientRange() {
        return range + HIDE_MARGIN;
    }
}
