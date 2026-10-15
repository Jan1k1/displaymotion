package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import org.jan1k.displaymotion.Frames;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class DisplayState {

    final int entityId;
    final UUID uuid;
    final UUID world;
    final double x;
    final double y;
    final double z;
    final ItemStack item;
    final int itemTransform;
    final int blockState;
    final MotionDisplay handle;
    final List<Viewer> viewers = new ArrayList<>();

    double range;
    double rangeSquared;
    Frames frames;
    long step;
    int frame;
    int index;
    boolean playing;
    boolean fresh = true;
    boolean removed;
    Runnable onEnd;

    Object spawnBuffer;
    Object metadataBuffer;
    Object destroyBuffer;
    boolean encoded;

    DisplayState(int entityId, UUID world, double x, double y, double z,
                 ItemStack item, int itemTransform, int blockState, double range, MotionDisplay handle) {
        this.entityId = entityId;
        this.uuid = UUID.randomUUID();
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.item = item;
        this.itemTransform = itemTransform;
        this.blockState = blockState;
        this.range = range;
        this.rangeSquared = range * range;
        this.handle = handle;
    }

    boolean isBlock() {
        return item == null;
    }

    boolean canSee(Viewer viewer) {
        if (!world.equals(viewer.world)) {
            return false;
        }
        double dx = viewer.x - x;
        double dy = viewer.y - y;
        double dz = viewer.z - z;
        return dx * dx + dy * dy + dz * dz <= rangeSquared;
    }
}
