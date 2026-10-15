package org.jan1k.displaymotion.packetevents;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jan1k.displaymotion.Animations;
import org.jan1k.displaymotion.Pose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotionEngineTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();
    private static final int ENTITY = 1000;

    private MotionEngine engine;
    private EmbeddedChannel channel;
    private long sequence;

    record Packet(String kind, int entity, List<EntityData<?>> data) {
    }

    @BeforeAll
    static void fakePacketEvents() {
        FakePacketEvents.install();
    }

    @BeforeEach
    void setUp() {
        FakePacketEvents.WRITTEN.clear();
        Plugin plugin = FakePacketEvents.proxy(Plugin.class, (method, args) -> switch (method) {
            case "getName" -> "test";
            case "getLogger" -> Logger.getLogger("displaymotion-test");
            case "isEnabled" -> false;
            default -> null;
        });
        engine = new MotionEngine(plugin, false);
        channel = new EmbeddedChannel();
        viewerAt(0);
    }

    @AfterEach
    void everyBufferIsReleased() {
        engine.close();
        channel.runPendingTasks();
        drain();
        for (ByteBuf buffer : FakePacketEvents.WRITTEN) {
            assertEquals(0, buffer.refCnt(), "a buffer leaked");
        }
        channel.finishAndReleaseAll();
    }

    @Test
    void spawnWaitsOneTickAndStartsAtTheFirstFrame() {
        display().play(Animations.popIn(20));

        assertEquals(List.of(), tick());
        List<Packet> packets = tick();

        assertEquals(List.of("bundle", "spawn", "meta", "bundle"), kinds(packets));
        assertEquals(0f, ((Vector3f) value(packets.get(2), 12)).x);
    }

    @Test
    void framesGrowAndThenStop() {
        display().play(Animations.popIn(20));
        tick();
        tick();

        List<Float> scales = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            for (Packet packet : tick()) {
                assertEquals("meta", packet.kind());
                scales.add(((Vector3f) value(packet, 12)).x);
            }
        }

        assertEquals(10, scales.size());
        for (int i = 1; i < scales.size(); i++) {
            assertTrue(scales.get(i) > scales.get(i - 1));
        }
        assertEquals(1f, scales.getLast(), 1e-6f);
    }

    @Test
    void removeSendsOneDestroy() {
        MotionDisplay display = display();
        tick();
        tick();

        display.remove();
        List<Packet> packets = tick();

        assertEquals(List.of("destroy"), kinds(packets));
        assertEquals(ENTITY, packets.getFirst().entity());
    }

    @Test
    void glowIsSentToViewersRightAway() {
        MotionDisplay display = display();
        tick();
        tick();

        display.glow(Color.ORANGE);
        List<Packet> packets = tick();

        assertEquals(List.of("meta"), kinds(packets));
        assertEquals((byte) 0x40, value(packets.getFirst(), 0));
        assertEquals(Color.ORANGE.asRGB(), value(packets.getFirst(), 22));
    }

    @Test
    void glowSetBeforeSpawnIsInTheSpawnPacket() {
        display().glow(Color.RED);
        tick();
        List<Packet> packets = tick();

        assertEquals(Color.RED.asRGB(), value(packets.get(2), 22));
    }

    @Test
    void teleportMovesEveryPartAndSetsTheGlideTime() {
        MotionDisplay display = display();
        tick();
        tick();

        display.teleport(new Location(world(), 5, 0, 0), 10);
        List<Packet> packets = tick();

        assertEquals(List.of("bundle", "meta", "teleport", "bundle"), kinds(packets));
        assertEquals(10, value(packets.get(1), 10));
        assertEquals(ENTITY, packets.get(2).entity());

        display.teleport(new Location(world(), 6, 0, 0), 10);
        assertEquals(List.of("teleport"), kinds(tick()));
    }

    @Test
    void teleportOutOfRangeHidesIt() {
        MotionDisplay display = display();
        tick();
        tick();

        display.teleport(new Location(world(), 500, 0, 0));

        assertEquals(List.of("destroy"), kinds(tick()));
    }

    @Test
    void teleportToAnotherWorldHidesItWithoutAMove() {
        MotionDisplay display = display();
        tick();
        tick();

        UUID other = UUID.randomUUID();
        World otherWorld = FakePacketEvents.proxy(World.class, (method, args) -> method.equals("getUID") ? other : null);
        display.teleport(new Location(otherWorld, 0, 0, 0));

        assertEquals(List.of("destroy"), kinds(tick()));
    }

    @Test
    void partsAboveTheSpawnPointGetACullingBox() {
        PartState[] parts = {
                PartState.block("low", ENTITY, -1, 1, Pose.IDENTITY),
                PartState.block("high", ENTITY + 1, -1, 1, Pose.IDENTITY.withOffset(0f, 2f, 0f))
        };
        MotionDisplay display = new MotionDisplay(engine, new Location(world(), 0, 0, 0), parts, 48);
        engine.submit(() -> engine.add(display.state()));
        tick();
        List<Packet> packets = tick();

        Packet low = packets.stream().filter(p -> p.kind().equals("meta") && p.entity() == ENTITY).findFirst().orElseThrow();
        Packet high = packets.stream().filter(p -> p.kind().equals("meta") && p.entity() == ENTITY + 1).findFirst().orElseThrow();
        assertEquals(0f, value(low, 20));
        assertTrue((float) value(high, 20) > 1.4f);
        assertTrue((float) value(high, 21) >= 2.5f);
    }

    @Test
    void groupsAreNeverSpawnedButMoveTheirChildren() {
        PartState[] parts = {
                PartState.group("pivot", -1, Pose.IDENTITY.withOffset(0f, 1f, 0f)),
                PartState.block("arm", ENTITY, 0, 1, Pose.IDENTITY.withOffset(1f, 0f, 0f))
        };
        MotionDisplay display = new MotionDisplay(engine, new Location(world(), 0, 0, 0), parts, 48);
        engine.submit(() -> engine.add(display.state()));
        tick();
        List<Packet> spawn = tick();

        assertEquals(List.of("bundle", "spawn", "meta", "bundle"), kinds(spawn));
        assertEquals(ENTITY, spawn.get(1).entity());

        display.play("pivot", Animations.turn(0f, 90f, 0f, 20));
        List<Packet> moves = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            moves.addAll(tick());
        }
        assertTrue(moves.stream().anyMatch(p -> p.kind().equals("meta") && p.entity() == ENTITY));
        assertTrue(moves.stream().allMatch(p -> p.entity() == ENTITY || p.kind().equals("bundle")));

        display.remove();
        List<Packet> gone = tick();
        assertEquals(List.of("destroy"), kinds(gone));
    }

    @Test
    void matrixPartsWorkLikeVanilla() {
        float[] doubleSize = {2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 1};
        float[] oneRight = {1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        PartState[] parts = {
                PartState.matrix("big", -1, -1, PartState.GROUP, null, 0, 0, null, doubleSize),
                PartState.matrix("cube", ENTITY, 0, PartState.BLOCK, null, 0, 1, null, oneRight)
        };
        MotionDisplay display = new MotionDisplay(engine, new Location(world(), 0, 0, 0), parts, 48);
        engine.submit(() -> engine.add(display.state()));
        tick();
        Packet meta = tick().get(2);

        Vector3f translation = (Vector3f) value(meta, 11);
        Vector3f scale = (Vector3f) value(meta, 12);
        assertEquals(2f, translation.x, 1e-5f);
        assertEquals(0f, translation.y, 1e-5f);
        assertEquals(2f, scale.x, 1e-5f);
    }

    @Test
    void hiddenPlayersDoNotSeeIt() {
        MotionDisplay display = display();
        tick();
        tick();

        display.hide(player());
        assertEquals(List.of("destroy"), kinds(tick()));
        display.show(player());
        assertEquals(List.of("bundle", "spawn", "meta", "bundle"), kinds(tick()));
    }

    @Test
    void privateDisplayOnlyShowsToChosenPlayers() {
        MotionDisplay display = display().visibleByDefault(false);
        tick();
        assertEquals(List.of(), tick());

        display.show(player());
        assertEquals(List.of("bundle", "spawn", "meta", "bundle"), kinds(tick()));
    }

    @Test
    void viewerAtTheEdgeDoesNotFlicker() {
        display();
        tick();
        tick();

        viewerAt(50);
        assertEquals(List.of(), tick());
        viewerAt(53);
        assertEquals(List.of("destroy"), kinds(tick()));
        viewerAt(50);
        assertEquals(List.of(), tick());
        viewerAt(47);
        assertEquals(List.of("bundle", "spawn", "meta", "bundle"), kinds(tick()));
    }

    @Test
    void resetWaitsForAPositionTakenAfterTheMove() {
        display();
        tick();
        tick();

        long now = sequence;
        engine.submit(() -> engine.viewerReset(PLAYER, now));
        assertEquals(List.of("destroy"), kinds(tick()));
        engine.publish(new Snapshot(now, new PlayerView[]{new PlayerView(PLAYER, WORLD, 0, 0, 0, channel)}));
        assertEquals(List.of(), tick());
        viewerAt(0);
        assertEquals(List.of("bundle", "spawn", "meta", "bundle"), kinds(tick()));
    }

    @Test
    void stalledViewerSkipsFramesAndGetsTheFullStateBack() {
        display().play(Animations.spin(20));
        tick();
        tick();

        channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
        for (int i = 0; i < 6; i++) {
            assertEquals(List.of(), tick());
        }
        channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true);
        List<Packet> packets = tick();

        assertTrue(kinds(packets).contains("meta"));
        Packet full = packets.stream().filter(p -> p.kind().equals("meta") && p.data().size() > 6).findFirst().orElseThrow();
        assertEquals(1, value(full, 23));
    }

    @Test
    void bigQueuesAreSplitIntoBundles() {
        Viewer viewer = new Viewer(PLAYER);
        for (int i = 0; i < Viewer.MAX_BUNDLE + 1; i++) {
            viewer.queue(Unpooled.buffer(1));
        }
        ByteBuf bundle = Unpooled.buffer(1);

        Object[] out = viewer.drain(bundle);

        int delimiters = 0;
        for (Object buffer : out) {
            ByteBuf b = (ByteBuf) buffer;
            if (b.unwrap() == bundle) {
                delimiters++;
            }
            b.release();
        }
        assertEquals(4, delimiters);
        assertEquals(Viewer.MAX_BUNDLE + 1 + 4, out.length);
        bundle.release();
    }

    private static Player player() {
        return FakePacketEvents.proxy(Player.class, (method, args) -> method.equals("getUniqueId") ? PLAYER : null);
    }

    private static World world() {
        return FakePacketEvents.proxy(World.class, (method, args) -> method.equals("getUID") ? WORLD : null);
    }

    private MotionDisplay display() {
        PartState[] parts = {PartState.block("main", ENTITY, -1, 1, Pose.IDENTITY)};
        MotionDisplay display = new MotionDisplay(engine, new Location(world(), 0, 0, 0), parts, 48);
        engine.submit(() -> engine.add(display.state()));
        return display;
    }

    private void viewerAt(double x) {
        engine.publish(new Snapshot(++sequence, new PlayerView[]{new PlayerView(PLAYER, WORLD, x, 0, 0, channel)}));
    }

    private List<Packet> tick() {
        engine.runTick();
        channel.runPendingTasks();
        return drain();
    }

    private List<Packet> drain() {
        ClientVersion version = FakePacketEvents.VERSION.toClientVersion();
        List<Packet> packets = new ArrayList<>();
        ByteBuf buffer;
        while ((buffer = channel.readOutbound()) != null) {
            try {
                PacketWrapper<?> reader = PacketWrapper.createUniversalPacketWrapper(buffer.duplicate(), FakePacketEvents.VERSION);
                int id = reader.readVarInt();
                if (id == PacketType.Play.Server.BUNDLE.getId(version)) {
                    packets.add(new Packet("bundle", -1, List.of()));
                } else if (id == PacketType.Play.Server.SPAWN_ENTITY.getId(version)) {
                    packets.add(new Packet("spawn", reader.readVarInt(), List.of()));
                } else if (id == PacketType.Play.Server.ENTITY_METADATA.getId(version)) {
                    int entity = reader.readVarInt();
                    packets.add(new Packet("meta", entity, reader.readEntityMetadata()));
                } else if (id == PacketType.Play.Server.ENTITY_TELEPORT.getId(version)) {
                    packets.add(new Packet("teleport", reader.readVarInt(), List.of()));
                } else if (id == PacketType.Play.Server.DESTROY_ENTITIES.getId(version)) {
                    reader.readVarInt();
                    packets.add(new Packet("destroy", reader.readVarInt(), List.of()));
                } else {
                    packets.add(new Packet("other " + id, -1, List.of()));
                }
            } finally {
                buffer.release();
            }
        }
        return packets;
    }

    private static List<String> kinds(List<Packet> packets) {
        return packets.stream().map(Packet::kind).toList();
    }

    private static Object value(Packet packet, int slot) {
        for (EntityData<?> entry : packet.data()) {
            if (entry.getIndex() == slot) {
                return entry.getValue();
            }
        }
        throw new AssertionError("no slot " + slot + " in " + packet);
    }
}
