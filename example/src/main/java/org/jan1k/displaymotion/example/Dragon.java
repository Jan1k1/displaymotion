package org.jan1k.displaymotion.example;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Pose;
import org.jan1k.displaymotion.paper.DisplayMotion;
import org.jan1k.displaymotion.paper.ModelBuilder;
import org.jan1k.displaymotion.paper.MotionDisplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

// a dragon out of about 240 block displays and 140 more for its fire. it roars, takes off, flies a circle,
// lands and looks around, then does it all again. with build it first blows out of a glowing ball, swirls
// around like a tornado and snaps together piece by piece. its all the normal public api, nothing special
final class Dragon {

    // build mode, before its life starts
    static final int CHARGE = 6;
    static final int BURST = 18;
    static final int FIRST_LOCK = 34;
    static final int LAST_LOCK = 120;
    static final int FLY = 16;
    static final int WAKE = 132;
    static final int AWAKE = 176;

    // one round of its life, it loops
    static final int CYCLE = 380;
    static final int FIRE = 18;
    static final int FIRE_END = 52;
    static final int LEAP = 68;
    static final int CIRCLE = 96;
    static final int CIRCLE_END = 256;
    static final int TOUCH = 290;
    static final int BEAT = 34;
    static final float RADIUS = 10f;
    static final float HEIGHT = 9f;

    private static final Pose HIDDEN = Pose.IDENTITY.withScale(0f);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    private static final Map<String, Design> DESIGNS = new ConcurrentHashMap<>();

    enum Kind {
        FIRE(Material.POLISHED_BLACKSTONE, Material.NETHERITE_BLOCK, Material.GOLD_BLOCK, Material.WAXED_CUT_COPPER,
                Material.BLACK_STAINED_GLASS, Material.MAGMA_BLOCK, Material.RED_CONCRETE, Material.SHROOMLIGHT,
                Color.fromRGB(255, 170, 0),
                Material.YELLOW_CONCRETE, Material.YELLOW_CONCRETE, Material.SHROOMLIGHT, Material.ORANGE_CONCRETE, Material.SHROOMLIGHT,
                Material.ORANGE_CONCRETE, Material.ORANGE_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.RED_CONCRETE, Material.GRAY_STAINED_GLASS),
        ICE(Material.BLUE_ICE, Material.PACKED_ICE, Material.QUARTZ_BLOCK, Material.SNOW_BLOCK,
                Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.CYAN_CONCRETE, Material.SEA_LANTERN,
                Color.fromRGB(120, 230, 255),
                Material.WHITE_CONCRETE, Material.WHITE_CONCRETE, Material.SNOW_BLOCK, Material.LIGHT_BLUE_CONCRETE, Material.PACKED_ICE,
                Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS, Material.WHITE_STAINED_GLASS, Material.CYAN_STAINED_GLASS, Material.WHITE_STAINED_GLASS),
        ENDER(Material.OBSIDIAN, Material.PURPUR_BLOCK, Material.AMETHYST_BLOCK, Material.END_STONE_BRICKS,
                Material.BLACK_STAINED_GLASS, Material.CRYING_OBSIDIAN, Material.MAGENTA_CONCRETE, Material.PEARLESCENT_FROGLIGHT,
                Color.fromRGB(200, 90, 255),
                Material.PINK_CONCRETE, Material.MAGENTA_CONCRETE, Material.PEARLESCENT_FROGLIGHT, Material.MAGENTA_CONCRETE, Material.PURPLE_CONCRETE,
                Material.PEARLESCENT_FROGLIGHT, Material.MAGENTA_STAINED_GLASS, Material.PURPLE_STAINED_GLASS, Material.PURPLE_CONCRETE, Material.BLACK_STAINED_GLASS);

        final Material dark;
        final Material armor;
        final Material trim;
        final Material belly;
        final Material skin;
        final Material skinLit;
        final Material eye;
        final Material hot;
        final Color glow;
        // hot to cold, the first ones stay close to the mouth
        final Material[] flames;

        Kind(Material dark, Material armor, Material trim, Material belly, Material skin, Material skinLit, Material eye,
             Material hot, Color glow, Material... flames) {
            this.dark = dark;
            this.armor = armor;
            this.trim = trim;
            this.belly = belly;
            this.skin = skin;
            this.skinLit = skinLit;
            this.eye = eye;
            this.hot = hot;
            this.glow = glow;
            this.flames = flames;
        }
    }

    // lit is the block it swaps to when it wakes up, bright ones glow from then on. model 1 is the fire,
    // it got it's own model because the 256 part limit is per model
    record Piece(String name, String parent, Material block, Material lit, boolean bright, Pose pose, Animation animation, int model) {

        boolean isBone() {
            return block == null;
        }
    }

    record Design(List<Piece> pieces, Animation root) {

        // the bones from the hips to the head go into the fire model too, so the fire moves with the head for free, no seperate math
        List<Piece> model(int model) {
            Map<String, Piece> byName = new HashMap<>();
            for (Piece piece : pieces) {
                byName.put(piece.name(), piece);
            }
            Set<String> needed = new HashSet<>();
            for (Piece piece : pieces) {
                String name = piece.model() == model ? piece.name() : null;
                while (name != null && needed.add(name)) {
                    name = byName.get(name).parent();
                }
            }
            return pieces.stream().filter(piece -> needed.contains(piece.name())).toList();
        }
    }

    // build: the tornado intro first. offset: ticks into its life, so a few dragons dont all move the same.
    // side: 1 flies the circle to its left, -1 to its right
    static Design design(Kind kind, boolean build, int offset, int side) {
        return DESIGNS.computeIfAbsent(kind + " " + build + " " + offset + " " + side, key -> new Build(kind, build, offset, side).design());
    }

    private Dragon() {
    }

