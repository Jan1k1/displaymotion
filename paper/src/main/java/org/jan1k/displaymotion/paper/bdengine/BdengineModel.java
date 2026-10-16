package org.jan1k.displaymotion.paper.bdengine;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jan1k.displaymotion.Animation;
import org.jan1k.displaymotion.Euler;
import org.jan1k.displaymotion.KeyframeBuilder;
import org.jan1k.displaymotion.Pose;
import org.jan1k.displaymotion.paper.DisplayMotion;
import org.jan1k.displaymotion.paper.ImportedModel;
import org.jan1k.displaymotion.paper.ModelBuilder;
import org.jan1k.displaymotion.paper.MotionDisplay;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * A model made in BDEngine (block-display.com), saved as a {@code .bdengine} project file.
 * Blocks, items, player heads and texts come in exactly like in the editor, and so do the animations.
 */
public final class BdengineModel implements ImportedModel {

    private static final String ROOT = "root";
    private static final int MIN_LOOP_TICKS = 4;

    enum Kind { GROUP, BLOCK, ITEM, TEXT }

    record Frame(int time, float[] position, float[] rotation, float[] scale) {
    }

    record Node(String name, Kind kind, int parent, float[] matrix, String data, String headTexture,
                int sky, int block, float[] restPosition, float[] restRotation, float[] restScale,
                Map<Integer, List<Frame>> frames) {

        boolean animated() {
            return !frames.isEmpty() && restScale != null
                    && restScale[0] != 0 && restScale[1] != 0 && restScale[2] != 0;
        }
    }

    private final List<Node> nodes;
    private final Map<String, Integer> clips;

    private BdengineModel(List<Node> nodes, Map<String, Integer> clips) {
        this.nodes = nodes;
        this.clips = clips;
    }

    /** Reads a {@code .bdengine} file. Safe on any thread. */
    public static BdengineModel load(Path file) throws IOException {
        return read(Files.readAllBytes(file));
    }

    /** Reads the content of a {@code .bdengine} file, or the plain json inside it. */
    public static BdengineModel parse(String content) throws IOException {
        return read(content.getBytes(StandardCharsets.UTF_8));
    }

    /** Reads the bytes of a {@code .bdengine} file, any of the formats the editor saved over time. */
    public static BdengineModel read(byte[] bytes) throws IOException {
        return fromJson(unpack(bytes, 0));
    }

