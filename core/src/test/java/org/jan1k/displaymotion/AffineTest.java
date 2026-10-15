package org.jan1k.displaymotion;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AffineTest {

    private static final float EPSILON = 1e-4f;

    private final Decomposer decomposer = new Decomposer();

    @Test
    void poseMatchesJoml() {
        Pose pose = Pose.IDENTITY.withOffset(1f, -2f, 3f).withRotation(30f, 75f, -20f).withScale(2f, 0.5f, 1.5f);
        float[] m = new float[Affine.SIZE];
        Affine.fromPose(pose, m, 0);

        Matrix4f expected = new Matrix4f()
                .translation(1f, -2f, 3f)
                .rotate(rotation(30f, 75f, -20f))
                .scale(2f, 0.5f, 1.5f);
        assertSame(expected, m);
    }

    @Test
    void multiplyMatchesJoml() {
        float[] a = new float[Affine.SIZE];
        float[] b = new float[Affine.SIZE];
        float[] out = new float[Affine.SIZE];
        Affine.fromPose(Pose.IDENTITY.withOffset(0f, 1f, 0f).withRotation(0f, 90f, 0f).withScale(1f, 2f, 1f), a, 0);
        Affine.fromPose(Pose.IDENTITY.withOffset(0.5f, 0f, 0f).withRotation(0f, 0f, 45f), b, 0);
        Affine.multiply(a, 0, b, 0, out, 0);

        Matrix4f expected = new Matrix4f().translation(0f, 1f, 0f).rotate(rotation(0f, 90f, 0f)).scale(1f, 2f, 1f)
                .mul(new Matrix4f().translation(0.5f, 0f, 0f).rotate(rotation(0f, 0f, 45f)));
        assertSame(expected, out);
    }

    @Test
    void plainPoseSplitsWithoutARightRotation() {
        float[] m = new float[Affine.SIZE];
        float[] parts = new float[Decomposer.SIZE];
        Affine.fromPose(Pose.IDENTITY.withOffset(1f, 2f, 3f).withRotation(10f, 200f, 5f).withScale(0.3f, 2f, 1f), m, 0);
        decomposer.decompose(m, 0, parts, 0);

        assertEquals(0.3f, parts[Decomposer.SX], EPSILON);
        assertEquals(2f, parts[Decomposer.SY], EPSILON);
        assertEquals(1f, parts[Decomposer.SZ], EPSILON);
        assertEquals(1f, Math.abs(parts[Decomposer.RW]), EPSILON);
        assertRebuilds(m, parts);
    }

    @Test
    void tiltedSpinStaysSmoothFrameToFrame() {
        float[] m = new float[Affine.SIZE];
        float[] parts = new float[Decomposer.SIZE];
        Quaternionf last = null;
        for (int frame = 0; frame < 80; frame++) {
            Affine.fromPose(Pose.IDENTITY.withRotation(45f, frame * 9f, 0f), m, 0);
            decomposer.decompose(m, 0, parts, 0);

            assertEquals(1f, Math.abs(parts[Decomposer.RW]), 1e-6f, "right rotation must stay still");
            assertEquals(1f, parts[Decomposer.SX], 1e-5f);
            Quaternionf left = new Quaternionf(parts[Decomposer.LX], parts[Decomposer.LY], parts[Decomposer.LZ], parts[Decomposer.LW]);
            if (last != null) {
                float step = (float) Math.toDegrees(2 * Math.acos(Math.min(1f, Math.abs(last.dot(left)))));
                assertEquals(9f, step, 0.01f, "frame " + frame);
            }
            last = left;
        }
    }

    @Test
    void stretchedParentWithTurnedChildStillRebuilds() {
        float[] parent = new float[Affine.SIZE];
        float[] child = new float[Affine.SIZE];
        float[] m = new float[Affine.SIZE];
        float[] parts = new float[Decomposer.SIZE];
        Affine.fromPose(Pose.IDENTITY.withScale(3f, 0.5f, 1f), parent, 0);
        Affine.fromPose(Pose.IDENTITY.withRotation(0f, 0f, 30f), child, 0);
        Affine.multiply(parent, 0, child, 0, m, 0);
        decomposer.decompose(m, 0, parts, 0);

        assertRebuilds(m, parts);
    }

    @Test
    void mirroredAndFlatMatricesRebuild() {
        float[] m = new float[Affine.SIZE];
        float[] parts = new float[Decomposer.SIZE];

        Affine.fromPose(Pose.IDENTITY.withRotation(0f, 40f, 0f).withScale(1f, 1f, -2f), m, 0);
        decomposer.decompose(m, 0, parts, 0);
        assertRebuilds(m, parts);

        Affine.fromPose(Pose.IDENTITY.withRotation(20f, 40f, 0f).withScale(1f, 0f, 2f), m, 0);
        decomposer.decompose(m, 0, parts, 0);
        assertRebuilds(m, parts);

        Affine.fromPose(Pose.IDENTITY.withRotation(20f, 40f, 0f).withScale(0f, 0f, 2f), m, 0);
        decomposer.decompose(m, 0, parts, 0);
        assertRebuilds(m, parts);

        Affine.fromPose(Pose.IDENTITY.withOffset(4f, 5f, 6f).withScale(0f), m, 0);
        decomposer.decompose(m, 0, parts, 0);
        assertRebuilds(m, parts);
        assertEquals(4f, parts[Decomposer.TX]);
    }

    @Test
    void randomChainsRebuild() {
        Random random = new Random(7);
        float[] a = new float[Affine.SIZE];
        float[] b = new float[Affine.SIZE];
        float[] m = new float[Affine.SIZE];
        float[] parts = new float[Decomposer.SIZE];
        for (int i = 0; i < 2000; i++) {
            Affine.fromPose(randomPose(random), a, 0);
            Affine.fromPose(randomPose(random), b, 0);
            Affine.multiply(a, 0, b, 0, m, 0);
            decomposer.decompose(m, 0, parts, 0);
            assertRebuilds(m, parts);
        }
    }

    @Test
    void blockTurnsAroundItsCenter() {
        Pose pose = Pose.IDENTITY.withOffset(1f, 2f, 3f).withRotation(10f, 45f, 5f).withScale(2f, 0.5f, 1f);
        float[] m = new float[Affine.SIZE];
        Affine.fromPose(pose, m, 0);
        Affine.translate(m, 0, -0.5f, -0.5f, -0.5f);

        Vector3f center = matrix(m).transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
        assertEquals(1f, center.x, EPSILON);
        assertEquals(2f, center.y, EPSILON);
        assertEquals(3f, center.z, EPSILON);
    }

    private static Pose randomPose(Random random) {
        return Pose.IDENTITY
                .withOffset(random.nextFloat() * 4 - 2, random.nextFloat() * 4 - 2, random.nextFloat() * 4 - 2)
                .withRotation(random.nextFloat() * 360, random.nextFloat() * 360, random.nextFloat() * 360)
                .withScale(0.1f + random.nextFloat() * 3, 0.1f + random.nextFloat() * 3, 0.1f + random.nextFloat() * 3);
    }

    private static Quaternionf rotation(float pitch, float yaw, float roll) {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(yaw), (float) Math.toRadians(pitch), (float) Math.toRadians(roll));
    }

    private static void assertRebuilds(float[] m, float[] parts) {
        Quaternionf left = new Quaternionf(parts[Decomposer.LX], parts[Decomposer.LY], parts[Decomposer.LZ], parts[Decomposer.LW]);
        Quaternionf right = new Quaternionf(parts[Decomposer.RX], parts[Decomposer.RY], parts[Decomposer.RZ], parts[Decomposer.RW]);
        assertEquals(1f, left.lengthSquared(), EPSILON);
        assertEquals(1f, right.lengthSquared(), EPSILON);
        Matrix4f rebuilt = new Matrix4f()
                .translation(parts[Decomposer.TX], parts[Decomposer.TY], parts[Decomposer.TZ])
                .rotate(left)
                .scale(parts[Decomposer.SX], parts[Decomposer.SY], parts[Decomposer.SZ])
                .rotate(right);
        assertSame(rebuilt, m);
    }

    private static Matrix4f matrix(float[] m) {
        return new Matrix4f(
                m[0], m[1], m[2], 0f,
                m[3], m[4], m[5], 0f,
                m[6], m[7], m[8], 0f,
                m[9], m[10], m[11], 1f);
    }

    private static void assertSame(Matrix4f expected, float[] m) {
        Matrix4f actual = matrix(m);
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 3; row++) {
                float scale = Math.max(1f, Math.abs(expected.get(col, row)));
                assertEquals(expected.get(col, row), actual.get(col, row), EPSILON * scale * 10,
                        "column " + col + " row " + row);
            }
        }
    }
}
