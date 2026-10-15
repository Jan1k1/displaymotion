package org.jan1k.displaymotion.packetevents.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Euler;
import org.jan1k.displaymotion.Frames;
import org.jan1k.displaymotion.Pose;
import org.jan1k.displaymotion.packetevents.DisplayMotion;
import org.jan1k.displaymotion.packetevents.ModelBuilder;
import org.jan1k.displaymotion.packetevents.MotionDisplay;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * A model made in Blockbench ({@code .bbmodel}), turned into vanilla blocks.
 * Every cube becomes a block display with the vanilla block closest to its texture color,
 * every group becomes an invisible pivot, and the animations play on the groups.
 */
public final class BlockbenchModel {

    private static final float PIXELS_PER_BLOCK = 16f;
    private static final float MIN_SIZE = 0.01f;
    private static final int MIN_LOOP_TICKS = 4;
    private static final Animation REST = Animation.keyframes().at(0, Pose.IDENTITY).build();

    record Node(String name, String uuid, int parent, boolean group, float[] origin, float[] rotation,
                float[] from, float[] to, Material material) {
    }

    record Key(float time, float x, float y, float z, String interpolation) {
    }

    record Channels(List<Key> rotation, List<Key> position, List<Key> scale) {
    }

    record Clip(String name, boolean loop, float length, Map<String, Channels> bones) {
    }

    private final List<Node> nodes;
    private final Map<String, Clip> clips;
    private final Map<MotionDisplay, Float> scales = Collections.synchronizedMap(new WeakHashMap<>());

    private BlockbenchModel(List<Node> nodes, Map<String, Clip> clips) {
        this.nodes = nodes;
        this.clips = clips;
    }

    /** Reads a {@code .bbmodel} file. Safe on any thread. */
    public static BlockbenchModel load(Path file) throws IOException {
        return parse(Files.readString(file));
    }

    /** Reads the text of a {@code .bbmodel} file. */
    public static BlockbenchModel parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, BufferedImage> textures = readTextures(root);
        float uvWidth = 16f;
        float uvHeight = 16f;
        if (root.has("resolution")) {
            JsonObject resolution = root.getAsJsonObject("resolution");
            uvWidth = resolution.get("width").getAsFloat();
            uvHeight = resolution.get("height").getAsFloat();
        }

        Map<String, JsonObject> elements = new HashMap<>();
        for (JsonElement element : array(root, "elements")) {
            JsonObject object = element.getAsJsonObject();
            elements.put(object.get("uuid").getAsString(), object);
        }
        // format 5 keeps the group data in its own list, older files put it straight in the outliner
        Map<String, JsonObject> groups = new HashMap<>();
        for (JsonElement group : array(root, "groups")) {
            JsonObject object = group.getAsJsonObject();
            groups.put(object.get("uuid").getAsString(), object);
        }

        List<Node> nodes = new ArrayList<>();
        Map<String, Integer> names = new HashMap<>();
        Reader reader = new Reader(elements, groups, textures, uvWidth, uvHeight, nodes, names);
        reader.walk(array(root, "outliner"), -1);

