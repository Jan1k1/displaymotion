package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Frames;
import org.jan1k.displaymotion.Pose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayPacketTest {

    private static final ServerVersion VERSION = ServerVersion.V_26_3;

    @BeforeAll
    static void fakePacketEvents() {
        NettyManager netty = new NettyManagerImpl();
        ServerManager server = () -> VERSION;
        PacketEvents.setAPI(new PacketEventsAPI<>() {
            @Override
            public boolean isLoaded() {
                return true;
            }

            @Override
            public void init() {
            }

            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public Object getPlugin() {
                return null;
            }

            @Override
            public ServerManager getServerManager() {
                return server;
            }

            @Override
            public ProtocolManager getProtocolManager() {
                return null;
            }

            @Override
            public PlayerManager getPlayerManager() {
                return null;
            }

            @Override
            public NettyManager getNettyManager() {
                return netty;
            }

            @Override
            public ChannelInjector getInjector() {
                return null;
            }
        });
    }

    @Test
    void frameUpdateReadsBackAsTheSameTransform() {
        DisplayState display = block(42, 7);
        Pose pose = Pose.IDENTITY.withOffset(1f, 2f, 3f).withRotation(0f, 90f, 0f).withScale(0.5f);
        display.frames = Frames.bake(Animation.keyframes().at(0, pose).build(), false);

        List<EntityData<?>> data = readBack(42, new DisplayPacket().frame(display, 0, 2));

        assertEquals(5, data.size());
        assertEquals(0, value(data, 8));
        assertEquals(2, value(data, 9));
        Vector3f translation = (Vector3f) value(data, 11);
        assertEquals(1f, translation.x);
        assertEquals(2f, translation.y);
        assertEquals(3f, translation.z);
        Vector3f scale = (Vector3f) value(data, 12);
        assertEquals(0.5f, scale.y);
        Quaternion4f rotation = (Quaternion4f) value(data, 13);
        assertEquals((float) Math.sin(Math.PI / 4), rotation.getY(), 1e-6f);
        assertEquals((float) Math.cos(Math.PI / 4), rotation.getW(), 1e-6f);
    }

    @Test
    void spawnAlsoCarriesTheBlock() {
        DisplayState display = block(43, 1234);
        display.frames = Frames.bake(Animation.keyframes().at(0, Pose.IDENTITY).build(), true);

        List<EntityData<?>> data = readBack(43, new DisplayPacket().spawn(display));

        assertEquals(7, data.size());
        assertEquals(1, value(data, 9));
        assertEquals(0.75f, value(data, 17));
        assertEquals(1234, value(data, 23));
        Vector3f translation = (Vector3f) value(data, 11);
        assertEquals(-0.5f, translation.x);
    }

    @Test
    void rangeUpdateOnlyCarriesTheViewRange() {
        DisplayState display = block(44, 1);
        display.range = 128;

        List<EntityData<?>> data = readBack(44, new DisplayPacket().range(display));

        assertEquals(1, data.size());
        assertEquals(2f, value(data, 17));
    }

    private static DisplayState block(int entityId, int blockState) {
        return new DisplayState(entityId, UUID.randomUUID(), 0, 0, 0, null, 0, blockState, 48, null);
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