    // older files are base64 text of gzip of json. the editor now saves raw gzip around a small
    // "PRJ2" container with scene.json in it. this peels whatever layer it finds until json shows up
    static String unpack(byte[] bytes, int depth) throws IOException {
        if (depth > 4) {
            throw new IOException("not a bdengine file, too many layers");
        }
        if (bytes.length > 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                return unpack(in.readAllBytes(), depth + 1);
            }
        }
        if (bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'R' && bytes[2] == 'J') {
            return unpack(sceneFromContainer(bytes), depth + 1);
        }
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (text.startsWith("[") || text.startsWith("{")) {
            return text;
        }
        try {
            return unpack(Base64.getDecoder().decode(text), depth + 1);
        } catch (IllegalArgumentException e) {
            throw new IOException("not a bdengine file");
        }
    }

    // PRJ2 layout: magic (4) + version (1) + entry count (int) then per entry: name length (short),
    // name, data length (int), data. all little endian
    private static byte[] sceneFromContainer(byte[] bytes) throws IOException {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.position(5);
        int entries = buffer.getInt();
        for (int i = 0; i < entries; i++) {
            byte[] name = new byte[Short.toUnsignedInt(buffer.getShort())];
            buffer.get(name);
            byte[] data = new byte[buffer.getInt()];
            buffer.get(data);
            if (new String(name, StandardCharsets.UTF_8).equals("scene.json")) {
                return data;
            }
        }
        throw new IOException("bdengine project has no scene.json inside");
    }

    private static BdengineModel fromJson(String text) {
        JsonElement parsed = JsonParser.parseString(text);
        JsonObject root = parsed.isJsonArray() ? parsed.getAsJsonArray().get(0).getAsJsonObject() : parsed.getAsJsonObject();

        Map<String, Integer> clips = new LinkedHashMap<>();
        if (root.has("listAnim")) {
            for (JsonElement anim : root.getAsJsonArray("listAnim")) {
                JsonObject object = anim.getAsJsonObject();
                clips.put(object.get("name").getAsString(), object.get("id").getAsInt());
            }
        }

        List<Node> nodes = new ArrayList<>();
        Map<String, Integer> names = new HashMap<>();
        walk(root, -1, nodes, names, true);
        return new BdengineModel(List.copyOf(nodes), clips);
    }

    @Override
    public MotionDisplay spawn(DisplayMotion motion, Location at) {
        return spawn(motion, at, 1f);
    }

    @Override
    public MotionDisplay spawn(DisplayMotion motion, Location at, float scale) {
        if (!(scale > 0)) {
            throw new IllegalArgumentException("scale must be more than 0, got " + scale);
        }
        ModelBuilder builder = motion.model(at);
        builder.group(ROOT, new float[]{scale, 0, 0, 0, 0, scale, 0, 0, 0, 0, scale, 0, 0, 0, 0, 1}, null);
        for (Node node : nodes) {
            String parent = node.parent() < 0 ? ROOT : nodes.get(node.parent()).name();
            float[] matrix = node.animated() ? withoutRest(node) : node.matrix();
            switch (node.kind()) {
                case GROUP -> builder.group(node.name(), matrix, parent);
                case BLOCK -> builder.block(node.name(), blockData(node.data()), matrix, parent);
                case ITEM -> builder.item(node.name(), item(node), itemTransform(node.data()), matrix, parent);
                case TEXT -> builder.text(node.name(), Component.text(node.data()), matrix, parent);
            }
        }
        MotionDisplay display = builder.spawn();
        for (Node node : nodes) {
            if (node.kind() != Kind.GROUP) {
                display.brightness(node.name(), new Display.Brightness(node.block(), node.sky()));
            }
            if (node.animated()) {
                display.play(node.name(), Animation.keyframes().at(0, restPose(node)).build());
            }
        }
        return display;
    }

    /** Plays one of the model's animations, looping. */
    @Override
    public void play(MotionDisplay display, String animation, int fadeTicks) {
        Integer id = clips.get(Objects.requireNonNull(animation, "animation"));
        if (id == null) {
            throw new IllegalArgumentException("no animation named '" + animation + "', there are " + clips.keySet());
        }
        int length = 0;
        for (Node node : nodes) {
            for (Frame frame : node.frames().getOrDefault(id, List.of())) {
                length = Math.max(length, frame.time());
            }
        }
        length = Math.max(length, MIN_LOOP_TICKS);
        for (Node node : nodes) {
            if (node.animated()) {
                if (fadeTicks > 0) {
                    display.crossfade(node.name(), clip(node, id, length), fadeTicks);
                } else {
                    display.play(node.name(), clip(node, id, length));
                }
            }
        }
    }

    @Override
    public List<String> animations() {
        return List.copyOf(clips.keySet());
    }

    @Override
    public List<String> parts() {
        return nodes.stream().map(Node::name).toList();
    }

    Node node(String name) {
        return nodes.stream().filter(node -> node.name().equals(name)).findFirst().orElseThrow();
    }

    static Animation clip(Node node, int id, int length) {
        List<Frame> frames = node.frames().getOrDefault(id, List.of());
        if (frames.isEmpty()) {
            return Animation.keyframes().at(0, restPose(node)).at(length, restPose(node)).build().loop();
        }
        KeyframeBuilder keys = Animation.keyframes();
        int last = -1;
        for (Frame frame : frames) {
            if (frame.time() <= last) {
                continue;
            }
            keys.at(frame.time(), pose(frame.position(), frame.rotation(), frame.scale()));
            last = frame.time();
        }
        if (last < length) {
            Frame end = frames.getLast();
            keys.at(length, pose(end.position(), end.rotation(), end.scale()));
        }
        return keys.build().loop();
    }

    static Pose restPose(Node node) {
        return pose(node.restPosition(), node.restRotation(), node.restScale());
    }

    private static Pose pose(float[] position, float[] radians, float[] scale) {
        float[] rotation = Euler.fromXyz((float) Math.toDegrees(radians[0]), (float) Math.toDegrees(radians[1]), (float) Math.toDegrees(radians[2]));
        return Pose.IDENTITY.withOffset(position[0], position[1], position[2])
                .withRotation(rotation[0], rotation[1], rotation[2])
                .withScale(scale[0], scale[1], scale[2]);
    }

    // keyframe positions live in their own space. matrix * rest^-1 is the fixed bit in front of every keyframe
    static float[] withoutRest(Node node) {
        float[] p = node.restPosition();
        float[] s = node.restScale();
        double[] r = Euler.xyzMatrix(Math.toDegrees(node.restRotation()[0]), Math.toDegrees(node.restRotation()[1]), Math.toDegrees(node.restRotation()[2]));
        // rest^-1 = S^-1 * R^T * T(-p)
        double[] inverse = new double[12];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                inverse[row * 4 + col] = r[col * 3 + row] / s[row];
            }
            inverse[row * 4 + 3] = -(inverse[row * 4] * p[0] + inverse[row * 4 + 1] * p[1] + inverse[row * 4 + 2] * p[2]);
        }
        float[] m = node.matrix();
        float[] out = new float[16];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                double sum = col == 3 ? m[row * 4 + 3] : 0;
                for (int k = 0; k < 3; k++) {
                    sum += m[row * 4 + k] * inverse[k * 4 + col];
                }
                out[row * 4 + col] = (float) sum;
            }
        }
        out[15] = 1;
        return out;
    }

    private static void walk(JsonObject object, int parent, List<Node> nodes, Map<String, Integer> names, boolean root) {
        int index = parent;
        if (!root) {
            Kind kind = flag(object, "isCollection") ? Kind.GROUP
                    : flag(object, "isBlockDisplay") ? Kind.BLOCK
                    : flag(object, "isItemDisplay") ? Kind.ITEM
                    : flag(object, "isTextDisplay") ? Kind.TEXT : null;
            if (kind == null) {
                return;
            }
            String data = object.has("name") ? object.get("name").getAsString() : "";
            String label = kind == Kind.BLOCK || kind == Kind.ITEM ? data.replaceAll("\\[.*", "") : data;
            String name = unique(label, names);
            String head = null;
            if (object.has("tagHead") && object.getAsJsonObject("tagHead").has("Value")
                    && !object.getAsJsonObject("tagHead").get("Value").isJsonNull()) {
                head = object.getAsJsonObject("tagHead").get("Value").getAsString();
            }
            if ((head == null || head.isBlank()) && object.has("defaultTextureValue")) {
                head = object.get("defaultTextureValue").getAsString();
            }
            int sky = 15;
            int block = 0;
            if (object.has("brightness")) {
                JsonObject brightness = object.getAsJsonObject("brightness");
                sky = brightness.get("sky").getAsInt();
                block = brightness.get("block").getAsInt();
            }
            float[] restPosition = null;
            float[] restRotation = null;
            float[] restScale = null;
            if (object.has("defaultTransform")) {
                JsonObject rest = object.getAsJsonObject("defaultTransform");
                restPosition = vector(rest.get("position"), 0f);
                restRotation = vector(rest.get("rotation"), 0f);
                restScale = vector(rest.get("scale"), 1f);
            }
            Map<Integer, List<Frame>> frames = new HashMap<>();
            if (restPosition != null) {
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    Integer id = animationId(entry.getKey());
                    if (id == null || !entry.getValue().isJsonArray() || entry.getValue().getAsJsonArray().isEmpty()) {
                        continue;
                    }
                    List<Frame> list = new ArrayList<>();
                    for (JsonElement frame : entry.getValue().getAsJsonArray()) {
                        JsonObject f = frame.getAsJsonObject();
                        list.add(new Frame(f.get("time").getAsInt(), vector(f.get("position"), 0f),
                                vector(f.get("rotation"), 0f), vector(f.get("scale"), 1f)));
                    }
                    list.sort((a, b) -> Integer.compare(a.time(), b.time()));
                    frames.put(id, list);
                }
            }
            nodes.add(new Node(name, kind, parent, matrix(object), data, head, sky, block,
                    restPosition, restRotation, restScale, frames));
            index = nodes.size() - 1;
        }
        if (object.has("children")) {
            for (JsonElement child : object.getAsJsonArray("children")) {
                walk(child.getAsJsonObject(), index, nodes, names, false);
            }
        }
    }

    // "animation" is the first animation, "animation_2" the second and so on
    private static Integer animationId(String key) {
        if (key.equals("animation")) {
            return 1;
        }
        if (key.startsWith("animation_")) {
            try {
                return Integer.parseInt(key.substring("animation_".length()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static float[] matrix(JsonObject object) {
        float[] matrix = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        if (object.has("transforms")) {
            JsonArray values = object.getAsJsonArray("transforms");
            for (int i = 0; i < 16 && i < values.size(); i++) {
                matrix[i] = values.get(i).getAsFloat();
            }
        }
        return matrix;
    }

    private static float[] vector(JsonElement element, float fallback) {
        if (element == null || element.isJsonNull()) {
            return new float[]{fallback, fallback, fallback};
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
        }
        JsonObject object = element.getAsJsonObject();
        return new float[]{object.get("x").getAsFloat(), object.get("y").getAsFloat(), object.get("z").getAsFloat()};
    }

    private static boolean flag(JsonObject object, String key) {
        return object.has(key) && object.get(key).getAsBoolean();
    }

    private static String unique(String base, Map<String, Integer> names) {
        String name = base.isBlank() ? "part" : base.replace(' ', '_').toLowerCase(Locale.ROOT);
        int seen = names.merge(name, 1, Integer::sum);
        return seen == 1 ? name : name + "_" + seen;
    }

    private static BlockData blockData(String state) {
        try {
            return Bukkit.createBlockData(state.contains(":") ? state : "minecraft:" + state);
        } catch (IllegalArgumentException e) {
            // a block from a newer version than the server, or a typo in the file
            return Material.STONE.createBlockData();
        }
    }

    private static ItemStack item(Node node) {
        String id = node.data().replaceAll("\\[.*", "");
        Material material = Material.matchMaterial(id);
        ItemStack item = ItemStack.of(material == null || !material.isItem() ? Material.STONE : material);
        if (node.headTexture() != null && !node.headTexture().isBlank() && item.getItemMeta() instanceof SkullMeta skull) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", node.headTexture()));
            skull.setPlayerProfile(profile);
            item.setItemMeta(skull);
        }
        return item;
    }

    private static ItemDisplay.ItemDisplayTransform itemTransform(String data) {
        int start = data.indexOf("display=");
        if (start < 0) {
            return ItemDisplay.ItemDisplayTransform.NONE;
        }
        String value = data.substring(start + "display=".length()).replaceAll("[],].*", "").toUpperCase(Locale.ROOT);
        try {
            return ItemDisplay.ItemDisplayTransform.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ItemDisplay.ItemDisplayTransform.NONE;
        }
    }
}
