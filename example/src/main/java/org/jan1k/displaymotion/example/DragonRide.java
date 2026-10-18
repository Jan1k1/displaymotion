package org.jan1k.displaymotion.example;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Pose;
import org.jan1k.displaymotion.paper.DisplayMotion;
import org.jan1k.displaymotion.paper.Follow;
import org.jan1k.displaymotion.paper.MotionDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.jan1k.displaymotion.example.Dragon.bump;
import static org.jan1k.displaymotion.example.Dragon.fire;
import static org.jan1k.displaymotion.example.Dragon.s;
import static org.jan1k.displaymotion.example.Dragon.snap;
import static org.jan1k.displaymotion.example.Dragon.turn;
import static org.jan1k.displaymotion.example.Dragon.wave;

// /dm dragon ride. you sit on its back and it flies where you look. the player and both models ride
// the same invisible seat, so the game moves them together every frame and only the seat gets moved here.
// all the turning, banking and flapping is sent as short poses every 2 ticks
final class DragonRide {

    // where you sit on the model, on the back just behind the wings. first i put it on the shoulders, then the
    // wings beat right around the third person camera and you saw nothing but wing
    static final Dragon.Vec SEAT = new Dragon.Vec(0f, 3.3f, 0.1f);
    // each pose also says where it goes for 6 more ticks, so a late one does not make it stop for a moment
    static final int AHEAD = 6;

    private static final float CRUISE = 0.55f;
    private static final float FAST = 1.15f;
    private static final float SLOW = 0.12f;
    private static final float MAX_TURN = 4.5f;
    private static final float BANK = 32f;
    private static final float BEAT_RATE = (float) (2 * Math.PI / Dragon.BEAT);
    private static final int TAKE_OFF = 16;
    private static final int STAY = 120;
    private static final int SHRINK = 20;
    private static final double CAMERA = 9.0;

    private enum Mode { START, FLY, LAND, LANDED }

    private final Dragon.Kind kind;
    private final MotionDisplay body;
    private final MotionDisplay fire;
    private final List<Bone> bodyBones;
    private final List<Bone> fireBones;
    private final Runnable onEnd;
    private final BukkitTask task;
    private Player rider;
    private Entity seat;
    private World world;
    private Mode mode = Mode.START;
    private int modeTicks;
    private int ticks;
    private boolean letGo;
    private boolean sneaking;
    private double cameraWas = Double.NaN;

    private double x;
    private double y;
    private double z;
    private float heading;
    private float turnRate;
    private float speed;
    private float climb;
    private float bodyPitch;
    private float bank;
    private float lead;
    private float look;
    private float air;
    private float fold = 1f;
    private float flap = 1f;
    private float rate = BEAT_RATE;
    private float beat;
    private float brake;
    private float crouch;
    private float roarAge = 1000f;

    private record Bone(String name, Function<Flight, Pose> pose) {
    }

    // everything a pose needs, frozen for one send. after() moves the clocks on for the poses ahead
    record Flight(float clock, float beat, float rate, float air, float fold, float flap, float bank, float pitch, float roll,
                  float yaw, float turn, float lead, float look, float brake, float crouch, float roarAge) {

        Flight after(float t) {
            return new Flight(clock + t, beat + rate * t, rate, air, fold, flap, bank, pitch, roll, yaw + turn * t, turn, lead, look, brake, crouch, roarAge + t);
        }
    }

    private record Ahead(Flight flight, Function<Flight, Pose> pose) implements Animation {

        @Override
        public Pose poseAt(float tick) {
            return pose.apply(flight.after(tick));
        }

        @Override
        public int introTicks() {
            return AHEAD;
        }

        @Override
        public int loopTicks() {
            return 0;
        }
    }

