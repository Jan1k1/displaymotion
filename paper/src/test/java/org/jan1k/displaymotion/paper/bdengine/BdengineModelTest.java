package org.jan1k.displaymotion.paper.bdengine;

import org.jan1k.displaymotion.Affine;
import org.jan1k.displaymotion.Frames;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BdengineModelTest {

    // a group scaled 1.5, a block in it, and a head that turns in the "nod" animation
    private static final String JSON = """
            [{
              "isCollection": true, "name": "Project", "transforms": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1],
              "listAnim": [{"id": 1, "name": "nod"}, {"id": 2, "name": "still"}],
              "children": [
                {"isCollection": true, "name": "Body Group", "transforms": [1.5,0,0,0, 0,1.5,0,0.25, 0,0,1.5,0, 0,0,0,1],
                 "children": [
                   {"isBlockDisplay": true, "name": "oak_stairs[facing=east,half=bottom]", "brightness": {"sky": 15, "block": 3},
                    "transforms": [0.5,0,0,-0.25, 0,0.5,0,0, 0,0,0.5,-0.25, 0,0,0,1]},
                   {"isItemDisplay": true, "name": "player_head[display=none]", "brightness": {"sky": 15, "block": 0},
                    "tagHead": {"Value": "abc"},
                    "transforms": [0.2,0,0,0.1, 0,0.2,0,0.5, 0,0,0.2,0.3, 0,0,0,1],
                    "defaultTransform": {"position": [1.0, 2.0, 0.5], "rotation": {"x": 0, "y": 0, "z": 0}, "scale": [0.2, 0.2, 0.2]},
                    "animation": [
                      {"time": 0, "position": {"x": 1.0, "y": 2.0, "z": 0.5}, "rotation": {"x": 0, "y": 0, "z": 0}, "scale": {"x": 0.2, "y": 0.2, "z": 0.2}},
                      {"time": 10, "position": {"x": 1.0, "y": 2.0, "z": 0.5}, "rotation": {"x": 0.4, "y": 0, "z": 0}, "scale": {"x": 0.2, "y": 0.2, "z": 0.2}},
                      {"time": 20, "position": {"x": 1.0, "y": 2.0, "z": 0.5}, "rotation": {"x": 0, "y": 0, "z": 0}, "scale": {"x": 0.2, "y": 0.2, "z": 0.2}}
                    ]}
                 ]}
              ]
            }]
            """;

    @Test
    void readsThePackedFileFormat() throws IOException {
        BdengineModel model = BdengineModel.parse(pack(JSON));

        assertEquals(List.of("body_group", "oak_stairs", "player_head"), model.parts());
        assertEquals(List.of("nod", "still"), model.animations());
        BdengineModel.Node stairs = model.node("oak_stairs");
        assertEquals(BdengineModel.Kind.BLOCK, stairs.kind());
        assertEquals("oak_stairs[facing=east,half=bottom]", stairs.data());
        assertEquals(3, stairs.block());
        assertFalse(stairs.animated());
        assertEquals("abc", model.node("player_head").headTexture());
        assertTrue(model.node("player_head").animated());
    }

    @Test
    void readsTheNewProjectFormat() throws IOException {
        byte[] scene = JSON.getBytes(StandardCharsets.UTF_8);
        byte[] name = "scene.json".getBytes(StandardCharsets.UTF_8);
        java.nio.ByteBuffer container = java.nio.ByteBuffer.allocate(4 + 1 + 4 + 2 + name.length + 4 + scene.length)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        container.put("PRJ2".getBytes(StandardCharsets.US_ASCII)).put((byte) 1).putInt(1)
                .putShort((short) name.length).put(name).putInt(scene.length).put(scene);
        ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(gzipped)) {
            gzip.write(container.array());
        }

        BdengineModel model = BdengineModel.read(gzipped.toByteArray());

        assertEquals(List.of("body_group", "oak_stairs", "player_head"), model.parts());
    }

    @Test
    void fixedPartTimesTheFirstFrameIsTheOriginalMatrix() throws IOException {
        BdengineModel.Node head = BdengineModel.parse(JSON).node("player_head");
        float[] fixed = new float[Affine.SIZE];
        float[] first = new float[Affine.SIZE];
        float[] result = new float[Affine.SIZE];
        rows(BdengineModel.withoutRest(head), fixed);
        Affine.fromPose(BdengineModel.restPose(head), first, 0);
        Affine.multiply(fixed, 0, first, 0, result, 0);

        float[] original = new float[Affine.SIZE];
        rows(head.matrix(), original);
        for (int i = 0; i < Affine.SIZE; i++) {
            assertEquals(original[i], result[i], 1e-5f, "cell " + i);
        }
    }

    @Test
    void animationsLoopAndBake() throws IOException {
        BdengineModel.Node head = BdengineModel.parse(JSON).node("player_head");

        Frames nod = Frames.bake(BdengineModel.clip(head, 1, 20));
        assertTrue(nod.isEndless());
        Frames still = Frames.bake(BdengineModel.clip(head, 2, 20));
        assertTrue(still.isEndless());
    }

    @Test
    void exampleChestLoads() throws IOException {
        String file = new String(getClass().getResourceAsStream("/chest.bdengine").readAllBytes(), StandardCharsets.US_ASCII);
        BdengineModel chest = BdengineModel.parse(file);

        assertEquals(List.of("open"), chest.animations());
        assertTrue(chest.node("lid").animated());
        assertTrue(chest.node("loot").animated());
        Frames.bake(BdengineModel.clip(chest.node("lid"), 1, 70));
        Frames.bake(BdengineModel.clip(chest.node("loot"), 1, 70));
    }

    private static void rows(float[] m, float[] out) {
        out[0] = m[0];
        out[1] = m[4];
        out[2] = m[8];
        out[3] = m[1];
        out[4] = m[5];
        out[5] = m[9];
        out[6] = m[2];
        out[7] = m[6];
        out[8] = m[10];
        out[9] = m[3];
        out[10] = m[7];
        out[11] = m[11];
    }

    private static String pack(String json) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