        Map<String, Clip> clips = new LinkedHashMap<>();
        for (JsonElement animation : array(root, "animations")) {
            Clip clip = readClip(animation.getAsJsonObject());
            clips.put(clip.name(), clip);
        }
        return new BlockbenchModel(List.copyOf(nodes), clips);
    }

    /** Spawns it at real size, 16 Blockbench pixels are one block. */
    public MotionDisplay spawn(DisplayMotion motion, Location at) {
        return spawn(motion, at, 1f);
    }

    /** Spawns it bigger or smaller. 0.5 is half size. */
    public MotionDisplay spawn(DisplayMotion motion, Location at, float scale) {
        if (!(scale > 0)) {
            throw new IllegalArgumentException("scale must be more than 0, got " + scale);
        }
        ModelBuilder builder = motion.model(at);
        for (Node node : nodes) {
            String parent = node.parent() < 0 ? null : nodes.get(node.parent()).name();
            Pose pose = restPose(node, scale);
            if (node.group()) {
                builder.group(node.name(), pose, parent);
            } else {
                builder.block(node.name(), node.material().createBlockData(), pose, parent);
            }
        }
        MotionDisplay display = builder.spawn();
        scales.put(display, scale);
        return display;
    }

    /** Plays one of the model's animations. Groups it does not move go back to their rest pose. */
    public void play(MotionDisplay display, String animation) {
        Clip clip = clips.get(Objects.requireNonNull(animation, "animation"));
        if (clip == null) {
            throw new IllegalArgumentException("no animation named '" + animation + "', there are " + clips.keySet());
        }
        float scale = scales.getOrDefault(display, 1f);
        for (Node node : nodes) {
            if (!node.group()) {
                continue;
            }
            Channels channels = clip.bones().get(node.uuid());
            display.play(node.name(), channels == null ? REST : boneAnimation(node, clip, channels, scale));
        }
    }

    /** Names of the animations in the file. */
    public List<String> animations() {
        return List.copyOf(clips.keySet());
    }

    /** Names of the parts, the same names {@link MotionDisplay#play(String, Animation)} takes. */
    public List<String> parts() {
        return nodes.stream().map(Node::name).toList();
    }

    Animation animation(String clip, String part, float scale) {
        Clip found = clips.get(clip);
        Node node = nodes.stream().filter(n -> n.name().equals(part)).findFirst().orElseThrow();
        Channels channels = found.bones().get(node.uuid());
        return channels == null ? REST : boneAnimation(node, found, channels, scale);
    }

    Material material(String part) {
        return nodes.stream().filter(node -> node.name().equals(part)).findFirst().map(Node::material).orElse(null);
    }

    Pose restPose(String part, float scale) {
        return nodes.stream().filter(node -> node.name().equals(part)).findFirst()
                .map(node -> restPose(node, scale)).orElseThrow();
    }

    private Pose restPose(Node node, float scale) {
        float[] pivot = node.parent() < 0 ? new float[3] : nodes.get(node.parent()).origin();
        float[] rotation = Euler.fromZyx(node.rotation()[0], node.rotation()[1], node.rotation()[2]);
        float unit = scale / PIXELS_PER_BLOCK;
        if (node.group()) {
            return Pose.IDENTITY
                    .withOffset((node.origin()[0] - pivot[0]) * unit, (node.origin()[1] - pivot[1]) * unit, (node.origin()[2] - pivot[2]) * unit)
                    .withRotation(rotation[0], rotation[1], rotation[2]);
        }
        // a cube turns around its own origin, so its center moves: origin + R * (center - origin)
        double[] r = Euler.zyxMatrix(node.rotation()[0], node.rotation()[1], node.rotation()[2]);
        float[] center = new float[3];
        float[] size = new float[3];
        for (int i = 0; i < 3; i++) {
            center[i] = (node.from()[i] + node.to()[i]) / 2f - node.origin()[i];
            size[i] = Math.max(Math.abs(node.to()[i] - node.from()[i]) * unit, MIN_SIZE);
        }
        float[] turned = multiply(r, center);
        return Pose.IDENTITY
                .withOffset((node.origin()[0] - pivot[0] + turned[0]) * unit,
                        (node.origin()[1] - pivot[1] + turned[1]) * unit,
                        (node.origin()[2] - pivot[2] + turned[2]) * unit)
                .withRotation(rotation[0], rotation[1], rotation[2])
                .withScale(size[0], size[1], size[2]);
    }

    private static Animation boneAnimation(Node bone, Clip clip, Channels channels, float scale) {
        int ticks = Math.max(Math.round(clip.length() * 20f), clip.loop() ? MIN_LOOP_TICKS : 1);
        float unit = scale / PIXELS_PER_BLOCK;
        float[] rest = bone.rotation();
        double[] undoRest = transpose(Euler.zyxMatrix(rest[0], rest[1], rest[2]));
        Animation animation = new Animation() {
            @Override
            public Pose poseAt(float tick) {
                float time = (clip.loop() ? Math.floorMod((int) tick, ticks) + (tick - (int) tick) : Math.min(tick, ticks)) / 20f;
                float[] turn = sample(channels.rotation(), time, 0f);
                float[] move = sample(channels.position(), time, 0f);
                float[] size = sample(channels.scale(), time, 1f);
                // blockbench flips x and y for animated rotation and x for position, same as bedrock
                double[] total = Euler.zyxMatrix(rest[0] - turn[0], rest[1] - turn[1], rest[2] + turn[2]);
                float[] angles = Euler.fromMatrix(multiply(undoRest, total));
                // the part already has its rest rotation, so the move has to be undone through it too
                float[] offset = multiply(undoRest, new float[]{-move[0] * unit, move[1] * unit, move[2] * unit});
                return Pose.IDENTITY.withOffset(offset[0], offset[1], offset[2])
                        .withRotation(angles[0], angles[1], angles[2])
                        .withScale(size[0], size[1], size[2]);
            }

            @Override
            public int introTicks() {
                return clip.loop() ? 0 : ticks;
            }

            @Override
            public int loopTicks() {
                return clip.loop() ? ticks : 0;
            }
        };
        Frames.check(animation);
        return animation;
    }

    static float[] sample(List<Key> keys, float time, float fallback) {
        if (keys.isEmpty()) {
            return new float[]{fallback, fallback, fallback};
        }
        if (time <= keys.getFirst().time()) {
            return values(keys.getFirst());
        }
        if (time >= keys.getLast().time()) {
            return values(keys.getLast());
        }
        int i = 0;
        while (keys.get(i + 1).time() < time) {
            i++;
        }
        Key from = keys.get(i);
        Key to = keys.get(i + 1);
        float t = (time - from.time()) / Math.max(to.time() - from.time(), 1e-6f);
        if (from.interpolation().equals("step")) {
            return values(from);
        }
        if (smooth(from) || smooth(to)) {
            Key before = keys.get(Math.max(i - 1, 0));
            Key after = keys.get(Math.min(i + 2, keys.size() - 1));
            return new float[]{
                    catmullRom(before.x(), from.x(), to.x(), after.x(), t),
                    catmullRom(before.y(), from.y(), to.y(), after.y(), t),
                    catmullRom(before.z(), from.z(), to.z(), after.z(), t)};
        }
        return new float[]{lerp(from.x(), to.x(), t), lerp(from.y(), to.y(), t), lerp(from.z(), to.z(), t)};
    }

    private static boolean smooth(Key key) {
        return key.interpolation().equals("catmullrom") || key.interpolation().equals("bezier");
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (3 * p1 - p0 - 3 * p2 + p3) * t3);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float[] values(Key key) {
        return new float[]{key.x(), key.y(), key.z()};
    }

    private static Clip readClip(JsonObject object) {
        String name = object.get("name").getAsString();
        boolean loop = object.has("loop") && object.get("loop").getAsString().equals("loop");
        float length = object.has("length") ? object.get("length").getAsFloat() : 0f;
        Map<String, Channels> bones = new HashMap<>();
        if (object.has("animators")) {
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("animators").entrySet()) {
                List<Key> rotation = new ArrayList<>();
                List<Key> position = new ArrayList<>();
                List<Key> scale = new ArrayList<>();
                for (JsonElement keyframe : array(entry.getValue().getAsJsonObject(), "keyframes")) {
                    JsonObject key = keyframe.getAsJsonObject();
                    JsonObject point = array(key, "data_points").get(0).getAsJsonObject();
                    Key parsed = new Key(key.get("time").getAsFloat(), number(point, "x"), number(point, "y"), number(point, "z"),
                            key.has("interpolation") ? key.get("interpolation").getAsString() : "linear");
                    switch (key.get("channel").getAsString()) {
                        case "rotation" -> rotation.add(parsed);
                        case "position" -> position.add(parsed);
                        case "scale" -> scale.add(parsed);
                        default -> {
                        }
                    }
                }
                rotation.sort((a, b) -> Float.compare(a.time(), b.time()));
                position.sort((a, b) -> Float.compare(a.time(), b.time()));
                scale.sort((a, b) -> Float.compare(a.time(), b.time()));
                bones.put(entry.getKey(), new Channels(rotation, position, scale));
            }
        }
        return new Clip(name, loop, length, bones);
    }

    // molang like "math.sin(q.anim_time*90)" is not supported yet, those just read as 0
    private static float number(JsonObject point, String axis) {
        if (!point.has(axis)) {
            return 0f;
        }
        try {
            return Float.parseFloat(point.get(axis).getAsString().trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private static Map<String, BufferedImage> readTextures(JsonObject root) {
        Map<String, BufferedImage> textures = new HashMap<>();
        JsonArray list = array(root, "textures");
        for (int i = 0; i < list.size(); i++) {
            JsonObject texture = list.get(i).getAsJsonObject();
            if (!texture.has("source")) {
                continue;
            }
            String source = texture.get("source").getAsString();
            int comma = source.indexOf(',');
            if (comma < 0) {
                continue;
            }
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(source.substring(comma + 1))));
                if (image != null) {
                    textures.put(String.valueOf(i), image);
                    if (texture.has("id")) {
                        textures.put("id:" + texture.get("id").getAsString(), image);
                    }
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // a broken texture just means that cube gets the fallback block
            }
        }
        return textures;
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static float[] floats(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return new float[3];
        }
        JsonArray array = object.getAsJsonArray(key);
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static float[] multiply(double[] m, float[] v) {
        return new float[]{
                (float) (m[0] * v[0] + m[1] * v[1] + m[2] * v[2]),
                (float) (m[3] * v[0] + m[4] * v[1] + m[5] * v[2]),
                (float) (m[6] * v[0] + m[7] * v[1] + m[8] * v[2])};
    }

    private static double[] multiply(double[] a, double[] b) {
        double[] out = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                out[row * 3 + col] = a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col];
            }
        }
        return out;
    }

    private static double[] transpose(double[] m) {
        return new double[]{m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]};
    }

    private record Reader(Map<String, JsonObject> elements, Map<String, JsonObject> groups,
                          Map<String, BufferedImage> textures, float uvWidth, float uvHeight,
                          List<Node> nodes, Map<String, Integer> names) {

        void walk(JsonArray children, int parent) {
            for (JsonElement child : children) {
                if (child.isJsonPrimitive()) {
                    JsonObject element = elements.get(child.getAsString());
                    if (element != null && (!element.has("type") || element.get("type").getAsString().equals("cube"))) {
                        addCube(element, parent);
                    }
                    continue;
                }
                JsonObject group = child.getAsJsonObject();
                JsonObject data = groups.getOrDefault(group.get("uuid").getAsString(), group);
                String name = unique(data.has("name") ? data.get("name").getAsString() : "group");
                nodes.add(new Node(name, data.get("uuid").getAsString(), parent, true,
                        floats(data, "origin"), floats(data, "rotation"), null, null, null));
                walk(array(group, "children"), nodes.size() - 1);
            }
        }

        private void addCube(JsonObject element, int parent) {
            float[] from = floats(element, "from");
            float[] to = floats(element, "to");
            float inflate = element.has("inflate") ? element.get("inflate").getAsFloat() : 0f;
            for (int i = 0; i < 3; i++) {
                from[i] -= inflate;
                to[i] += inflate;
            }
            float[] rotation = new float[3];
            if (element.has("rotation") && element.get("rotation").isJsonArray()) {
                rotation = floats(element, "rotation");
            } else if (element.has("rotation") && element.get("rotation").isJsonObject()) {
                // old java block format, one axis only
                JsonObject old = element.getAsJsonObject("rotation");
                int axis = switch (old.get("axis").getAsString()) {
                    case "x" -> 0;
                    case "y" -> 1;
                    default -> 2;
                };
                rotation[axis] = old.get("angle").getAsFloat();
            }
            String name = unique(element.has("name") ? element.get("name").getAsString() : "cube");
            nodes.add(new Node(name, element.get("uuid").getAsString(), parent, false,
                    floats(element, "origin"), rotation, from, to, BlockPalette.closest(averageColor(element))));
        }

        private int averageColor(JsonObject element) {
            long red = 0;
            long green = 0;
            long blue = 0;
            long count = 0;
            if (element.has("faces")) {
                for (Map.Entry<String, JsonElement> face : element.getAsJsonObject("faces").entrySet()) {
                    JsonObject data = face.getValue().getAsJsonObject();
                    if (!data.has("texture") || data.get("texture").isJsonNull() || !data.has("uv")) {
                        continue;
                    }
                    String ref = data.get("texture").getAsString();
                    BufferedImage image = textures.getOrDefault("id:" + ref, textures.get(ref));
                    if (image == null) {
                        continue;
                    }
                    float[] uv = new float[4];
                    JsonArray box = data.getAsJsonArray("uv");
                    for (int i = 0; i < 4; i++) {
                        uv[i] = box.get(i).getAsFloat();
                    }
                    int x1 = clamp(Math.min(uv[0], uv[2]) / uvWidth * image.getWidth(), image.getWidth());
                    int x2 = clamp(Math.max(uv[0], uv[2]) / uvWidth * image.getWidth(), image.getWidth());
                    int y1 = clamp(Math.min(uv[1], uv[3]) / uvHeight * image.getHeight(), image.getHeight());
                    int y2 = clamp(Math.max(uv[1], uv[3]) / uvHeight * image.getHeight(), image.getHeight());
                    // big textures get sampled, no need to read every pixel for an average
                    int step = Math.max(1, Math.max(x2 - x1, y2 - y1) / 32);
                    for (int y = y1; y <= y2 && y < image.getHeight(); y += step) {
                        for (int x = x1; x <= x2 && x < image.getWidth(); x += step) {
                            int argb = image.getRGB(x, y);
                            if ((argb >>> 24) < 128) {
                                continue;
                            }
                            red += argb >> 16 & 0xFF;
                            green += argb >> 8 & 0xFF;
                            blue += argb & 0xFF;
                            count++;
                        }
                    }
                }
            }
            if (count == 0) {
                return 0xB4B4B4;
            }
            return (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
        }

        private static int clamp(float value, int size) {
            return Math.clamp((int) value, 0, Math.max(size - 1, 0));
        }

        private String unique(String base) {
            String name = base.isBlank() ? "part" : base;
            int seen = names.merge(name, 1, Integer::sum);
            return seen == 1 ? name : name + "_" + seen;
        }
    }
}