    private DragonRide(Plugin plugin, DisplayMotion motion, Player player, Dragon.Kind kind, Runnable onEnd) {
        this.kind = kind;
        this.onEnd = onEnd;
        this.rider = player;
        Location feet = player.getLocation();
        world = feet.getWorld();
        heading = feet.getYaw();
        double radians = Math.toRadians(heading);
        x = feet.getX() - Math.sin(radians) * 4;
        z = feet.getZ() + Math.cos(radians) * 4;
        y = standing(x, z, heading);
        // yaw 0 on purpose, the seat never turns. the whole turn is in the root pose around the seat
        Location at = new Location(world, x, y, z, 0f, 0f);
        List<MotionDisplay> models = Dragon.saddled(motion, at, kind);
        body = models.get(0);
        fire = models.get(1);
        bodyBones = bones(body);
        fireBones = bones(fire);
        send();
        body.seat("rider", 0, 0, 0);
        body.sit(player, "rider");
        seat = player.getVehicle();
        if (seat instanceof Display display) {
            display.setTeleportDuration(2);
        }
        camera(true);
        if (seat != null) {
            body.ride(seat, Follow.NONE);
            fire.ride(seat, Follow.NONE);
        }
        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, kind == Dragon.Kind.FIRE ? 0.8f : 1.1f);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    static DragonRide start(Plugin plugin, DisplayMotion motion, Player player, Dragon.Kind kind, Runnable onEnd) {
        return new DragonRide(plugin, motion, player, kind, onEnd);
    }

    Player rider() {
        return rider;
    }

    // sneak while it flies: it lands first and lets you off on the ground
    boolean keepOn() {
        if (letGo || rider == null) {
            return false;
        }
        if (!sneaking && mode != Mode.LAND && mode != Mode.LANDED) {
            sneaking = true;
            if (canLand()) {
                land();
            } else {
                rider.sendActionBar(Component.text("find solid ground", NamedTextColor.RED));
            }
        }
        return true;
    }

    void roar() {
        if (roarAge < 60f || mode == Mode.LANDED && rider == null) {
            return;
        }
        roarAge = 0f;
        List<Animation> flames = Dragon.breath(kind);
        for (int i = 0; i < flames.size(); i++) {
            fire.play("flame_" + i, flames.get(i));
        }
    }

