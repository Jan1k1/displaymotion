package org.jan1k.displaymotion.packetevents.blockbench;

import org.bukkit.Material;
import org.jan1k.displaymotion.Affine;
import org.jan1k.displaymotion.Pose;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockbenchModelTest {

    private static final String RED = texture(0xAF1805);

    // format 5: groups in their own list, the outliner only points at them
    private static final String NEW_FORMAT = """
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "resolution": {"width": 16, "height": 16},
              "elements": [
                {"name": "arm", "uuid": "c1", "type": "cube", "from": [8, 0, 0], "to": [10, 2, 2], "origin": [8, 0, 8],
                 "faces": {"north": {"uv": [0, 0, 16, 16], "texture": 0}, "up": {"uv": [0, 0, 16, 16], "texture": 0}}},
                {"name": "arm", "uuid": "c2", "type": "cube", "from": [0, 0, 0], "to": [16, 1, 16], "origin": [0, 0, 0],
                 "rotation": [0, 45, 0], "faces": {}}
              ],
              "groups": [
                {"name": "body", "uuid": "g1", "origin": [8, 0, 8], "rotation": [0, 90, 0]}
              ],
              "outliner": [
                {"uuid": "g1", "children": ["c1"]},
                "c2"
              ],
              "textures": [{"name": "red.png", "id": "0", "source": "%s"}],
              "animations": [
                {"name": "wave", "loop": "loop", "length": 1,
                 "animators": {"g1": {"name": "body", "type": "bone", "keyframes": [
                   {"channel": "rotation", "time": 0, "interpolation": "linear", "data_points": [{"x": "0", "y": "0", "z": "0"}]},
                   {"channel": "rotation", "time": 0.5, "interpolation": "linear", "data_points": [{"x": "0", "y": "0", "z": "30"}]},
                   {"channel": "rotation", "time": 1, "interpolation": "linear", "data_points": [{"x": "0", "y": "0", "z": "0"}]}
                 ]}}}
              ]
            }
            """.formatted(RED);

    // format 4: the outliner holds the whole group
    private static final String OLD_FORMAT = """
            {
              "meta": {"format_version": "4.5", "model_format": "free"},
              "elements": [
                {"name": "head", "uuid": "c1", "from": [-4, 24, -4], "to": [4, 32, 4], "origin": [0, 24, 0], "faces": {}}
              ],
              "outliner": [
                {"name": "head", "uuid": "g1", "origin": [0, 24, 0], "children": ["c1"]}
              ]
            }
            """;

    @Test
    void readsGroupsCubesAndAnimationsFromTheNewFormat() {
        BlockbenchModel model = BlockbenchModel.parse(NEW_FORMAT);

        assertEquals(List.of("body", "arm", "arm_2"), model.parts());
        assertEquals(List.of("wave"), model.animations());
        assertEquals(Material.REDSTONE_BLOCK, model.material("arm"));
    }

    @Test
    void readsTheOldFormatToo() {
        BlockbenchModel model = BlockbenchModel.parse(OLD_FORMAT);

        assertEquals(List.of("head", "head_2"), model.parts());
        assertTrue(model.animations().isEmpty());
    }

    @Test
    void cubeEndsUpWhereBlockbenchDrawsIt() {
        BlockbenchModel model = BlockbenchModel.parse(NEW_FORMAT);
        // center (9, 1, 1) turned 90 around y about the group origin (8, 0, 8) lands on (1, 1, 7)
        float[] group = new float[Affine.SIZE];
        float[] cube = new float[Affine.SIZE];
        float[] world = new float[Affine.SIZE];
        Affine.fromPose(model.restPose("body", 1f), group, 0);
        Affine.fromPose(model.restPose("arm", 1f).withScale(1f), cube, 0);
        Affine.multiply(group, 0, cube, 0, world, 0);

        assertEquals(1f / 16f, world[9], 1e-5f);
        assertEquals(1f / 16f, world[10], 1e-5f);
        assertEquals(7f / 16f, world[11], 1e-5f);
    }

    @Test
    void sizeIsInBlocksAndScales() {
        BlockbenchModel model = BlockbenchModel.parse(NEW_FORMAT);
        Pose arm = model.restPose("arm", 2f);

        assertEquals(0.25f, arm.scaleX(), 1e-6f);
        assertEquals(0.25f, arm.scaleY(), 1e-6f);
    }

    @Test
    void keyframesBlendLinearStepAndSmooth() {
        List<BlockbenchModel.Key> linear = List.of(
                new BlockbenchModel.Key(0f, 0f, 0f, 0f, "linear"),
                new BlockbenchModel.Key(1f, 10f, 20f, 30f, "linear"));
        assertEquals(5f, BlockbenchModel.sample(linear, 0.5f, 0f)[0], 1e-5f);

        List<BlockbenchModel.Key> step = List.of(
                new BlockbenchModel.Key(0f, 0f, 0f, 0f, "step"),
                new BlockbenchModel.Key(1f, 10f, 10f, 10f, "linear"));
        assertEquals(0f, BlockbenchModel.sample(step, 0.9f, 0f)[0], 1e-5f);

        List<BlockbenchModel.Key> smooth = List.of(
                new BlockbenchModel.Key(0f, 0f, 0f, 0f, "catmullrom"),
                new BlockbenchModel.Key(1f, 10f, 10f, 10f, "catmullrom"));
        float middle = BlockbenchModel.sample(smooth, 0.5f, 0f)[0];
        assertEquals(5f, middle, 0.01f);
        assertFalse(BlockbenchModel.sample(smooth, 0.25f, 0f)[0] == 2.5f);

        assertEquals(1f, BlockbenchModel.sample(List.of(), 0.3f, 1f)[1]);
    }

    @Test
    void exampleRobotLoadsWithTheRightBlocks() throws Exception {
        String json = new String(getClass().getResourceAsStream("/robot.bbmodel").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        BlockbenchModel robot = BlockbenchModel.parse(json);

        assertEquals(List.of("wave", "walk"), robot.animations());
        assertEquals(Material.WHITE_WOOL, robot.material("head_2"));
        assertEquals(Material.LIGHT_BLUE_WOOL, robot.material("body_2"));
        assertEquals(Material.BLACK_CONCRETE, robot.material("eye_left"));
        assertEquals(Material.REDSTONE_BLOCK, robot.material("light"));
        for (String clip : robot.animations()) {
            for (String part : robot.parts()) {
                org.jan1k.displaymotion.Frames.bake(robot.animation(clip, part, 1f));
            }
        }
    }

    @Test
    void paletteFindsObviousColors() {
        assertEquals(Material.REDSTONE_BLOCK, BlockPalette.closest(0xAF1805));
        assertEquals(Material.SNOW_BLOCK, BlockPalette.closest(0xFFFFFF));
        assertEquals(Material.BLACK_CONCRETE, BlockPalette.closest(0x000000));
        assertEquals(Material.OAK_PLANKS, BlockPalette.closest(0xA2834F));
    }

    private static String texture(int rgb) {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                image.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
