package org.jan1k.displaymotion.paper;

import org.jan1k.displaymotion.Ease;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class DisplayState {

    // standing right on the edge made it spawn and despawn every few ticks,
    // so we hide a bit further out then we show
    static final double HIDE_MARGIN = 4.0;
    static final double LOD_MARGIN = 2.0;

    UUID world;
    double x;
    double y;
    double z;
    float yaw;
    float pitch;
    int moveTicks;
    final PartState[] parts;
    final List<Hitbox> hitboxes = new ArrayList<>();
    int[] entityIds;
    final Track[] tracks;
    final long[] requests;
    final MotionDisplay handle;
    final List<Viewer> viewers = new ArrayList<>();
    final Set<UUID> shownTo = new HashSet<>();
    final Set<UUID> hiddenFrom = new HashSet<>();
    boolean visibleByDefault = true;
    // past this many blocks a viewer gets no frames and no detail parts, 0 is off
    double lod;
    final Set<Viewer> far = new HashSet<>();
    // entity id of what it rides, -1 when it does not ride
    int vehicle = -1;
    // extra turn around Y on top of the entity yaw, in the transform. a riding display turns with this,
    // its entity yaw and pitch stay 0 while it rides
    float turn;
    // the player it rides gets its own turn, see Bodies. null when it rides no player
    UUID wearer;
    float ownTurn;
    // tilt around X at the pivot height, then moved down by drop. for hats on a head and leaning backpacks
    float tilt;
    float ownTilt;
    boolean ownHidden;
    float pivot;
    float drop;
    final Resize size = new Resize();
    double speed = 1.0;
    double fromX;
    double fromY;
    double fromZ;
    double toX;
    double toY;
    double toZ;
    int glideId;
    int glideSteps;
    int glideStep;
    Ease glideEase = Ease.LINEAR;
    // null for a straight moveTo
    Curve glideCurve;

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
        this.entityIds = Arrays.stream(parts).filter(PartState::hasEntity).mapToInt(p -> p.entityId).toArray();
        this.tracks = new Track[parts.length + 1];
        this.requests = new long[parts.length + 1];
        this.range = range;
        this.handle = handle;
    }

    void addHitbox(Hitbox hitbox) {
        hitboxes.add(hitbox);
        entityIds = Arrays.copyOf(entityIds, entityIds.length + 1);
        entityIds[entityIds.length - 1] = hitbox.entityId;
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

    // a bit of room around the line, or standing right on it would switch every snapshot
    boolean farFor(Viewer viewer, boolean now) {
        if (lod <= 0) {
            return false;
        }
        double limit = now ? lod - LOD_MARGIN : lod + LOD_MARGIN;
        double dx = viewer.x - x;
        double dy = viewer.y - y;
        double dz = viewer.z - z;
        return dx * dx + dy * dy + dz * dz > limit * limit;
    }

    double clientRange() {
        return range + HIDE_MARGIN;
    }
}