    // gone right now, /dm stop and quit. someone still up in the air falls slowly down
    void remove() {
        task.cancel();
        if (rider != null && rider.isOnline() && mode != Mode.LANDED && rider.getVehicle() == seat) {
            rider.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0));
        }
        letGo = true;
        camera(false);
        body.remove();
        fire.remove();
        onEnd.run();
    }

    private void tick() {
        ticks++;
        modeTicks++;
        if (body.isRemoved() || fire.isRemoved() || seat == null || !seat.isValid()) {
            remove();
            return;
        }
        if (rider != null && (!rider.isOnline() || rider.getVehicle() != seat)) {
            camera(false);
            rider = null;
            if (mode != Mode.LANDED) {
                land();
            }
        }
        Input input = rider == null ? null : rider.getCurrentInput();
        if (input != null && input.isSneak()) {
            keepOn();
        } else {
            sneaking = false;
        }
        switch (mode) {
            case START -> start();
            case FLY -> fly(input);
            case LAND -> landing();
            case LANDED -> landed();
        }
        roarAge++;
        float wasBeat = beat;
        beat += rate;
        if (flap > 0.5f && Math.floor((wasBeat - 0.6f) / (2 * Math.PI)) != Math.floor((beat - 0.6f) / (2 * Math.PI))) {
            world.playSound(seatSpot(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.8f + 0.2f * (rate / BEAT_RATE - 1f));
        }
        if (roarAge == 3f) {
            world.playSound(seatSpot(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, kind == Dragon.Kind.FIRE ? 0.8f : 1.1f);
        } else if (roarAge == Dragon.FIRE) {
            world.playSound(seatSpot(), kind == Dragon.Kind.ICE ? Sound.BLOCK_POWDER_SNOW_STEP : Sound.ENTITY_BLAZE_SHOOT, 2f, 0.5f);
        }
        if (mode == Mode.LANDED && modeTicks >= STAY + SHRINK) {
            remove();
            return;
        }
        Location to = seatSpot();
        // bob up and down with the wings, you go with it
        to.add(0, -0.15 * Math.cos(beat - 0.5) * air * flap, 0);
        seat.teleport(to, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        if (ticks % 2 == 0) {
            send();
        }
    }

    // stands up, crouches and jumps
    private void start() {
        crouch = bump(modeTicks, 2, TAKE_OFF + 8);
        y = standing(x, z, heading) - 0.3f * crouch;
        if (modeTicks >= TAKE_OFF) {
            mode = Mode.FLY;
            modeTicks = 0;
            climb = 0.55f;
            speed = 0.2f;
        }
    }

    private void fly(Input input) {
        Location eye = rider.getLocation();
        float off = wrap(eye.getYaw() - heading);
        boolean forward = input != null && input.isForward();
        boolean back = input != null && input.isBackward();
        boolean jump = input != null && input.isJump();

        // turn speed eases in and out, a sharp look to the side still gives a soft curve
        turnRate += (Math.clamp(off * 0.09f, -MAX_TURN, MAX_TURN) - turnRate) * 0.15f;
        heading = wrap(heading + turnRate);
        float wantSpeed = forward ? FAST : back ? SLOW : CRUISE;
        speed += (wantSpeed - speed) * (wantSpeed > speed ? 0.03f : 0.06f);

        // a few degrees around level count as level, or you could never fly straight
        float pitch = eye.getPitch();
        float tilt = Math.signum(pitch) * Math.max(0f, Math.abs(pitch) - 8f);
        float wantClimb = (float) -Math.sin(Math.toRadians(tilt)) * (0.25f + speed * 0.8f) + (jump ? 0.45f : 0f);
        boolean gliding = !forward && !jump && !back && wantClimb < 0.05f;
        if (gliding) {
            wantClimb -= 0.03f;
        }
        double ahead = Math.max(ground(x, z), Math.max(ground(x - Math.sin(Math.toRadians(heading)) * 6, z + Math.cos(Math.toRadians(heading)) * 6),
                ground(x - Math.sin(Math.toRadians(heading)) * 12, z + Math.cos(Math.toRadians(heading)) * 12)));
        float floor = (float) (ahead + SEAT.y() + 1.5f);
        wantClimb = Math.max(Math.clamp(wantClimb, -0.9f, 0.6f), Math.min(0.6f, (floor - (float) y) * 0.12f));
        climb += (wantClimb - climb) * 0.1f;

        flap += ((gliding ? 0.12f : 1f) - flap) * 0.05f;
        float wantRate = gliding ? 0.55f : 1f + 0.9f * Math.max(0f, climb / 0.6f) + 0.25f * Math.max(0f, (speed - CRUISE) / (FAST - CRUISE));
        rate += (BEAT_RATE * wantRate - rate) * 0.05f;
        brake += ((back ? 0.6f : 0f) - brake) * 0.08f;
        lead += (Math.clamp(-off * 0.6f, -35f, 35f) - lead) * 0.2f;
        look += (Math.clamp(pitch * 0.3f, -15f, 20f) - look) * 0.2f;
        steer();
        move();
    }

    private void land() {
        if (mode == Mode.LAND || mode == Mode.LANDED) {
            return;
        }
        mode = Mode.LAND;
        modeTicks = 0;
    }

    private void landing() {
        if (rider != null) {
            float off = wrap(rider.getLocation().getYaw() - heading);
            turnRate += (Math.clamp(off * 0.05f, -MAX_TURN / 2, MAX_TURN / 2) - turnRate) * 0.15f;
        } else {
            turnRate *= 0.9f;
        }
        heading = wrap(heading + turnRate);
        double height = y - standing(x, z, heading);
        speed += ((height < 4 ? 0.12f : 0.35f) - speed) * 0.06f;
        climb += (Math.clamp((float) -height * 0.1f, -0.4f, -0.06f) - climb) * 0.12f;
        flap += (1f - flap) * 0.08f;
        rate += (BEAT_RATE * 1.2f - rate) * 0.05f;
        brake += ((height < 5 ? 1f : 0.3f) - brake) * 0.1f;
        lead *= 0.9f;
        look *= 0.9f;
        steer();
        move();
        double stand = standing(x, z, heading);
        if (y <= stand + 0.05) {
            y = stand;
            mode = Mode.LANDED;
            modeTicks = 0;
            world.playSound(seatSpot(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.5f, 0.5f);
        }
    }

    private void landed() {
        speed = 0f;
        climb = 0f;
        turnRate = 0f;
        crouch = bump(modeTicks, 0, 22);
        brake += (0f - brake) * 0.1f;
        flap += (0.3f - flap) * 0.1f;
        rate += (BEAT_RATE - rate) * 0.1f;
        lead *= 0.9f;
        look *= 0.9f;
        steer();
        y = standing(x, z, heading) - 0.3f * crouch;
        if (modeTicks == 14 && rider != null) {
            off();
        }
        if (modeTicks == STAY) {
            body.scale(0f, SHRINK);
            fire.scale(0f, SHRINK);
        }
    }

    // down next to its shoulder, the seat is too high to just drop off
    private void off() {
        camera(false);
        Player player = rider;
        rider = null;
        letGo = true;
        player.leaveVehicle();
        double radians = Math.toRadians(heading + 90f);
        double sx = x - Math.sin(radians) * 3.2;
        double sz = z + Math.cos(radians) * 3.2;
        Location spot = new Location(world, sx, ground(sx, sz), sz);
        spot.setDirection(new Vector(x, y - 1, z).subtract(spot.toVector().add(new Vector(0, 1.6, 0))));
        player.teleport(spot);
        player.setFallDistance(0f);
    }

    // the third person camera of 1.21.6+ can go further back, 4 blocks is inside the wings. older ones don't
    // have the attribute, so it's looked up by name
    private void camera(boolean riding) {
        Attribute distance = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("camera_distance"));
        AttributeInstance camera = distance == null || rider == null ? null : rider.getAttribute(distance);
        if (camera == null) {
            return;
        }
        if (riding && Double.isNaN(cameraWas)) {
            cameraWas = camera.getBaseValue();
            camera.setBaseValue(CAMERA);
        } else if (!riding && !Double.isNaN(cameraWas)) {
            camera.setBaseValue(cameraWas);
            cameraWas = Double.NaN;
        }
    }

    // the smoothed values everything else comes from
    private void steer() {
        boolean flying = mode == Mode.FLY || mode == Mode.LAND;
        air += ((flying ? 1f : 0f) - air) * (flying ? 0.1f : 0.15f);
        fold += ((flying ? 0f : 1f) - fold) * (flying ? 0.15f : 0.06f);
        bank += (Math.clamp(-turnRate / MAX_TURN, -1f, 1f) - bank) * 0.12f;
        bodyPitch += (Math.clamp(-climb * 45f, -28f, 32f) * air - bodyPitch) * 0.12f;
    }

    private void move() {
        double radians = Math.toRadians(heading);
        x += -Math.sin(radians) * speed;
        z += Math.cos(radians) * speed;
        y += climb;
        double floor = ground(x, z) + SEAT.y() + (mode == Mode.LAND ? 0 : 1.2 * air);
        if (y < floor) {
            y = floor;
            climb = Math.max(climb, 0f);
        }
        y = Math.min(y, world.getMaxHeight() + 20);
    }

    private Location seatSpot() {
        return new Location(world, x, y, z, 0f, 0f);
    }

    // seat height when it stands here, the feet on the highest of the ground under its chest and hips
    private double standing(double atX, double atZ, float yaw) {
        double radians = Math.toRadians(yaw);
        double hipsX = atX + Math.sin(radians) * 1.4;
        double hipsZ = atZ - Math.cos(radians) * 1.4;
        return Math.max(ground(atX, atZ), ground(hipsX, hipsZ)) + SEAT.y();
    }

    private double ground(double atX, double atZ) {
        return world.getHighestBlockYAt((int) Math.floor(atX), (int) Math.floor(atZ), HeightMap.MOTION_BLOCKING) + 1;
    }

    private boolean canLand() {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        for (int by = (int) Math.floor(y); by >= world.getMinHeight(); by--) {
            Block block = world.getBlockAt(bx, by, bz);
            Material type = block.getType();
            if (unlandable(type, block.isLiquid())) {
                return false;
            }
            if (solidGround(type, block.isSolid(), block.isLiquid())) {
                return true;
            }
        }
        return false;
    }

    static boolean unlandable(Material type, boolean liquid) {
        return liquid || type == Material.WATER || type == Material.LAVA || type.name().endsWith("_LEAVES");
    }

    static boolean solidGround(Material type, boolean solid, boolean liquid) {
        return solid && !unlandable(type, liquid) && type != Material.AIR && !type.name().endsWith("_AIR");
    }

    private Flight flight() {
        return new Flight(ticks, beat, rate, air, fold, flap, bank, bodyPitch, -BANK * bank * air, -heading, -turnRate, lead, look, brake, crouch, roarAge);
    }

    private void send() {
        Flight now = flight();
        Animation root = new Ahead(now, DragonRide::root);
        body.play(root);
        fire.play(root);
        for (Bone bone : bodyBones) {
            body.play(bone.name(), new Ahead(now, bone.pose()));
        }
        for (Bone bone : fireBones) {
            fire.play(bone.name(), new Ahead(now, bone.pose()));
        }
    }

    private static List<Bone> bones(MotionDisplay display) {
        List<Bone> bones = new ArrayList<>();
        for (Dragon.Piece piece : Dragon.design(Dragon.Kind.FIRE, false, 0, 1).pieces()) {
            if (piece.isBone() && display.parts().contains(piece.name())) {
                bones.add(new Bone(piece.name(), pose(piece.name())));
            }
        }
        return bones;
    }

    // turns the whole dragon around the seat, so the seat point itself never moves
    static Pose root(Flight f) {
        Dragon.Vec at = SEAT.turned(f.pitch(), f.yaw(), f.roll()).times(-1f);
        return Pose.IDENTITY.withOffset(at.x(), at.y(), at.z()).withRotation(f.pitch(), f.yaw(), f.roll());
    }

    static Function<Flight, Pose> pose(String bone) {
        int s = bone.endsWith("_right") ? -1 : 1;
        if (bone.equals("hips")) {
            return DragonRide::hips;
        } else if (bone.equals("chest")) {
            return DragonRide::chest;
        } else if (bone.equals("head")) {
            return DragonRide::head;
        } else if (bone.equals("jaw")) {
            return DragonRide::jaw;
        } else if (bone.equals("tail_tip")) {
            return f -> tail(f, 9);
        } else if (bone.startsWith("neck_")) {
            int k = Integer.parseInt(bone.substring(5));
            return f -> neck(f, k);
        } else if (bone.startsWith("tail_")) {
            int k = Integer.parseInt(bone.substring(5));
            return f -> tail(f, k);
        } else if (bone.startsWith("thigh")) {
            return f -> leg(f, 1);
        } else if (bone.startsWith("shin")) {
            return f -> leg(f, 2);
        } else if (bone.startsWith("foot")) {
            return f -> leg(f, 3);
        } else if (bone.startsWith("shoulder")) {
            return f -> shoulder(f, s);
        } else if (bone.startsWith("elbow")) {
            return f -> elbow(f, s);
        } else if (bone.startsWith("wrist")) {
            return f -> wrist(f, s);
        } else if (bone.startsWith("finger_")) {
            int i = bone.charAt(7) - '0';
            return f -> finger(f, s, i);
        }
        throw new IllegalArgumentException("no ride pose for bone " + bone);
    }

    // same moves as in its life, only driven by the flight and not by the tick of a loop. the neck stays up
    // more and the fire sweeps less, so you see both from its back

    private static float flapping(Flight f) {
        return f.air() * f.flap();
    }

    private static Pose hips(Flight f) {
        return turn(6f * f.crouch() - 14f * f.brake() + 2.5f * (float) Math.sin(f.beat() - 1f) * flapping(f), 0f, 0f);
    }

    private static Pose chest(Flight f) {
        float breathe = 1.5f * (float) Math.sin(f.clock() / 47.5f * 2 * Math.PI) * (1f - f.air());
        return turn(breathe + 2f * (float) Math.sin(f.beat() - 0.8f) * flapping(f), 0f, 0f);
    }

    private static Pose neck(Flight f, int k) {
        float c = f.roarAge();
        float lag = k * 1.5f;
        float pitch = -6f * s(c, 4 + lag, 12 + lag) * (1f - s(c, 14, 20))
                + 3f * snap(c, 14 + lag * 0.5f, 21 + lag * 0.5f) * (1f - s(c, 50, 62))
                + (6f + 3f * (float) Math.sin(f.beat() - 0.6f - 0.35f * k) * f.flap()) * f.air() + f.look() * 0.08f;
        float yaw = 2f * (float) Math.sin((c - Dragon.FIRE) / (Dragon.FIRE_END - Dragon.FIRE) * 2 * Math.PI) * fire(c)
                + f.lead() * 0.11f + 4f * (float) Math.sin(f.beat() / 2 - 0.3f * k) * flapping(f)
                + 9f * (float) Math.sin(f.clock() / 90f * 2 * Math.PI - 0.3f * k) * (1f - f.air());
        return turn(pitch, yaw, 0f);
    }

    private static Pose head(Flight f) {
        float c = f.roarAge();
        float pitch = -12f * s(c, 4, 14) * (1f - s(c, 14, 20)) + 8f * snap(c, 16, 22) * (1f - s(c, 50, 62))
                + 1.5f * wave(c, 6) * fire(c) + (-26f + 3f * (float) Math.sin(f.beat() - 1.6f) * f.flap()) * f.air() + f.look() * 0.6f;
        return turn(pitch, f.lead() * 0.45f, 0f);
    }

    private static Pose jaw(Flight f) {
        float c = f.roarAge();
        return turn(38f * snap(c, 15, 21) * (1f - s(c, 48, 58)) + (5f + 3f * (float) Math.sin(f.beat() - 2f)) * flapping(f), 0f, 0f);
    }

    // the tail hangs a bit in flight, curled up it was right between the camera and you
    private static Pose tail(Flight f, int k) {
        float pitch = (k < 3 ? 4f : 0f) * Dragon.roar(f.roarAge()) + (-3f + (1.5f + 0.4f * k) * (float) Math.sin(f.beat() - 1.2f - 0.45f * k) * f.flap()) * f.air();
        float yaw = (2.5f + 1.4f * k) * (float) Math.sin(f.clock() / 76f * 2 * Math.PI - 0.55f * k) * (1f - f.air())
                + (2f + 1.3f * k) * (float) Math.sin(f.beat() / 2 - 0.5f * k) * flapping(f) + (2.5f + 0.9f * k) * f.bank();
        return turn(pitch, yaw, 0f);
    }

    private static Pose leg(Flight f, int part) {
        float[] crouch = {-22f, 40f, -18f};
        float[] tucked = {62f, -25f, 45f};
        float sway = 5f * (float) Math.sin(f.beat() - part * 0.5f);
        return turn(crouch[part - 1] * f.crouch() + (tucked[part - 1] + sway) * f.air(), 0f, 0f);
    }

    // gliding holds the wings flat with the tips a bit up, flapping swings them from there
    private static Pose shoulder(Flight f, int s) {
        float r = Dragon.roar(f.roarAge());
        float roll = -35f * f.fold() + 12f * r + 15f * f.brake() + 1.2f * wave(f.roarAge(), 6) * fire(f.roarAge())
                + (2f + 30f * (float) Math.cos(f.beat())) * flapping(f) + 5f * (1f - f.flap()) * f.air();
        float yaw = 45f * f.fold() - 14f * r - 25f * f.brake() + 8f * (float) Math.sin(f.beat()) * flapping(f);
        return turn(0f, s * yaw, s * roll);
    }

    private static Pose elbow(Flight f, int s) {
        float yaw = 45f * f.fold() - 8f * Dragon.roar(f.roarAge()) - 10f * f.brake() + (10f - 14f * (float) Math.sin(f.beat() - 0.7f) * f.flap()) * f.air();
        return turn(0f, s * yaw, s * 10f * (float) Math.cos(f.beat() - 0.7f) * flapping(f));
    }

    private static Pose wrist(Flight f, int s) {
        return turn(0f, s * 25f * f.fold(), s * 14f * (float) Math.cos(f.beat() - 1.2f) * flapping(f));
    }

    private static Pose finger(Flight f, int s, int i) {
        float spread = (i - 1.5f) * 5f * Math.min(1f, Dragon.roar(f.roarAge()) + f.air());
        float yaw = (4f + 4f * i) * f.fold() - spread;
        return turn(0f, s * yaw, s * 10f * (float) Math.cos(f.beat() - 1.6f - 0.25f * i) * flapping(f));
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        return d > 180f ? d - 360f : d < -180f ? d + 360f : d;
    }
}
