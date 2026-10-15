package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.netty.buffer.ByteBuf;
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

        List<EntityData<?>> data = readBack(44, new DisplayPacket().spawn(part, 48));

        assertEquals(8, data.size());
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

    private static PartState block(int entityId, int blockState, Pose pose) {
        PartState part = new PartState("test", entityId, -1, null, 0, blockState, Pose.IDENTITY);
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
