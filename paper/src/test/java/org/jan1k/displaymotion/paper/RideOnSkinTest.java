package org.jan1k.displaymotion.paper;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.util.LpVector3d;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jan1k.displaymotion.Pose;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// reads the real packets and puts every corner where the 1.21.11 client draws it, then checks it against
// the player skin the client draws. none of our own matrix math is used on the client side here:
// DisplayRenderer.submit, Transformation, Entity.positionRider, LivingEntityRenderer.submit, AvatarRenderer
// and HumanoidModel.setupAnim, all with joml like the client
class RideOnSkinTest {

    private static final float DEG = (float) (Math.PI / 180.0);
    private static final float CLOSE = 0.002f;
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID WEARER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final int VEHICLE = 500;

    private static final Pose[] HAT = {
            Pose.IDENTITY.withOffset(0f, 0.04f, 0f).withScale(0.5f, 0.12f, 0.5f),
            Pose.IDENTITY.withOffset(0.2f, 0.17f, -0.2f).withScale(0.1f, 0.14f, 0.1f),
            Pose.IDENTITY.withOffset(0f, 0.32f, 0f).withScale(0.35f)
    };
    private static final Pose[] BACKPACK = {
            Pose.IDENTITY.withOffset(0f, -0.74f, -0.235f).withScale(0.45f, 0.55f, 0.25f),
            Pose.IDENTITY.withOffset(0f, -0.52f, -0.37f).withScale(0.4f, 0.2f, 0.02f)
    };

    private static final class Client {
        final EmbeddedChannel channel = new EmbeddedChannel();
        final Map<Integer, float[]> turn = new HashMap<>();
        final Map<Integer, Object[]> transform = new HashMap<>();
    }

