package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import org.jan1k.displaymotion.Decomposer;

final class DisplayPacket extends PacketWrapper<DisplayPacket> {

    static final int TRANSLATION = 1;
    static final int LEFT_ROTATION = 2;
    static final int SCALE = 4;
    static final int RIGHT_ROTATION = 8;
    static final int ALL = TRANSLATION | LEFT_ROTATION | SCALE | RIGHT_ROTATION;

    private static final int START_DELAY_SLOT = 8;
    private static final int DURATION_SLOT = 9;
    private static final int TRANSLATION_SLOT = 11;
    private static final int SCALE_SLOT = 12;
    private static final int LEFT_ROTATION_SLOT = 13;
    private static final int RIGHT_ROTATION_SLOT = 14;
    private static final int VIEW_RANGE_SLOT = 17;
    private static final int CONTENT_SLOT = 23;
    private static final int ITEM_TRANSFORM_SLOT = 24;
    private static final int END = 0xFF;
    private static final double BLOCKS_PER_VIEW_RANGE = 64.0;

    private static final int FRAME = 0;
    private static final int SPAWN = 1;
    private static final int RANGE = 2;

    private final int intType;
    private final int byteType;
    private final int floatType;
    private final int vectorType;
    private final int quaternionType;
    private final int itemType;
    private final int blockType;

    private PartState part;
    private int changes;
    private int duration;
    private double range;
    private int mode;

    DisplayPacket() {
        super(PacketType.Play.Server.ENTITY_METADATA);
        ClientVersion version = getServerVersion().toClientVersion();
        intType = EntityDataTypes.INT.getId(version);
        byteType = EntityDataTypes.BYTE.getId(version);
        floatType = EntityDataTypes.FLOAT.getId(version);
        vectorType = EntityDataTypes.VECTOR3F.getId(version);
        quaternionType = EntityDataTypes.QUATERNION.getId(version);
        itemType = EntityDataTypes.ITEMSTACK.getId(version);
        blockType = EntityDataTypes.BLOCK_STATE.getId(version);
    }

    DisplayPacket frame(PartState part, int changes, int duration) {
        this.part = part;
        this.changes = changes;
        this.duration = duration;
        this.mode = FRAME;
        return this;
    }

    DisplayPacket spawn(PartState part, double range) {
        frame(part, ALL, 1);
        this.range = range;
        this.mode = SPAWN;
        return this;
    }

    DisplayPacket range(PartState part, double range) {
        this.part = part;
        this.range = range;
        this.mode = RANGE;
        return this;
    }

    @Override
    public void write() {
        writeVarInt(part.entityId);
        if (mode == RANGE) {
            writeViewRange();
            writeByte(END);
            return;
        }

        float[] s = part.sent;
        entry(START_DELAY_SLOT, intType);
        writeVarInt(0);
        entry(DURATION_SLOT, intType);
        writeVarInt(duration);

        if ((changes & TRANSLATION) != 0) {
            entry(TRANSLATION_SLOT, vectorType);
            writeFloat(s[Decomposer.TX]);
            writeFloat(s[Decomposer.TY]);
            writeFloat(s[Decomposer.TZ]);
        }
        if ((changes & LEFT_ROTATION) != 0) {
            entry(LEFT_ROTATION_SLOT, quaternionType);
            writeFloat(s[Decomposer.LX]);
            writeFloat(s[Decomposer.LY]);
            writeFloat(s[Decomposer.LZ]);
            writeFloat(s[Decomposer.LW]);
        }
        if ((changes & SCALE) != 0) {
            entry(SCALE_SLOT, vectorType);
            writeFloat(s[Decomposer.SX]);
            writeFloat(s[Decomposer.SY]);
            writeFloat(s[Decomposer.SZ]);
        }
        if ((changes & RIGHT_ROTATION) != 0) {
            entry(RIGHT_ROTATION_SLOT, quaternionType);
            writeFloat(s[Decomposer.RX]);
            writeFloat(s[Decomposer.RY]);
            writeFloat(s[Decomposer.RZ]);
            writeFloat(s[Decomposer.RW]);
        }

        if (mode == SPAWN) {
            writeViewRange();
            if (part.item != null) {
                entry(CONTENT_SLOT, itemType);
                writeItemStack(part.item);
                entry(ITEM_TRANSFORM_SLOT, byteType);
                writeByte(part.itemTransform);
            } else {
                entry(CONTENT_SLOT, blockType);
                writeVarInt(part.blockState);
            }
        }

        writeByte(END);
    }

    private void writeViewRange() {
        entry(VIEW_RANGE_SLOT, floatType);
        writeFloat((float) (range / BLOCKS_PER_VIEW_RANGE));
    }

    private void entry(int slot, int type) {
        writeByte(slot);
        writeVarInt(type);
    }
}
