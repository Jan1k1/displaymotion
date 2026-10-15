package org.jan1k.displaymotion.paper;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import net.kyori.adventure.text.Component;
import org.jan1k.displaymotion.Affine;
import org.jan1k.displaymotion.Decomposer;
import org.jan1k.displaymotion.Pose;

import java.util.UUID;

final class PartState {

    static final int ITEM = 0;
    static final int BLOCK = 1;
    static final int TEXT = 2;
    static final int GROUP = 3;
    static final int DEFAULT_BACKGROUND = 0x40000000;

    final String name;
    final int entityId;
    final UUID uuid = UUID.randomUUID();
    final int parent;
    final int kind;
    final ItemStack item;
    final int itemTransform;
    final int blockState;
    final float sizeX;
    final float sizeY;
    final float sizeZ;
    final float[] rest = new float[Affine.SIZE];
    final float[] world = new float[Affine.SIZE];
    final float[] sent = new float[Decomposer.SIZE];
    // what the wearer of a riding display got, and what changed for it
    final float[] own = new float[Decomposer.SIZE];
    int ownChanges;
    final boolean centered;
    int changes;
    float cullWidth;
    float cullHeight;

    Component text;
    boolean glowing;
    int glowColor = -1;
    int brightness = -1;
    int billboard;
    int background = DEFAULT_BACKGROUND;

    private PartState(String name, int entityId, int parent, int kind, ItemStack item, int itemTransform,
                      int blockState, Component text, Pose pose) {
        this.name = name;
        this.entityId = entityId;
        this.parent = parent;
        this.kind = kind;
        this.item = item;
        this.itemTransform = itemTransform;
        this.blockState = blockState;
        this.text = text;
        this.sizeX = pose.scaleX();
        this.sizeY = pose.scaleY();
        this.sizeZ = pose.scaleZ();
        this.centered = true;
        Affine.fromPose(pose.x(), pose.y(), pose.z(), pose.pitch(), pose.yaw(), pose.roll(), 1f, 1f, 1f, rest, 0);
    }

    // matrix parts work like vanilla: the whole matrix goes to the children and blocks turn around the corner
    private PartState(String name, int entityId, int parent, int kind, ItemStack item, int itemTransform,
                      int blockState, Component text, float[] matrix) {
        this.name = name;
        this.entityId = entityId;
        this.parent = parent;
        this.kind = kind;
        this.item = item;
        this.itemTransform = itemTransform;
        this.blockState = blockState;
        this.text = text;
        this.sizeX = 1f;
        this.sizeY = 1f;
        this.sizeZ = 1f;
        this.centered = false;
        fromRows(matrix, rest);
    }

    static PartState matrix(String name, int entityId, int parent, int kind, ItemStack item, int itemTransform,
                            int blockState, Component text, float[] matrix) {
        return new PartState(name, entityId, parent, kind, item, itemTransform, blockState, text, matrix);
    }

    // 4x4 row by row (like /summon and bdengine) into our 3x4 column by column
    static void fromRows(float[] m, float[] out) {
        if (m.length != 16 && m.length != 12) {
            throw new IllegalArgumentException("a matrix needs 16 numbers, row by row, got " + m.length);
        }
        out[0] = m[0];
        out[1] = m[4];
        out[2] = m[8];
        out[3] = m[1];
        out[4] = m[5];
        out[5] = m[9];
        out[6] = m[2];
        out[7] = m[6];
        out[8] = m[10];
        out[9] = m[3];
        out[10] = m[7];
        out[11] = m[11];
    }

    static PartState item(String name, int entityId, int parent, ItemStack item, int itemTransform, Pose pose) {
        return new PartState(name, entityId, parent, ITEM, item, itemTransform, 0, null, pose);
    }

    static PartState block(String name, int entityId, int parent, int blockState, Pose pose) {
        return new PartState(name, entityId, parent, BLOCK, null, 0, blockState, null, pose);
    }

    static PartState group(String name, int parent, Pose pose) {
        return new PartState(name, -1, parent, GROUP, null, 0, 0, null, pose);
    }

    static PartState text(String name, int entityId, int parent, Component text, Pose pose) {
        return new PartState(name, entityId, parent, TEXT, null, 0, 0, text, pose);
    }

    // groups are just a pivot, nothing gets spawned for them
    boolean hasEntity() {
        return kind != GROUP;
    }

    boolean isBlock() {
        return kind == BLOCK;
    }
}
