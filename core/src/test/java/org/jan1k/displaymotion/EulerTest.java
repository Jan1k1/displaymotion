package org.jan1k.displaymotion;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EulerTest {

    @Test
    void zyxBecomesTheSameRotationInYxz() {
        Random random = new Random(3);
        for (int i = 0; i < 2000; i++) {
            float x = random.nextFloat() * 360f - 180f;
            float y = random.nextFloat() * 360f - 180f;
            float z = random.nextFloat() * 360f - 180f;
            assertSameRotation(x, y, z);
        }
    }

    @Test
    void xyzBecomesTheSameRotationInYxz() {
        Random random = new Random(5);
        for (int i = 0; i < 2000; i++) {
            float x = random.nextFloat() * 360f - 180f;
            float y = random.nextFloat() * 360f - 180f;
            float z = random.nextFloat() * 360f - 180f;
            Quaternionf expected = new Quaternionf().rotationXYZ(rad(x), rad(y), rad(z));
            float[] yxz = Euler.fromXyz(x, y, z);
            Quaternionf actual = new Quaternionf().rotationYXZ(rad(yxz[1]), rad(yxz[0]), rad(yxz[2]));
            assertEquals(1f, Math.abs(expected.dot(actual)), 1e-4f, "x " + x + " y " + y + " z " + z);
        }
    }

    @Test
    void straightUpStillWorks() {
        assertSameRotation(0f, 90f, 30f);
        assertSameRotation(45f, -90f, 10f);
    }

    @Test
    void singleAxisStaysTheSame() {
        float[] onlyY = Euler.fromZyx(0f, 40f, 0f);
        assertEquals(40f, onlyY[1], 1e-4f);
        float[] onlyZ = Euler.fromZyx(0f, 0f, -25f);
        assertEquals(-25f, onlyZ[2], 1e-4f);
    }

    private static void assertSameRotation(float x, float y, float z) {
        Quaternionf expected = new Quaternionf().rotationZYX(rad(z), rad(y), rad(x));
        float[] yxz = Euler.fromZyx(x, y, z);
        Quaternionf actual = new Quaternionf().rotationYXZ(rad(yxz[1]), rad(yxz[0]), rad(yxz[2]));
        assertEquals(1f, Math.abs(expected.dot(actual)), 1e-4f, "x " + x + " y " + y + " z " + z);
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
