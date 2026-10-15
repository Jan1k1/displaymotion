package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import io.netty.buffer.Unpooled;
import org.jan1k.displaymotion.Affine;
import org.jan1k.displaymotion.Decomposer;
import org.jan1k.displaymotion.Pose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayPacketTest {

    private static final ServerVersion VERSION = FakePacketEvents.VERSION;

    @BeforeAll
    static void fakePacketEvents() {
        FakePacketEvents.install();
    }

    @Test
    void fullFrameReadsBackAsTheSameTransform() {
        PartState part = block(42, 7, Pose.IDENTITY.withOffset(1f, 2f, 3f).withRotation(0f, 90f, 0f).withScale(0.5f));

        List<EntityData<?>> data = readBack(42, new DisplayPacket().frame(part, DisplayPacket.ALL, 2));

        assertEquals(6, data.size());
        assertEquals(0, value(data, 8));
        assertEquals(2, value(data, 9));
        Vector3f translation = (Vector3f) value(data, 11);
        assertEquals(1f, translation.x, 1e-6f);
        assertEquals(2f, translation.y, 1e-6f);
        assertEquals(3f, translation.z, 1e-6f);
        Vector3f scale = (Vector3f) value(data, 12);
        assertEquals(0.5f, scale.y, 1e-6f);
        Quaternion4f left = (Quaternion4f) value(data, 13);
        assertEquals((float) Math.sin(Math.PI / 4), Math.abs(left.getY()), 1e-6f);
        assertEquals((float) Math.cos(Math.PI / 4), Math.abs(left.getW()), 1e-6f);
        Quaternion4f right = (Quaternion4f) value(data, 14);
        assertEquals(1f, Math.abs(right.getW()), 1e-6f);
    }

    @Test
    void onlyChangedFieldsAreWritten() {
        PartState part = block(43, 7, Pose.IDENTITY.withRotation(0f, 30f, 0f));

        List<EntityData<?>> data = readBack(43, new DisplayPacket().frame(part, DisplayPacket.LEFT_ROTATION, 2));

        assertEquals(3, data.size());
        value(data, 13);
    }

    @Test
    void spawnAlsoCarriesTheBlockAndViewRange() {
        PartState part = block(44, 1234, Pose.IDENTITY);

        List<EntityData<?>> data = readBack(44, new DisplayPacket().spawn(part, 48, 0));

        assertEquals(15, data.size());
        assertEquals(0, value(data, 10));
        assertEquals(1, value(data, 9));
        assertEquals(0.75f, value(data, 17));
        assertEquals(1234, value(data, 23));
    }

    @Test
    void rangeUpdateOnlyCarriesTheViewRange() {
        PartState part = block(45, 1, Pose.IDENTITY);

        List<EntityData<?>> data = readBack(45, new DisplayPacket().range(part, 128));

        assertEquals(1, data.size());
        assertEquals(2f, value(data, 17));
    }

    @Test
    void lookCarriesGlowBillboardAndBrightness() {
        PartState part = block(46, 1, Pose.IDENTITY);
        part.glowing = true;
        part.glowColor = 0xFF8800;
        part.billboard = 3;
        part.brightness = 15 << 4 | 15 << 20;

        List<EntityData<?>> data = readBack(46, new DisplayPacket().look(part));

        assertEquals(4, data.size());
        assertEquals((byte) 0x40, value(data, 0));
        assertEquals((byte) 3, value(data, 15));
        assertEquals(15 << 4 | 15 << 20, value(data, 16));
        assertEquals(0xFF8800, value(data, 22));
    }

    @Test
    void textLookAlsoCarriesTheBackground() {
        PartState part = PartState.text("label", 48, -1, Component.text("hi"), Pose.IDENTITY);
        part.background = 0;

        List<EntityData<?>> data = readBack(48, new DisplayPacket().look(part));

        assertEquals(5, data.size());
        assertEquals(0, value(data, 25));
    }

    @Test
    void textPartCarriesItsText() {
        PartState part = PartState.text("label", 47, -1, Component.text("hello"), Pose.IDENTITY);

        List<EntityData<?>> data = readBack(47, new DisplayPacket().content(part));

        assertEquals(1, data.size());
        assertEquals(Component.text("hello"), value(data, 23));
    }

    private static PartState block(int entityId, int blockState, Pose pose) {
        PartState part = PartState.block("test", entityId, -1, blockState, Pose.IDENTITY);
        float[] m = new float[Affine.SIZE];
        Affine.fromPose(pose, m, 0);
        new Decomposer().decompose(m, 0, part.sent, 0);
        return part;
    }

    private static List<EntityData<?>> readBack(int entityId, DisplayPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.setBuffer(buffer);
        packet.write();
        PacketWrapper<?> reader = PacketWrapper.createUniversalPacketWrapper(buffer, VERSION);
        assertEquals(entityId, reader.readVarInt());
        List<EntityData<?>> data = reader.readEntityMetadata();
        assertEquals(0, buffer.readableBytes());
        return data;
    }

    private static Object value(List<EntityData<?>> data, int slot) {
        for (EntityData<?> entry : data) {
            if (entry.getIndex() == slot) {
                return entry.getValue();
            }
        }
        throw new AssertionError("no slot " + slot);
    }
}
