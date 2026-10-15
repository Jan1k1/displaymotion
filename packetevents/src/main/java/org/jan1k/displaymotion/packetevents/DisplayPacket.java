package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import org.jan1k.displaymotion.Frames;

final class DisplayPacket extends PacketWrapper<DisplayPacket> {

    // slots checked against the Paper 26.3 server jar
    private static final int START_DELAY = 8;
    private static final int DURATION = 9;
    private static final int TRANSLATION = 11;
    private static final int SCALE = 12;
    private static final int LEFT_ROTATION = 13;
    private static final int VIEW_RANGE = 17;
    private static final int CONTENT = 23;
    private static final int ITEM_TRANSFORM = 24;
    private static final int END = 0xFF;
    // vanilla view range 1.0 is 64 blocks
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

    private DisplayState display;
    private int frame;
    private int duration;
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

    DisplayPacket frame(DisplayState display, int frame, int duration) {
        this.display = display;
        this.frame = frame;
        this.duration = duration;
        this.mode = FRAME;
        return this;
    }

    DisplayPacket spawn(DisplayState display) {
        frame(display, display.frame, 1);
        this.mode = SPAWN;
        return this;
    }

    DisplayPacket range(DisplayState display) {
        this.display = display;
        this.mode = RANGE;
        return this;
    }

    @Override
    public void write() {
        writeVarInt(display.entityId);
        if (mode == RANGE) {
            writeViewRange();
            writeByte(END);
            return;
        }

        Frames frames = display.frames;
        entry(START_DELAY, intType);
        writeVarInt(0);
        entry(DURATION, intType);
        writeVarInt(duration);

        entry(TRANSLATION, vectorType);
        writeFloat(frames.value(frame, Frames.TX));
        writeFloat(frames.value(frame, Frames.TY));
        writeFloat(frames.value(frame, Frames.TZ));

        entry(SCALE, vectorType);
        writeFloat(frames.value(frame, Frames.SX));
        writeFloat(frames.value(frame, Frames.SY));
        writeFloat(frames.value(frame, Frames.SZ));

        entry(LEFT_ROTATION, quaternionType);
        writeFloat(frames.value(frame, Frames.QX));
        writeFloat(frames.value(frame, Frames.QY));
        writeFloat(frames.value(frame, Frames.QZ));
        writeFloat(frames.value(frame, Frames.QW));

        if (mode == SPAWN) {
            writeViewRange();
            if (display.item != null) {
                entry(CONTENT, itemType);
                writeItemStack(display.item);
                entry(ITEM_TRANSFORM, byteType);
                writeByte(display.itemTransform);
            } else {
                entry(CONTENT, blockType);
                writeVarInt(display.blockState);
            }
        }

        writeByte(END);
    }

    private void writeViewRange() {
        entry(VIEW_RANGE, floatType);
        writeFloat((float) (display.range / BLOCKS_PER_VIEW_RANGE));
    }

    private void entry(int slot, int type) {
        writeByte(slot);
        writeVarInt(type);
    }
}
