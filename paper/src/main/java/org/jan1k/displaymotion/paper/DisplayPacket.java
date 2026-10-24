package org.jan1k.displaymotion.paper;

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
    static final int BOX = 16;
    static final int VIEW = 32;

    private static final int FLAGS_SLOT = 0;
    private static final int START_DELAY_SLOT = 8;
    private static final int DURATION_SLOT = 9;
    private static final int MOVE_DURATION_SLOT = 10;
    private static final int TRANSLATION_SLOT = 11;
    private static final int SCALE_SLOT = 12;
    private static final int LEFT_ROTATION_SLOT = 13;
    private static final int RIGHT_ROTATION_SLOT = 14;
    private static final int BILLBOARD_SLOT = 15;
    private static final int BRIGHTNESS_SLOT = 16;
    private static final int VIEW_RANGE_SLOT = 17;
    private static final int SHADOW_RADIUS_SLOT = 18;
    private static final int SHADOW_STRENGTH_SLOT = 19;
    private static final int WIDTH_SLOT = 20;
    private static final int HEIGHT_SLOT = 21;
    private static final int GLOW_COLOR_SLOT = 22;
    private static final int CONTENT_SLOT = 23;
    private static final int ITEM_TRANSFORM_SLOT = 24;
    private static final int LINE_WIDTH_SLOT = 24;
    private static final int BACKGROUND_SLOT = 25;
    private static final int TEXT_OPACITY_SLOT = 26;
    private static final int TEXT_FLAGS_SLOT = 27;
    private static final int END = 0xFF;
    private static final int GLOWING_FLAG = 0x40;
    // client hides displays past view_range * 64 blocks on its own
    private static final double BLOCKS_PER_VIEW_RANGE = 64.0;

    private static final int FRAME = 0;
    private static final int SPAWN = 1;
    private static final int RANGE = 2;
    private static final int LOOK = 3;
    private static final int CONTENT = 4;
    private static final int MOVE = 5;

    private final int intType;
    private final int byteType;
    private final int floatType;
    private final int vectorType;
    private final int quaternionType;
    private final int itemType;
    private final int blockType;
    private final int componentType;

    private PartState part;
    private net.kyori.adventure.text.Component text;
    private float[] values;
    private int changes;
    private int duration;
    private double range;
    private int moveTicks;
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
        componentType = EntityDataTypes.ADV_COMPONENT.getId(version);
    }

    DisplayPacket frame(PartState part, int changes, int duration, double range) {
        return frame(part, part.sent, changes, duration, range);
    }

    // values is part.sent, or part.own for the player that wears a riding display
    DisplayPacket frame(PartState part, float[] values, int changes, int duration, double range) {
        set(part, FRAME, changes, duration, range);
        this.values = values;
        return this;
    }

    DisplayPacket spawn(PartState part, double range, int moveTicks) {
        return spawn(part, part.sent, range, moveTicks);
    }

    DisplayPacket spawn(PartState part, float[] values, double range, int moveTicks) {
        this.moveTicks = moveTicks;
        set(part, SPAWN, ALL, 1, range);
        this.values = values;
        return this;
    }

    DisplayPacket move(PartState part, int moveTicks) {
        this.moveTicks = moveTicks;
        return set(part, MOVE, 0, 0, 0);
    }

    DisplayPacket range(PartState part, double range) {
        return set(part, RANGE, 0, 0, range);
    }

    DisplayPacket look(PartState part) {
        return set(part, LOOK, 0, 0, 0);
    }

    DisplayPacket content(PartState part) {
        return set(part, CONTENT, 0, 0, 0);
    }

    // a text part with the text of one player instead of the shared one
    DisplayPacket content(PartState part, net.kyori.adventure.text.Component text) {
        set(part, CONTENT, 0, 0, 0);
        this.text = text;
        return this;
    }

    private DisplayPacket set(PartState part, int mode, int changes, int duration, double range) {
        this.part = part;
        this.values = part.sent;
        this.text = part.text;
        this.mode = mode;
        this.changes = changes;
        this.duration = duration;
        this.range = range;
        return this;
    }

    @Override
    public void write() {
        writeVarInt(part.entityId);
        switch (mode) {
            case RANGE -> writeViewRange();
            case LOOK -> writeLook();
            case CONTENT -> writeContent();
            case MOVE -> writeMoveDuration();
            default -> {
                writeTransform();
                if (mode == SPAWN) {
                    writeBox();
                    writeMoveDuration();
                    writeViewRange();
                    writeLook();
                    writeContent();
                }
            }
        }
        writeByte(END);
    }

    private void writeTransform() {
        float[] s = values;
        // box only update, dont restart the transform blend or it stutters
        if ((changes & ALL) != 0) {
            entry(START_DELAY_SLOT, intType);
            writeVarInt(0);
            entry(DURATION_SLOT, intType);
            writeVarInt(duration);
        }
        if ((changes & BOX) != 0) {
            writeBox();
        }
        if ((changes & VIEW) != 0) {
            writeViewRange();
        }

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
    }

    private void writeBox() {
        entry(WIDTH_SLOT, floatType);
        writeFloat(part.cullWidth);
        entry(HEIGHT_SLOT, floatType);
        writeFloat(part.cullHeight);
    }

    private void writeMoveDuration() {
        entry(MOVE_DURATION_SLOT, intType);
        writeVarInt(moveTicks);
    }

    // 0 for a hidden part, the client checks distance < view_range * 64 so it never draws it.
    // else the smaller of the display range and the size range, see Composer.sizeRange
    private void writeViewRange() {
        entry(VIEW_RANGE_SLOT, floatType);
        writeFloat(part.hidden ? 0f : (float) (Math.min(range, part.sizeRange) / BLOCKS_PER_VIEW_RANGE));
    }

    private void writeLook() {
        entry(FLAGS_SLOT, byteType);
        writeByte(part.glowing ? GLOWING_FLAG : 0);
        entry(BILLBOARD_SLOT, byteType);
        writeByte(part.billboard);
        entry(BRIGHTNESS_SLOT, intType);
        writeVarInt(part.brightness);
        entry(GLOW_COLOR_SLOT, intType);
        writeVarInt(part.glowColor);
        entry(SHADOW_RADIUS_SLOT, floatType);
        writeFloat(part.shadowRadius);
        entry(SHADOW_STRENGTH_SLOT, floatType);
        writeFloat(part.shadowStrength);
        if (part.kind == PartState.TEXT) {
            entry(LINE_WIDTH_SLOT, intType);
            writeVarInt(part.lineWidth);
            entry(BACKGROUND_SLOT, intType);
            writeVarInt(part.background);
            entry(TEXT_OPACITY_SLOT, byteType);
            writeByte(part.textOpacity);
            entry(TEXT_FLAGS_SLOT, byteType);
            writeByte(part.textFlags());
        }
    }

    private void writeContent() {
        switch (part.kind) {
            case PartState.ITEM -> {
                entry(CONTENT_SLOT, itemType);
                writeItemStack(part.item);
                entry(ITEM_TRANSFORM_SLOT, byteType);
                writeByte(part.itemTransform);
            }
            case PartState.BLOCK -> {
                entry(CONTENT_SLOT, blockType);
                writeVarInt(part.blockState);
            }
            default -> {
                entry(CONTENT_SLOT, componentType);
                writeComponent(text);
            }
        }
    }

    private void entry(int slot, int type) {
        writeByte(slot);
        writeVarInt(type);
    }
}
