package org.jan1k.displaymotion.paper;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.TextDisplay;
import org.jan1k.displaymotion.Affine;
import org.jan1k.displaymotion.Decomposer;
import org.jan1k.displaymotion.Pose;

import java.util.UUID;

final class PartState {

    static final int ITEM = 0;
    static final int BLOCK = 1;
    static final int TEXT = 2;
    static final int GROUP = 3;
    static final int NPC = 4;
    static final int DEFAULT_BACKGROUND = 0x40000000;
    static final int DEFAULT_LINE_WIDTH = 200;
    static final int SHADOW_FLAG = 0x01;
    static final int SEE_THROUGH_FLAG = 0x02;
    static final int ALIGN_LEFT_FLAG = 0x08;
    static final int ALIGN_RIGHT_FLAG = 0x10;

    final String name;
    final int entityId;
    final UUID uuid = UUID.randomUUID();
    final int parent;
    final int kind;
    // item and blockState can be swapped later, engine thread
    ItemStack item;
    final int itemTransform;
    int blockState;
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
    // at scale 0, so the client has view range 0 for it and does not draw it at all. see Composer.hide
    boolean hidden;
    // engine tick when a part that went to scale 0 gets hidden, -1 when nothing waits
    long hideAfter = -1;

    Component text;
    boolean glowing;
    int glowColor = -1;
    int brightness = -1;
    int billboard;
    int background = DEFAULT_BACKGROUND;
    float shadowRadius;
    float shadowStrength = 1f;
    int lineWidth = DEFAULT_LINE_WIDTH;
    int textOpacity = 255;
    boolean textShadow;
    boolean seeThrough;
    TextDisplay.TextAlignment alignment = TextDisplay.TextAlignment.CENTER;
    // small extras that players far away dont get at all, see MotionDisplay.lod
    boolean detail;
    final Resize size = new Resize();

    // only for NPC parts, the mannequin data. engine thread
    Skin skin;
    Component npcName;
    Component description;
    NpcPose npcPose = NpcPose.STANDING;
    float npcSize = 1f;
    final java.util.Map<com.github.retrooper.packetevents.protocol.player.EquipmentSlot, ItemStack> equipment = new java.util.EnumMap<>(
            com.github.retrooper.packetevents.protocol.player.EquipmentSlot.class);
    double lookRange;
    // what each viewer was last told the head looks at, yaw and pitch
    final java.util.Map<Viewer, float[]> looks = new java.util.HashMap<>();

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

    static PartState npc(String name, int entityId, int parent, Skin skin) {
        PartState npc = new PartState(name, entityId, parent, NPC, null, 0, 0, null, Pose.IDENTITY);
        npc.skin = skin;
        return npc;
    }

    static PartState text(String name, int entityId, int parent, Component text, Pose pose) {
        return new PartState(name, entityId, parent, TEXT, null, 0, 0, text, pose);
    }

    // groups are just a pivot, nothing gets spawned for them
    boolean hasEntity() {
        return kind != GROUP;
    }

    // a display entity with a transform, a mannequin has none of the display data
    boolean isDisplay() {
        return kind != GROUP && kind != NPC;
    }

    // the style flags byte of a text display
    int textFlags() {
        int flags = (textShadow ? SHADOW_FLAG : 0) | (seeThrough ? SEE_THROUGH_FLAG : 0);
        return switch (alignment) {
            case LEFT -> flags | ALIGN_LEFT_FLAG;
            case RIGHT -> flags | ALIGN_RIGHT_FLAG;
            default -> flags;
        };
    }

    boolean isBlock() {
        return kind == BLOCK;
    }
}
