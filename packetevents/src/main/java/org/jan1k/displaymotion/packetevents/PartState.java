package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import org.jan1k.displaymotion.Affine;
import org.jan1k.displaymotion.Decomposer;
import org.jan1k.displaymotion.Pose;

import java.util.UUID;

final class PartState {

    final String name;
    final int entityId;
    final UUID uuid = UUID.randomUUID();
    final int parent;
    final ItemStack item;
    final int itemTransform;
    final int blockState;
    final float sizeX;
    final float sizeY;
    final float sizeZ;
    final float[] rest = new float[Affine.SIZE];
    final float[] world = new float[Affine.SIZE];
    final float[] sent = new float[Decomposer.SIZE];
    int changes;

    PartState(String name, int entityId, int parent, ItemStack item, int itemTransform, int blockState, Pose pose) {
        this.name = name;
        this.entityId = entityId;
        this.parent = parent;
        this.item = item;
        this.itemTransform = itemTransform;
        this.blockState = blockState;
        this.sizeX = pose.scaleX();
        this.sizeY = pose.scaleY();
        this.sizeZ = pose.scaleZ();
        Affine.fromPose(pose.x(), pose.y(), pose.z(), pose.pitch(), pose.yaw(), pose.roll(), 1f, 1f, 1f, rest, 0);
    }

    boolean isBlock() {
        return item == null;
    }
}