    private MotionEngine engine;
    private Client wearer;
    private Client other;
    private MotionDisplay display;

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
        wearer = new Client();
        other = new Client();
        engine.publish(new Snapshot(1, new PlayerView[]{
                new PlayerView(WEARER, WORLD, 0, 0, 0, wearer.channel),
                new PlayerView(OTHER, WORLD, 0, 0, 0, other.channel)}));
    }

    @AfterEach
    void everyBufferIsReleased() {
        engine.close();
        for (Client client : new Client[]{wearer, other}) {
            client.channel.runPendingTasks();
            read(client);
            client.channel.finishAndReleaseAll();
        }
        for (ByteBuf buffer : FakePacketEvents.WRITTEN) {
            assertEquals(0, buffer.refCnt(), "a buffer leaked");
        }
    }

    @Test
    void hatMadeWhileLookingDownStillSitsOnTheHead() {
        // the model got the pitch of the player that made it, the client used to lean the whole hat by it
        ride(HAT, -120f, 35f, false, new RidePose(-120f, 35f, MotionDisplay.HEAD_PIVOT, 0f));
        follow(new RidePose(230f, 45f, MotionDisplay.HEAD_PIVOT, 0f));

        assertOnHead(other, 200f, 230f, 45f, false);
        assertOnHead(wearer, 200f, 230f, 45f, false);
    }

    @Test
    void sneakingHatKeepsItsPlaceOnTheTiltedHead() {
        ride(HAT, 10f, 0f, false, new RidePose(10f, 0f, MotionDisplay.HEAD_PIVOT, 0f));
        follow(new RidePose(120f, -45f, MotionDisplay.HEAD_PIVOT_SNEAKING, MotionDisplay.HEAD_DROP_SNEAKING));

        assertOnHead(other, 90f, 120f, -45f, true);
    }

    @Test
    void hatOnADisplayThatWasAlreadyShownGetsLevelledFirst() {
        ride(HAT, 10f, -20f, true, new RidePose(10f, -20f, MotionDisplay.HEAD_PIVOT, 0f));
        follow(new RidePose(30f, 0f, MotionDisplay.HEAD_PIVOT, 0f));

        assertOnHead(other, 0f, 30f, 0f, false);
    }

    @Test
    void backpackSitsOnTheBackForOthersAndForTheWearer() {
        // the wearer's own game started the body at 0 and sees it 40 off from what the others see
        ride(BACKPACK, -37f, -60f, false, new RidePose(-37f, 0f, 0f, 0f, -77f));
        follow(new RidePose(90f, 0f, 0f, 0f, 50f));

        assertOnBack(other, 90f, false);
        assertOnBack(wearer, 50f, false);
    }

    @Test
    void sneakingBackpackLeansWithTheBody() {
        ride(BACKPACK, 0f, 0f, true, new RidePose(0f, 0f, 0f, 0f, -40f));
        follow(new RidePose(200f, MotionDisplay.BODY_LEAN_SNEAKING, MotionDisplay.BODY_PIVOT_SNEAKING, MotionDisplay.BODY_DROP_SNEAKING, 160f));

        assertOnBack(other, 200f, true);
        assertOnBack(wearer, 160f, true);
    }

    private void ride(Pose[] model, float yaw, float pitch, boolean shownFirst, RidePose pose) {
        PartState[] parts = new PartState[model.length];
        for (int i = 0; i < model.length; i++) {
            parts[i] = PartState.block("part" + i, i + 1, -1, 1, model[i]);
        }
        World world = FakePacketEvents.proxy(World.class, (method, args) -> method.equals("getUID") ? WORLD : null);
        MotionDisplay display = new MotionDisplay(engine, new Location(world, 0, 0, 0, yaw, pitch), parts, 48);
        engine.submit(() -> engine.add(display.state()));
        if (shownFirst) {
            tick();
            tick();
        }
        engine.submit(() -> engine.ride(display.state(), VEHICLE, new int[0], WORLD, 0, 0, 0, pose, WEARER));
        tick();
        tick();
        this.display = display;
    }

    private void follow(RidePose pose) {
        engine.submit(() -> engine.follow(display.state(), WORLD, 0, 0, 0, pose));
        tick();
    }

    private void assertOnHead(Client client, float body, float head, float pitch, boolean sneak) {
        assertGlued(client, HAT, head(0f, 0f, 0f, false), head(body, head, pitch, sneak), sneak);
    }

    private void assertOnBack(Client client, float body, boolean sneak) {
        assertGlued(client, BACKPACK, body(0f, false), body(body, sneak), sneak);
    }

    // every corner has to sit on the bone like it does in the plain pose (standing, all at 0)
    private void assertGlued(Client client, Pose[] model, Matrix4f plainBone, Matrix4f bone, boolean sneak) {
        Matrix4f back = new Matrix4f(plainBone).invert();
        for (int id = 1; id <= model.length; id++) {
            Matrix4f plain = new Matrix4f().translation(0f, 1.8f, 0f).mul(local(model[id - 1]));
            Matrix4f drawn = drawn(client, id, sneak);
            for (int c = 0; c < 8; c++) {
                Vector3f corner = new Vector3f(c & 1, (c >> 1) & 1, (c >> 2) & 1);
                Vector3f expected = bone.transformPosition(back.transformPosition(plain.transformPosition(new Vector3f(corner))));
                Vector3f actual = drawn.transformPosition(new Vector3f(corner));
                assertTrue(expected.distance(actual) < CLOSE, "part " + id + " is " + expected.distance(actual) + " blocks off the skin");
            }
        }
    }

    // a centered block part with no turn, the way the model was made
    private static Matrix4f local(Pose pose) {
        return new Matrix4f().translation(pose.x(), pose.y(), pose.z())
                .scale(pose.scaleX(), pose.scaleY(), pose.scaleZ())
                .translate(-0.5f, -0.5f, -0.5f);
    }

    // Entity.positionRider puts it on the passenger point, then DisplayRenderer turns it by the entity yaw and
    // pitch and puts the Transformation (translation, left rotation, scale, right rotation) on top
    private static Matrix4f drawn(Client client, int id, boolean sneak) {
        float[] turn = client.turn.get(id);
        Object[] t = client.transform.get(id);
        Vector3f translation = (Vector3f) t[0];
        Quaternion4f left = (Quaternion4f) t[1];
        Vector3f scale = (Vector3f) t[2];
        Quaternion4f right = (Quaternion4f) t[3];
        return new Matrix4f().translation(0f, sneak ? 1.5f : 1.8f, 0f)
                .rotate(new Quaternionf().rotationYXZ(-DEG * turn[0], DEG * turn[1], 0f))
                .translate(translation)
                .rotate(new Quaternionf(left.getX(), left.getY(), left.getZ(), left.getW()))
                .scale(scale)
                .rotate(new Quaternionf(right.getX(), right.getY(), right.getZ(), right.getW()));
    }

    // LivingEntityRenderer.submit with AvatarRenderer: crouch moves it 2 pixels down, body turn, flip, 0.9375, 1.501
    private static Matrix4f model(float body, boolean sneak) {
        return new Matrix4f().translate(0f, sneak ? -2f / 16f : 0f, 0f)
                .rotate(new Quaternionf().rotationY(DEG * (180f - body)))
                .scale(-1f, -1f, 1f)
                .scale(0.9375f)
                .translate(0f, -1.501f, 0f);
    }

    private static Matrix4f head(float body, float head, float pitch, boolean sneak) {
        return model(body, sneak).translate(0f, (sneak ? 4.2f : 0f) / 16f, 0f)
                .rotate(new Quaternionf().rotationZYX(0f, Bodies.wrap(head - body) * DEG, pitch * DEG));
    }

    private static Matrix4f body(float body, boolean sneak) {
        return model(body, sneak).translate(0f, (sneak ? 3.2f : 0f) / 16f, 0f)
                .rotate(new Quaternionf().rotationZYX(0f, 0f, sneak ? 0.5f : 0f));
    }

    private void tick() {
        engine.runTick();
        for (Client client : new Client[]{wearer, other}) {
            client.channel.runPendingTasks();
            read(client);
        }
    }

    private static void read(Client client) {
        ClientVersion version = FakePacketEvents.VERSION.toClientVersion();
        ByteBuf buffer;
        while ((buffer = client.channel.readOutbound()) != null) {
            try {
                PacketWrapper<?> reader = PacketWrapper.createUniversalPacketWrapper(buffer.duplicate(), FakePacketEvents.VERSION);
                int id = reader.readVarInt();
                if (id == PacketType.Play.Server.SPAWN_ENTITY.getId(version)) {
                    int entity = reader.readVarInt();
                    reader.readUUID();
                    reader.readVarInt();
                    reader.readDouble();
                    reader.readDouble();
                    reader.readDouble();
                    LpVector3d.read(reader);
                    // angles come as a byte each
                    float pitch = reader.readByte() * 360f / 256f;
                    float yaw = reader.readByte() * 360f / 256f;
                    client.turn.put(entity, new float[]{yaw, pitch});
                } else if (id == PacketType.Play.Server.ENTITY_TELEPORT.getId(version)) {
                    int entity = reader.readVarInt();
                    for (int i = 0; i < 6; i++) {
                        reader.readDouble();
                    }
                    float yaw = reader.readFloat();
                    float pitch = reader.readFloat();
                    client.turn.put(entity, new float[]{yaw, pitch});
                } else if (id == PacketType.Play.Server.ENTITY_METADATA.getId(version)) {
                    int entity = reader.readVarInt();
                    Object[] t = client.transform.computeIfAbsent(entity, e -> new Object[]{
                            new Vector3f(), new Quaternion4f(0, 0, 0, 1), new Vector3f(1, 1, 1), new Quaternion4f(0, 0, 0, 1)});
                    for (EntityData<?> data : reader.readEntityMetadata()) {
                        switch (data.getIndex()) {
                            case 11 -> t[0] = vector(data.getValue());
                            case 12 -> t[2] = vector(data.getValue());
                            case 13 -> t[1] = data.getValue();
                            case 14 -> t[3] = data.getValue();
                            default -> {
                            }
                        }
                    }
                }
            } finally {
                buffer.release();
            }
        }
    }

    private static Vector3f vector(Object value) {
        com.github.retrooper.packetevents.util.Vector3f v = (com.github.retrooper.packetevents.util.Vector3f) value;
        return new Vector3f(v.x, v.y, v.z);
    }
}
