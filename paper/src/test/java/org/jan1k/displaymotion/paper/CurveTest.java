package org.jan1k.displaymotion.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurveTest {

    @Test
    void straightPointsGiveAStraightLineAtEvenSpeed() {
        Curve curve = Curve.through(new double[]{0, 0, 0, 2, 0, 0, 10, 0, 0});

        assertEquals(10.0, curve.length(), 1e-6);
        assertArrayEquals(new double[]{0, 0, 0}, curve.at(0), 1e-9);
        assertArrayEquals(new double[]{2.5, 0, 0}, curve.at(0.25), 1e-3);
        assertArrayEquals(new double[]{5, 0, 0}, curve.at(0.5), 1e-3);
        assertArrayEquals(new double[]{10, 0, 0}, curve.at(1), 1e-9);
        assertArrayEquals(new double[]{10, 0, 0}, curve.end(), 0);
    }

    @Test
    void goesThroughEveryPoint() {
        double[] points = {0, 0, 0, 4, 2, 4, 8, 0, 0, 4, -1, -4, 0, 0, 0.5};
        Curve curve = Curve.through(points);

        for (int p = 0; p < points.length; p += 3) {
            double closest = Double.MAX_VALUE;
            for (int i = 0; i <= 2000; i++) {
                double[] at = curve.at(i / 2000.0);
                double dx = at[0] - points[p];
                double dy = at[1] - points[p + 1];
                double dz = at[2] - points[p + 2];
                closest = Math.min(closest, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            assertTrue(closest < 0.02, "point " + p / 3 + " missed by " + closest);
        }
    }

    @Test
    void unevenPointsDoNotOvershoot() {
        // two points close together and one far away, the plain catmull rom swings out past x 10 here
        Curve curve = Curve.through(new double[]{0, 0, 0, 0.2, 0, 0.2, 10, 0, 0});
        for (int i = 0; i <= 100; i++) {
            double[] at = curve.at(i / 100.0);
            assertTrue(at[0] <= 10.0 + 1e-6 && at[0] >= -1e-6, "x went to " + at[0]);
        }
    }

    @Test
    void repeatedPointsAndOnePointAreFine() {
        Curve curve = Curve.through(new double[]{1, 2, 3, 1, 2, 3, 5, 2, 3, 5, 2, 3});
        for (int i = 0; i <= 10; i++) {
            for (double v : curve.at(i / 10.0)) {
                assertFalse(Double.isNaN(v));
            }
        }
        assertArrayEquals(new double[]{5, 2, 3}, curve.end(), 0);

        Curve still = Curve.through(new double[]{1, 2, 3, 1, 2, 3});
        assertEquals(0.0, still.length());
        assertArrayEquals(new double[]{1, 2, 3}, still.at(0.7), 0);
    }

    @Test
    void elasticAndBackGoOnStraightPastTheEnds() {
        Curve curve = Curve.through(new double[]{0, 0, 0, 10, 0, 0});

        assertArrayEquals(new double[]{11, 0, 0}, curve.at(1.1), 1e-6);
        assertArrayEquals(new double[]{-1, 0, 0}, curve.at(-0.1), 1e-6);
    }
}