    static List<MotionDisplay> spawn(DisplayMotion motion, Location at, Kind kind, boolean build, int offset, int side, float size) {
        Design design = design(kind, build, offset, side);
        List<MotionDisplay> models = new ArrayList<>();
        for (int model = 0; model < 2; model++) {
            List<Piece> pieces = design.model(model);
            ModelBuilder builder = motion.model(at);
            for (Piece piece : pieces) {
                if (piece.isBone()) {
                    builder.group(piece.name(), piece.pose(), piece.parent());
                } else {
                    Material block = !build && piece.lit() != null ? piece.lit() : piece.block();
                    builder.block(piece.name(), block.createBlockData(), piece.pose(), piece.parent());
                }
            }
            MotionDisplay display = builder.spawn().play(design.root());
            if (size != 1f) {
                display.scale(size);
            }
            for (Piece piece : pieces) {
                if (piece.animation() != null) {
                    display.play(piece.name(), piece.animation());
                }
            }
            models.add(display);
        }

        MotionDisplay body = models.get(0);
        models.get(1).brightness(LIT);
        body.shadow("pelvis", 3f * size, 0.8f);
        if (build) {
            // every piece glows while it flies, the lock event turns it off again when it lands
            body.glow(kind.glow);
        } else {
            light(body, design);
        }
        int[] locks = {0};
        body.onEvent(event -> {
            switch (event.name()) {
                case "lock" -> {
                    body.glow(event.part(), null);
                    if (locks[0]++ % 3 == 0) {
                        at.getWorld().playSound(at, Sound.BLOCK_NETHERITE_BLOCK_PLACE, 0.8f, 0.6f + (locks[0] % 7) * 0.1f);
                    }
                }
                case "burst" -> at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
                case "wake" -> {
                    for (Piece piece : design.pieces()) {
                        if (piece.lit() != null) {
                            body.block(piece.name(), piece.lit().createBlockData());
                        }
                    }
                    light(body, design);
                    at.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.6f);
                }
                case "roar" -> at.getWorld().playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, kind == Kind.FIRE ? 0.8f : 1.1f);
                case "fire" -> at.getWorld().playSound(at, kind == Kind.ICE ? Sound.BLOCK_POWDER_SNOW_STEP : Sound.ENTITY_BLAZE_SHOOT, 2f, 0.5f);
                case "beat" -> at.getWorld().playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.8f);
                case "land" -> at.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.5f, 0.5f);
                default -> {
                }
            }
        });
        return models;
    }

    private static void light(MotionDisplay body, Design design) {
        for (Piece piece : design.pieces()) {
            if (piece.bright()) {
                body.brightness(piece.name(), LIT);
            }
        }
    }

    private static void skeleton(Build b) {
        b.bone("hips", null, 0f, 2.7f, -0.5f, c -> hips(c, b.side));
        b.bone("chest", "hips", 0f, 0.35f, 1.5f, Dragon::chest);
        String parent = "chest";
        for (int k = 0; k < 5; k++) {
            int segment = k;
            b.bone("neck_" + k, parent, NECK[k][0], NECK[k][1], NECK[k][2], c -> neck(c, segment));
            parent = "neck_" + k;
        }
        b.bone("head", parent, NECK[5][0], NECK[5][1], NECK[5][2], Dragon::head);
        b.bone("jaw", "head", 0f, -0.14f, -0.05f, Dragon::jaw);
        parent = "hips";
        for (int k = 0; k < 9; k++) {
            int segment = k;
            b.bone("tail_" + k, parent, TAIL[k][0], TAIL[k][1], TAIL[k][2], c -> tail(c, segment, b.side));
            parent = "tail_" + k;
        }
        b.bone("tail_tip", parent, TAIL[9][0], TAIL[9][1], TAIL[9][2], c -> tail(c, 9, b.side));
        for (int side = -1; side <= 1; side += 2) {
            int s = side;
            String n = side < 0 ? "_right" : "_left";
            b.bone("thigh" + n, "hips", s * 0.72f, -0.1f, 0.15f, c -> leg(c, 1));
            b.bone("shin" + n, "thigh" + n, s * 0.1f, -1.0f, 0.6f, c -> leg(c, 2));
            b.bone("foot" + n, "shin" + n, 0f, -0.95f, -0.55f, c -> leg(c, 3));
            b.bone("shoulder" + n, "chest", s * 0.62f, 0.42f, 0.15f, c -> shoulder(c, s));
            b.bone("elbow" + n, "shoulder" + n, s * 1.55f, 1.35f, -0.55f, c -> elbow(c, s));
            b.bone("wrist" + n, "elbow" + n, s * 2.2f, 0.45f, -0.45f, c -> wrist(c, s));
            for (int i = 0; i < 4; i++) {
                int finger = i;
                b.bone("finger_" + i + n, "wrist" + n, 0f, 0f, 0f, c -> finger(c, s, finger));
            }
        }
    }

    private static final float[][] NECK = {{0f, 0.45f, 0.6f}, {0f, 0.45f, 0.4f}, {0f, 0.42f, 0.3f}, {0f, 0.38f, 0.28f}, {0f, 0.32f, 0.28f}, {0f, 0.22f, 0.3f}};
    private static final float[][] TAIL = {{0f, -0.05f, -0.75f}, {0f, -0.3f, -0.55f}, {0f, -0.35f, -0.55f}, {0f, -0.35f, -0.55f}, {0f, -0.3f, -0.55f},
            {0f, -0.2f, -0.5f}, {0f, -0.1f, -0.5f}, {0f, 0f, -0.5f}, {0f, 0.08f, -0.45f}, {0f, 0.12f, -0.4f}};

    private static void leg(Build b, int s) {
        Kind p = b.kind;
        String n = s < 0 ? "_right" : "_left";
        Vec knee = new Vec(s * 0.1f, -1.0f, 0.6f);
        Vec ankle = new Vec(0f, -0.95f, -0.55f);
        b.limb("thigh_armor" + n, "thigh" + n, p.armor, Vec.ZERO, knee, 0.5f, 0.62f, new Vec(0f, 0.5f, 1f));
        b.limb("thigh_plate" + n, "thigh" + n, p.trim, new Vec(s * 0.3f, -0.15f, 0.12f), new Vec(s * 0.36f, -0.75f, 0.45f), 0.06f, 0.36f, new Vec(1f, 0f, 0f));
        b.box("knee" + n, "shin" + n, p.trim, 0f, 0.02f, 0.12f, 0.3f, 0.3f, 0.22f);
        b.limb("shin_bar" + n, "shin" + n, p.dark, Vec.ZERO, ankle, 0.3f, 0.3f, null);
        b.limb("spur" + n, "shin" + n, p.trim, new Vec(0f, -0.5f, -0.4f), new Vec(0f, -0.35f, -0.7f), 0.06f, 0.06f, null);
        b.limb("heel" + n, "foot" + n, p.dark, Vec.ZERO, new Vec(0f, -0.48f, 0.12f), 0.26f, 0.26f, null);
        for (int toe = -1; toe <= 1; toe++) {
            Vec base = new Vec(0f, -0.5f, 0.12f);
            Vec tip = new Vec(toe * 0.24f, -0.58f, 0.72f - Math.abs(toe) * 0.1f);
            b.limb("toe_" + (toe + 1) + n, "foot" + n, p.dark, base, tip, 0.13f, 0.12f, null);
            b.limb("claw_" + (toe + 1) + n, "foot" + n, p.trim, tip, tip.plus(new Vec(toe * 0.06f, -0.08f, 0.2f)), 0.07f, 0.07f, null);
        }
        b.limb("dewclaw" + n, "foot" + n, p.trim, new Vec(0f, -0.45f, 0.05f), new Vec(0f, -0.6f, -0.25f), 0.07f, 0.07f, null);
    }

    private static void hips(Build b) {
        Kind p = b.kind;
        b.box("pelvis", "hips", p.dark, 0f, 0f, 0f, 1.15f, 0.8f, 1.2f);
        b.box("waist", "hips", p.dark, 0f, 0.14f, 0.78f, 1.05f, 0.9f, 0.95f);
        for (int side = -1; side <= 1; side += 2) {
            String n = side < 0 ? "_right" : "_left";
            b.box("haunch" + n, "hips", p.armor, side * 0.6f, 0.02f, 0.05f, 0.12f, 0.86f, 1.1f, 0f, 0f, side * -6f);
            b.box("flank" + n, "hips", p.armor, side * 0.55f, 0.15f, 0.8f, 0.1f, 0.8f, 0.8f, 0f, side * 4f, side * -4f);
        }
        b.box("belly_rear", "hips", p.belly, 0f, -0.42f, 0.45f, 0.85f, 0.1f, 1.3f);
        b.fin("spine_hips_0", "hips", new Vec(0f, 0.4f, -0.3f), 0.34f, 0.3f);
        b.fin("spine_hips_1", "hips", new Vec(0f, 0.6f, 0.45f), 0.36f, 0.3f);
    }

    private static void tail(Build b) {
        Kind p = b.kind;
        for (int k = 0; k < 9; k++) {
            Vec next = new Vec(TAIL[k + 1][0], TAIL[k + 1][1], TAIL[k + 1][2]);
            float width = 0.72f - k * 0.06f;
            Vec dorsal = Vec.UP.minus(next.unit().times(Vec.UP.dot(next.unit()))).unit();
            String bone = "tail_" + k;
            b.limb("tail_core_" + k, bone, k % 2 == 0 ? p.dark : p.armor, Vec.ZERO, next.times(1.08f), width, width * 0.9f, dorsal);
            b.limb("tail_belly_" + k, bone, p.belly, dorsal.times(-width * 0.45f).plus(next.times(0.1f)),
                    dorsal.times(-width * 0.45f).plus(next.times(0.9f)), width * 0.75f, 0.06f, dorsal);
            b.fin("tail_spike_" + k, bone, dorsal.times(width * 0.45f + 0.08f).plus(next.times(0.5f)), 0.32f - k * 0.022f, 0.26f - k * 0.012f);
        }
        b.box("blade", "tail_tip", p.trim, 0f, 0f, -0.3f, 0.6f, 0.06f, 0.6f, 0f, 45f, 0f);
        b.box("blade_fin", "tail_tip", p.trim, 0f, 0.14f, -0.12f, 0.06f, 0.36f, 0.5f, -30f, 0f, 0f);
        b.box("blade_core", "tail_tip", p.armor, 0f, 0f, -0.05f, 0.24f, 0.2f, 0.3f);
    }

    private static void chest(Build b) {
        Kind p = b.kind;
        b.box("spine_plate", "chest", p.armor, 0f, 0.58f, 0f, 1.0f, 0.16f, 1.3f);
        b.box("sternum", "chest", p.belly, 0f, -0.58f, 0.1f, 0.85f, 0.14f, 1.2f);
        b.box("keel", "chest", p.trim, 0f, -0.68f, 0.15f, 0.12f, 0.08f, 1.0f);
        b.box("collar", "chest", p.armor, 0f, 0.28f, 0.66f, 0.9f, 0.55f, 0.12f, -15f, 0f, 0f);
        b.box("back_plate", "chest", p.dark, 0f, 0.05f, -0.6f, 1.1f, 1.05f, 0.14f);
        for (int side = -1; side <= 1; side += 2) {
            String n = side < 0 ? "_right" : "_left";
            for (int k = 0; k < 4; k++) {
                b.box("rib_" + k + n, "chest", p.armor, side * 0.64f, 0f, -0.42f + k * 0.29f, 0.1f, 1.15f, 0.13f, 0f, 0f, side * -7f);
            }
            b.box("shoulder_plate" + n, "chest", p.trim, side * 0.6f, 0.45f, 0.18f, 0.32f, 0.08f, 0.5f, 0f, 0f, side * -25f);
        }
        b.box("chest_core", "chest", p.dark, 0f, -0.02f, 0f, 1.1f, 0.95f, 1.05f);
        for (int k = 0; k < 3; k++) {
            b.fin("spine_chest_" + k, "chest", new Vec(0f, 0.66f, -0.42f + k * 0.45f), 0.42f, 0.32f);
        }
    }

    private static void neck(Build b) {
        Kind p = b.kind;
        for (int k = 0; k < 5; k++) {
            Vec next = new Vec(NECK[k + 1][0], NECK[k + 1][1], NECK[k + 1][2]);
            Vec dorsal = Vec.UP.minus(next.unit().times(Vec.UP.dot(next.unit()))).unit();
            float width = 0.6f - k * 0.035f;
            String bone = "neck_" + k;
            b.limb("neck_core_" + k, bone, k % 2 == 0 ? p.armor : p.dark, next.times(-0.05f), next.times(1.05f), width, width * 0.85f, dorsal);
            b.limb("throat_" + k, bone, p.belly, dorsal.times(-width * 0.42f), dorsal.times(-width * 0.42f).plus(next.times(0.95f)), width * 0.7f, 0.07f, dorsal);
            b.fin("neck_spike_" + k, bone, dorsal.times(width * 0.42f + 0.06f).plus(next.times(0.4f)), 0.36f - k * 0.03f, 0.28f);
        }
    }

    private static void wing(Build b, int s) {
        Kind p = b.kind;
        String n = s < 0 ? "_right" : "_left";
        Vec elbow = new Vec(s * 1.55f, 1.35f, -0.55f);
        Vec wrist = new Vec(s * 2.2f, 0.45f, -0.45f);
        b.box("shoulder_knob" + n, "shoulder" + n, p.trim, 0f, 0f, 0f, 0.34f, 0.34f, 0.34f, 0f, 45f, 0f);
        b.limb("humerus" + n, "shoulder" + n, p.armor, Vec.ZERO, elbow, 0.26f, 0.26f, null);
        b.box("elbow_knob" + n, "elbow" + n, p.trim, 0f, 0f, 0f, 0.26f, 0.26f, 0.26f, 0f, 45f, 0f);
        b.limb("elbow_spike" + n, "elbow" + n, p.trim, Vec.ZERO, new Vec(s * 0.12f, 0.5f, -0.35f), 0.07f, 0.07f, null);
        b.limb("forearm" + n, "elbow" + n, p.dark, Vec.ZERO, wrist, 0.19f, 0.19f, null);
        b.box("wrist_knob" + n, "wrist" + n, p.trim, 0f, 0f, 0f, 0.22f, 0.22f, 0.22f, 0f, 45f, 0f);
        b.limb("thumb" + n, "wrist" + n, p.trim, Vec.ZERO, new Vec(s * 0.12f, 0.32f, 0.38f), 0.08f, 0.08f, null);

        Vec[] tips = {new Vec(s * 2.4f, 1.9f, -0.3f), new Vec(s * 3.1f, 0.3f, -0.9f), new Vec(s * 2.6f, -1.3f, -1.4f), new Vec(s * 1.5f, -2.3f, -1.5f)};
        Vec normal = tips[0].cross(tips[2]).unit();
        for (int i = 0; i < 4; i++) {
            String bone = "finger_" + i + n;
            b.limb("finger_bar_" + i + n, bone, p.armor, Vec.ZERO, tips[i], 0.1f, 0.1f, normal);
            b.limb("talon_" + i + n, bone, p.trim, tips[i], tips[i].plus(tips[i].unit().times(0.28f)), 0.06f, 0.06f, normal);
            if (i < 3) {
                // a fan of thin slats between two fingers, wide at the edge like a bat wing
                for (int j = 1; j <= 3; j++) {
                    float k = j / 4f;
                    Vec dir = tips[i].times(1 - k).plus(tips[i + 1].times(k));
                    float gap = tips[i].minus(tips[i + 1]).length();
                    b.limb("skin_" + i + "_" + j + n, bone, p.skin, dir.unit().times(0.15f), dir.times(0.93f), gap * 0.42f, 0.03f, normal)
                            .lit(p.skinLit, true);
                }
            }
        }
        // the inner skin hangs from the arm down to a line from the hip to the last finger, strips like a curtain
        Vec hip = new Vec(s * 0.15f, -0.75f, -2.1f);
        Vec end = elbow.plus(wrist).plus(tips[3]).times(0.92f);
        for (int j = 0; j < 7; j++) {
            float k = (j + 0.5f) / 7f;
            Vec arm = k < 0.45f ? elbow.times(k / 0.45f) : elbow.plus(wrist.times((k - 0.45f) / 0.55f));
            Vec edge = hip.lerp(end, k).plus(new Vec(0f, -0.25f * (float) Math.sin(k * Math.PI), 0f));
            Vec across = k < 0.45f ? elbow : wrist;
            String bone = k < 0.45f ? "shoulder" + n : "elbow" + n;
            Vec base = k < 0.45f ? Vec.ZERO : elbow;
            b.limb("skin_arm_" + j + n, bone, p.skin, arm.minus(base), edge.minus(base), end.minus(hip).length() / 7f * 1.35f, 0.03f,
                    across.cross(edge.minus(arm))).lit(p.skinLit, true);
        }
    }

    private static void head(Build b) {
        Kind p = b.kind;
        b.box("skull", "head", p.armor, 0f, 0.05f, 0.08f, 0.72f, 0.5f, 0.75f);
        b.box("crown", "head", p.dark, 0f, 0.32f, -0.05f, 0.6f, 0.12f, 0.6f, -10f, 0f, 0f);
        b.box("snout", "head", p.dark, 0f, -0.02f, 0.72f, 0.48f, 0.32f, 0.65f);
        b.box("ridge", "head", p.trim, 0f, 0.16f, 0.66f, 0.12f, 0.06f, 0.62f, -4f, 0f, 0f);
        b.box("nose", "head", p.armor, 0f, 0f, 1.08f, 0.42f, 0.28f, 0.14f);
        // only seen when the jaw opens
        b.box("mouth", "head", Material.BLACK_CONCRETE, 0f, -0.2f, 0.6f, 0.36f, 0.06f, 0.75f).lit(p.hot, true);
        for (int side = -1; side <= 1; side += 2) {
            String n = side < 0 ? "_right" : "_left";
            b.box("brow" + n, "head", p.trim, side * 0.3f, 0.23f, 0.38f, 0.16f, 0.1f, 0.44f, 0f, side * 15f, side * -20f);
            b.box("eye" + n, "head", Material.BLACK_CONCRETE, side * 0.34f, 0.1f, 0.32f, 0.06f, 0.1f, 0.2f, 0f, side * 8f, 0f).lit(p.eye, false);
            b.box("nostril" + n, "head", Material.BLACK_CONCRETE, side * 0.12f, 0.08f, 1.15f, 0.07f, 0.06f, 0.04f);
            Vec[] horn = {new Vec(side * 0.25f, 0.3f, -0.1f), new Vec(side * 0.38f, 0.56f, -0.5f), new Vec(side * 0.46f, 0.7f, -0.95f), new Vec(side * 0.44f, 0.64f, -1.35f)};
            for (int k = 0; k < 3; k++) {
                b.limb("horn_" + k + n, "head", k == 0 ? p.armor : p.trim, horn[k], horn[k + 1], 0.15f - k * 0.04f, 0.15f - k * 0.04f, null);
            }
            b.limb("cheek_spike" + n, "head", p.trim, new Vec(side * 0.34f, -0.06f, -0.1f), new Vec(side * 0.58f, -0.08f, -0.5f), 0.06f, 0.06f, null);
            b.limb("cheek_spike_low" + n, "head", p.armor, new Vec(side * 0.3f, -0.16f, -0.05f), new Vec(side * 0.46f, -0.28f, -0.4f), 0.05f, 0.05f, null);
            for (int j = 0; j < 3; j++) {
                b.box("tooth_" + j + n, "head", Material.QUARTZ_BLOCK, side * 0.2f, -0.22f, 0.55f + j * 0.2f, 0.05f, 0.12f, 0.05f);
                b.box("fang_" + j + n, "jaw", Material.QUARTZ_BLOCK, side * 0.17f, -0.02f, 0.45f + j * 0.22f, 0.05f, 0.1f, 0.05f);
            }
            b.limb("jaw_spike" + n, "jaw", p.trim, new Vec(side * 0.22f, -0.12f, 0.1f), new Vec(side * 0.34f, -0.2f, -0.25f), 0.05f, 0.05f, null);
        }
        b.box("jaw_plate", "jaw", p.dark, 0f, -0.12f, 0.55f, 0.44f, 0.14f, 0.95f);
        b.box("chin", "jaw", p.trim, 0f, -0.2f, 0.6f, 0.2f, 0.04f, 0.7f);
    }

    // the breath is a stream of small glowing blocks. the first kinds die close to the mouth and the last
    // ones go furthest, so it looks hot in the middle without ever changing a block
    private static Function<Float, Pose> flame(Kind kind, int i) {
        int type = i % kind.flames.length;
        float heat = type / (kind.flames.length - 1f);
        float reach = 2.6f + 5.4f * heat;
        float life = 9f + 9f * heat;
        float grow = 0.35f + 0.55f * heat;
        float shift = noise(i, 0);
        float spin = 8f + noise(i, 1) * 14f;
        boolean smoke = type == kind.flames.length - 1;
        return c -> {
            float since = c - FIRE - shift * life;
            if (since < 0) {
                return HIDDEN;
            }
            int round = (int) (since / life);
            if (FIRE + shift * life + round * life > FIRE_END) {
                return HIDDEN;
            }
            float u = since / life - round;
            float sideways = (noise(i, 2 + round * 2) - 0.5f) * 2.2f;
            float lift = (noise(i, 3 + round * 2) - 0.5f) * 1.4f;
            float dist = 0.1f + reach * (1f - (1f - u) * (1f - u));
            float x = sideways * u * 1.1f + (float) Math.sin(u * 9f + i) * 0.1f;
            float y = -0.15f + lift * u + (smoke ? 1.8f : 0.6f) * u * u;
            float size = (0.12f + grow * u) * Math.min(1f, u / 0.1f) * Math.min(1f, (1f - u) / 0.3f);
            float angle = spin * since;
            return Pose.IDENTITY.withOffset(x, y, 1.1f + dist).withRotation(angle, angle * 0.7f, angle * 0.4f).withScale(size);
        };
    }

    // everything below works on c, thats the tick inside one round of its life

    // it flies a circle from where it stands back to the same spot
    private static Pose root(float c, int side) {
        double turn = 2 * Math.PI * s(c, CIRCLE, CIRCLE_END);
        float x = side * RADIUS * (1f - (float) Math.cos(turn));
        float z = RADIUS * (float) Math.sin(turn);
        float y = -0.3f * crouch(c) + HEIGHT * s(c, LEAP, CIRCLE + 24) * (1f - s(c, CIRCLE_END - 18, TOUCH))
                - 0.35f * (float) Math.cos(beat(c) - 0.5f) * air(c) * flap(c);
        return Pose.IDENTITY.withOffset(x, y, z).withRotation(0f, side * (float) Math.toDegrees(turn), 0f);
    }

    private static Pose hips(float c, int side) {
        float climb = (HEIGHT * s(c + 1, LEAP, CIRCLE + 24) - HEIGHT * s(c - 1, LEAP, CIRCLE + 24)) / 2f;
        float pitch = -6f * roar(c) + 6f * crouch(c) - 14f * brake(c) - 40f * climb + 2.5f * (float) Math.sin(beat(c) - 1f) * air(c);
        return turn(pitch, 0f, -side * 28f * bank(c));
    }

    private static Pose chest(float c) {
        float breathe = 1.5f * (float) Math.sin(c / CYCLE * 8 * 2 * Math.PI) * (1f - air(c));
        return turn(breathe + 2f * (float) Math.sin(beat(c) - 0.8f) * air(c), 0f, 0f);
    }

    private static Pose neck(float c, int k) {
        float lag = k * 1.5f;
        float pitch = -6f * s(c, 4 + lag, 12 + lag) * (1f - s(c, 14, 20))
                + 6f * snap(c, 14 + lag * 0.5f, 21 + lag * 0.5f) * (1f - s(c, 50, 62))
                + (10f + 3f * (float) Math.sin(beat(c) - 0.6f - 0.35f * k)) * air(c);
        float yaw = 6f * (float) Math.sin((c - FIRE) / (FIRE_END - FIRE) * 2 * Math.PI) * fire(c)
                + 9f * bump(c, 322 + lag, 346 + lag) - 11f * bump(c, 344 + lag, 372 + lag)
                + 4f * (float) Math.sin(beat(c) / 2 - 0.3f * k) * air(c);
        return turn(pitch, yaw, 0f);
    }

    private static Pose head(float c) {
        float pitch = -12f * s(c, 4, 14) * (1f - s(c, 14, 20)) + 12f * snap(c, 16, 22) * (1f - s(c, 50, 62))
                + 1.5f * wave(c, 6) * fire(c) + (-42f + 3f * (float) Math.sin(beat(c) - 1.6f)) * air(c);
        float yaw = 12f * bump(c, 326, 350) - 14f * bump(c, 348, 374);
        return turn(pitch, yaw, 0f);
    }

    private static Pose jaw(float c) {
        float open = 38f * snap(c, 15, 21) * (1f - s(c, 48, 58)) + (5f + 3f * (float) Math.sin(beat(c) - 2f)) * air(c)
                + 16f * bump(c, 334, 352);
        return turn(open, 0f, 0f);
    }

    private static Pose tail(float c, int k, int side) {
        float ground = 1f - air(c);
        float pitch = (k < 3 ? 4f : 0f) * roar(c) + (6f + (1.5f + 0.4f * k) * (float) Math.sin(beat(c) - 1.2f - 0.45f * k)) * air(c);
        float yaw = (2.5f + 1.4f * k) * (float) Math.sin(c / CYCLE * 5 * 2 * Math.PI - 0.55f * k) * ground
                + (2f + 1.3f * k) * (float) Math.sin(beat(c) / 2 - 0.5f * k) * air(c) + side * (2f + 0.6f * k) * bank(c);
        return turn(pitch, yaw, 0f);
    }

    // part 1 is the thigh, 2 the shin, 3 the foot. bends for the jump and tucks back while it flies
    private static Pose leg(float c, int part) {
        float[] crouch = {-22f, 40f, -18f};
        float[] tucked = {62f, -25f, 45f};
        float sway = 5f * (float) Math.sin(beat(c) - part * 0.5f);
        return turn(crouch[part - 1] * crouch(c) + (tucked[part - 1] + sway) * air(c), 0f, 0f);
    }

    private static Pose shoulder(float c, int s) {
        float roll = -35f * folded(c) + 12f * roar(c) + 25f * bump(c, 52, 72) + 15f * brake(c) + 1.2f * wave(c, 6) * fire(c)
                + (2f + 30f * (float) Math.cos(beat(c))) * air(c) * flap(c);
        float yaw = 45f * folded(c) - 14f * roar(c) - 25f * brake(c) + 8f * (float) Math.sin(beat(c)) * air(c) * flap(c);
        return turn(0f, s * yaw, s * roll);
    }

    private static Pose elbow(float c, int s) {
        float yaw = 45f * folded(c) - 8f * roar(c) - 10f * brake(c) + (10f - 14f * (float) Math.sin(beat(c) - 0.7f) * flap(c)) * air(c);
        float roll = 10f * (float) Math.cos(beat(c) - 0.7f) * air(c) * flap(c);
        return turn(0f, s * yaw, s * roll);
    }

    private static Pose wrist(float c, int s) {
        return turn(0f, s * 25f * folded(c), s * 14f * (float) Math.cos(beat(c) - 1.2f) * air(c) * flap(c));
    }

    private static Pose finger(float c, int s, int i) {
        float spread = (i - 1.5f) * 5f * Math.min(1f, roar(c) + air(c));
        float yaw = (4f + 4f * i) * folded(c) - spread;
        return turn(0f, s * yaw, s * 10f * (float) Math.cos(beat(c) - 1.6f - 0.25f * i) * air(c) * flap(c));
    }

    // wings folded at the start and the end of a round
    private static float folded(float c) {
        return 1f - s(c, 0, 8) + s(c, 300, 322);
    }

    // wings open and the chest up while it roars
    private static float roar(float c) {
        return snap(c, 0, 10) * (1f - s(c, 54, 66));
    }

    // 1 while it breathes fire, fades in and out
    private static float fire(float c) {
        return s(c, FIRE, FIRE + 6) * (1f - s(c, FIRE_END - 6, FIRE_END));
    }

    // 1 while it flies
    private static float air(float c) {
        return s(c, LEAP - 2, LEAP + 24) * (1f - s(c, CIRCLE_END + 4, TOUCH));
    }

    // halfway round the circle it glides with the wings out, looks more real then flapping all the time
    private static float flap(float c) {
        return 1f - 0.75f * bump(c, 150, 214);
    }

    private static float crouch(float c) {
        return bump(c, 54, 76);
    }

    // wings forward to slow down before it lands
    private static float brake(float c) {
        return bump(c, CIRCLE_END - 6, TOUCH + 6);
    }

    // how hard it turns, 1 at the fastest point of the circle
    private static float bank(float c) {
        return (s(c + 1, CIRCLE, CIRCLE_END) - s(c - 1, CIRCLE, CIRCLE_END)) / 2f / (1.875f / (CIRCLE_END - CIRCLE));
    }

    // the wing beat angle, 0 is wings all the way up
    private static float beat(float c) {
        return (float) ((c - LEAP) / BEAT * 2 * Math.PI);
    }

    // a random number from 0 to 1 that is always the same for the same a and b. new Random(seed) was no good
    // here, seeds next to each other give almost the same first number and all the flames came out in one clump
    private static float noise(int a, int b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    // 0 before from, 1 after to, smooth in between
    private static float s(float t, float from, float to) {
        float u = Math.clamp((t - from) / (to - from), 0f, 1f);
        return u * u * u * (u * (u * 6f - 15f) + 10f);
    }

    // like s but it shoots a bit past 1 and settles, for moves that should snap
    private static float snap(float t, float from, float to) {
        float u = Math.clamp((t - from) / (to - from), 0f, 1f) - 1f;
        return 1f + 2.4f * u * u * u + 1.4f * u * u;
    }

    // 0 outside, 1 in the middle
    private static float bump(float t, float from, float to) {
        if (t <= from || t >= to) {
            return 0f;
        }
        return (float) Math.sin((t - from) / (to - from) * Math.PI);
    }

    private static float wave(float t, float ticks) {
        return (float) Math.sin(t / ticks * 2 * Math.PI);
    }

    private static Pose turn(float pitch, float yaw, float roll) {
        return Pose.IDENTITY.withRotation(pitch, yaw, roll);
    }

    // a pose worked out by a function of the tick. the intro plays once, then the loop part repeats
    private record Motion(int introTicks, int loopTicks, Function<Float, Pose> pose) implements Animation {

        @Override
        public Pose poseAt(float tick) {
            return pose.apply(tick);
        }
    }

    record Vec(float x, float y, float z) {

        static final Vec ZERO = new Vec(0f, 0f, 0f);
        static final Vec UP = new Vec(0f, 1f, 0f);

        Vec plus(Vec o) {
            return new Vec(x + o.x, y + o.y, z + o.z);
        }

        Vec minus(Vec o) {
            return new Vec(x - o.x, y - o.y, z - o.z);
        }

        Vec times(float k) {
            return new Vec(x * k, y * k, z * k);
        }

        float dot(Vec o) {
            return x * o.x + y * o.y + z * o.z;
        }

        Vec cross(Vec o) {
            return new Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
        }

        float length() {
            return (float) Math.sqrt(dot(this));
        }

        Vec unit() {
            float length = length();
            return length < 1e-6f ? ZERO : times(1f / length);
        }

        Vec lerp(Vec to, float k) {
            return plus(to.minus(this).times(k));
        }

        // undoes a pose rotation, the pose turns yaw first, then pitch, then roll
        Vec turnedBack(float pitch, float yaw, float roll) {
            double a = Math.toRadians(-yaw);
            float x1 = (float) (x * Math.cos(a) + z * Math.sin(a));
            float z1 = (float) (-x * Math.sin(a) + z * Math.cos(a));
            double b = Math.toRadians(-pitch);
            float y2 = (float) (y * Math.cos(b) - z1 * Math.sin(b));
            float z2 = (float) (y * Math.sin(b) + z1 * Math.cos(b));
            double c = Math.toRadians(-roll);
            return new Vec((float) (x1 * Math.cos(c) - y2 * Math.sin(c)), (float) (x1 * Math.sin(c) + y2 * Math.cos(c)), z2);
        }
    }

    // collects the parts, then gives every piece its animation. in build mode every piece gets its own
    // path through the tornado and its own landing time
    private static final class Build {

        private record Draft(String name, String parent, Material block, Pose pose, int stage, int order) {
        }

        final Kind kind;
        final boolean build;
        final int offset;
        final int side;
        private final List<Piece> bones = new ArrayList<>();
        private final List<Draft> drafts = new ArrayList<>();
        private final Map<String, Vec> boneAt = new HashMap<>();
        private final Map<String, Material> lit = new HashMap<>();
        private final Set<String> bright = new HashSet<>();
        private int stage = -1;
        private String last;

        Build(Kind kind, boolean build, int offset, int side) {
            this.kind = kind;
            this.build = build;
            this.offset = offset;
            this.side = side;
        }

        Design design() {
            skeleton(this);
            // pieces land in this order, feet first and the head last so the eyes are the the last thing
            stage++;
            leg(this, -1);
            leg(this, 1);
            stage++;
            hips(this);
            stage++;
            tail(this);
            stage++;
            chest(this);
            stage++;
            neck(this);
            stage++;
            wing(this, -1);
            wing(this, 1);
            stage++;
            head(this);

            List<Piece> pieces = new ArrayList<>(bones);
            List<Draft> flying = new ArrayList<>(drafts);
            flying.sort(Comparator.comparingInt(Draft::stage).thenComparingInt(Draft::order));
            Random random = new Random(42);
            for (int i = 0; i < flying.size(); i++) {
                Draft d = flying.get(i);
                Animation animation = null;
                if (build) {
                    int lock = FIRST_LOCK + Math.round((LAST_LOCK - FIRST_LOCK) * i / (float) (flying.size() - 1)) + random.nextInt(3) - 1;
                    Pose p = d.pose();
                    animation = flight(boneAt.get(d.parent()).plus(new Vec(p.x(), p.y(), p.z())), p, lock, random);
                }
                pieces.add(new Piece(d.name(), d.parent(), d.block(), lit.get(d.name()), bright.contains(d.name()), d.pose(), animation, 0));
            }
            for (int i = 0; i < 140; i++) {
                Function<Float, Pose> flame = flame(kind, i);
                pieces.add(new Piece("flame_" + i, "head", kind.flames[i % kind.flames.length], null, false, Pose.IDENTITY,
                        life(c -> flame.apply(c), true), 1));
            }

            Animation root = life(c -> root(c, side), false);
            if (build) {
                root = root.event(CHARGE, "burst").event(WAKE, "wake");
            }
            int start = build ? AWAKE : 0;
            root = root.event(at(15, start), "roar").event(at(FIRE, start), "fire").event(at(TOUCH - 2, start), "land");
            for (int beat = LEAP; beat < CIRCLE_END; beat += BEAT) {
                root = root.event(at(beat, start), "beat");
            }
            return new Design(pieces, root);
        }

        // the tick of the animation where its life reaches c the first time
        private int at(int c, int start) {
            return build ? start + c : Math.floorMod(c - offset, CYCLE);
        }

        // turns a function of its life into an animation. build mode holds the rest pose until all pieces
        // are in, then blends into the start of its life while it wakes up
        Animation life(Function<Float, Pose> life, boolean hiddenBefore) {
            if (!build) {
                return new Motion(0, CYCLE, t -> life.apply((t + offset) % CYCLE));
            }
            Pose first = life.apply(0f);
            return new Motion(AWAKE, CYCLE, t -> {
                if (t >= AWAKE) {
                    return life.apply(t - AWAKE);
                }
                if (hiddenBefore) {
                    return HIDDEN;
                }
                float k = s(t, WAKE + 6, AWAKE);
                float shiver = 1.6f * (float) Math.sin(t * 0.8f) * bump(t, WAKE, WAKE + 14);
                Pose pose = Pose.IDENTITY.lerp(first, k);
                return pose.withRotation(pose.pitch() + shiver, pose.yaw(), pose.roll());
            });
        }

        void bone(String name, String parent, float x, float y, float z, Function<Float, Pose> motion) {
            Vec at = new Vec(x, y, z);
            boneAt.put(name, parent == null ? at : boneAt.get(parent).plus(at));
            bones.add(new Piece(name, parent, null, null, false, Pose.IDENTITY.withOffset(x, y, z), life(motion, false), 0));
        }

        Build box(String name, String bone, Material block, float x, float y, float z, float sx, float sy, float sz) {
            return box(name, bone, block, x, y, z, sx, sy, sz, 0f, 0f, 0f);
        }

        Build box(String name, String bone, Material block, float x, float y, float z, float sx, float sy, float sz,
                  float pitch, float yaw, float roll) {
            drafts.add(new Draft(name, bone, block, Pose.IDENTITY.withOffset(x, y, z).withRotation(pitch, yaw, roll)
                    .withScale(sx, sy, sz), stage, drafts.size()));
            last = name;
            return this;
        }

        // the block it swaps to when it wakes up
        Build lit(Material block, boolean glowing) {
            lit.put(last, block);
            if (glowing) {
                bright.add(last);
            }
            return this;
        }

        // a box from one point to another, width across and thick along the normal
        Build limb(String name, String bone, Material block, Vec from, Vec to, float width, float thick, Vec normal) {
            Vec d = to.minus(from).unit();
            float pitch = (float) -Math.asin(Math.clamp(d.y(), -1f, 1f));
            float yaw = (float) Math.atan2(d.x(), d.z());
            float roll = 0f;
            if (normal != null) {
                Vec u = new Vec((float) Math.cos(yaw), 0f, (float) -Math.sin(yaw));
                Vec v = new Vec((float) (Math.sin(yaw) * Math.sin(pitch)), (float) Math.cos(pitch), (float) (Math.cos(yaw) * Math.sin(pitch)));
                Vec n = normal.unit();
                roll = (float) Math.atan2(-n.dot(u), n.dot(v));
            }
            Vec center = from.plus(to).times(0.5f);
            return box(name, bone, block, center.x(), center.y(), center.z(), width, thick, to.minus(from).length(),
                    (float) Math.toDegrees(pitch), (float) Math.toDegrees(yaw), (float) Math.toDegrees(roll));
        }

        // a spike on the back, leaning back
        Build fin(String name, String bone, Vec base, float height, float length) {
            return box(name, bone, kind.trim, base.x(), base.y() + height * 0.4f, base.z(), 0.06f, height, length, -35f, 0f, 0f);
        }

        private static Animation flight(Vec rest, Pose pose, int lock, Random random) {
            Vec center = new Vec(0f, 3.2f, -0.5f);
            Vec ball = new Vec(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).times(2.4f);
            float height = 0.4f + random.nextFloat() * 7.2f;
            float radius = 4.2f + 0.5f * height + random.nextFloat() * 1.8f;
            float angle = (float) (random.nextFloat() * 2 * Math.PI);
            float speed = 0.8f / radius;
            float spinPitch = (random.nextFloat() * 2f - 1f) * 10f;
            float spinYaw = (random.nextFloat() * 2f - 1f) * 10f;
            float spinRoll = (random.nextFloat() * 2f - 1f) * 10f;
            return new Motion(lock + 6, 0, t -> {
                if (t >= lock) {
                    return Pose.IDENTITY.withScale(1f + 0.2f * bump(t, lock, lock + 6));
                }
                Vec start = center.plus(spun(ball, t).times(1f - 0.3f * s(t, 0, CHARGE)));
                float a = angle + speed * t;
                float shrink = 1f - 0.3f * s(t, BURST, LAST_LOCK);
                Vec orbit = new Vec((float) Math.cos(a) * radius * shrink, height + 0.35f * (float) Math.sin(2 * a), -0.5f + (float) Math.sin(a) * radius * shrink);
                float out = 1f - (1f - s(t, CHARGE, BURST)) * (1f - s(t, CHARGE, BURST));
                Vec at = t < CHARGE ? start : center.plus(spun(ball, CHARGE).times(0.7f)).lerp(orbit, out);
                float home = t < lock - FLY ? 0f : snap(t, lock - FLY, lock);
                Vec offset = at.lerp(rest, home).minus(rest).turnedBack(pose.pitch(), pose.yaw(), pose.roll());
                float spin = 1f - home;
                float size = 0.45f + 0.55f * s(t, CHARGE, BURST);
                size += (1f - size) * home;
                return Pose.IDENTITY.withOffset(offset.x(), offset.y(), offset.z())
                        .withRotation(wrap(spinPitch * t) * spin, wrap(spinYaw * t) * spin, wrap(spinRoll * t) * spin)
                        .withScale(size);
            }).event(lock, "lock");
        }

        // the ball spins around while it charges up
        private static Vec spun(Vec v, float t) {
            double a = t * 0.3;
            return new Vec((float) (v.x() * Math.cos(a) + v.z() * Math.sin(a)), v.y(), (float) (-v.x() * Math.sin(a) + v.z() * Math.cos(a)));
        }

        // spins stay under half a turn, a bigger jump between two frames actualy turns the short way round and flips
        private static float wrap(float degrees) {
            float d = degrees % 360f;
            return d > 180f ? d - 360f : d < -180f ? d + 360f : d;
        }
    }
}
