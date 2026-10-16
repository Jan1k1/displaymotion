package org.jan1k.displaymotion.paper;

import org.jan1k.displaymotion.Affine;
import org.jan1k.displaymotion.Decomposer;

final class Composer {

    private static final float BOX_MARGIN = 0.25f;
    // grow with some room so we dont send a new box every single frame while it pops in
    private static final float BOX_GROWTH = 1.25f;
    private static final float BELOW_TOLERANCE = 0.001f;

    private final Decomposer decomposer = new Decomposer();
    private final float[] root = new float[Affine.SIZE];
    private final float[] turn = new float[Affine.SIZE];
    private final float[] ownTurn = new float[Affine.SIZE];
    private final float[] toOwn = new float[Affine.SIZE];
    private final float[] ownLocal = new float[Affine.SIZE];
    private final float[] frame = new float[Affine.SIZE];
    private final float[] fading = new float[Affine.SIZE];
    private final float[] next = new float[Affine.SIZE];
    private final float[] local = new float[Affine.SIZE];
    private final float[] current = new float[Decomposer.SIZE];
    private final float[] ownCurrent = new float[Decomposer.SIZE];
    private final boolean[] dirty = new boolean[ModelBuilder.MAX_PARTS];

    void compose(DisplayState display, boolean everything) {
        Track whole = display.tracks[0];
        boolean rootMoved = everything || whole != null && whole.moved || display.size.moved;
        if (whole != null) {
            frameOf(whole, root);
        } else {
            Affine.identity(root, 0);
        }
        float size = display.size.now;
        if (size != 1f) {
            Affine.scale(root, 0, size, size, size);
        }
        display.size.moved = false;
        boolean posed = display.turn != 0f || display.tilt != 0f || display.drop != 0f;
        if (posed) {
            posed(display, display.turn, display.tilt, turn);
            Affine.multiply(turn, 0, root, 0, frame, 0);
            System.arraycopy(frame, 0, root, 0, Affine.SIZE);
        }
        // a wearer with another tilt (its hat runs ahead of the server) gets the whole difference, not only a turn
        boolean ownTilted = display.wearer != null && display.ownTilt != display.tilt;
        if (ownTilted) {
            if (!posed) {
                Affine.identity(turn, 0);
            }
            posed(display, display.ownTurn, display.ownTilt, ownTurn);
            invertRigid(turn, frame);
            Affine.multiply(ownTurn, 0, frame, 0, toOwn, 0);
        }

        PartState[] parts = display.parts;
        for (int i = 0; i < parts.length; i++) {
            PartState part = parts[i];
            Track track = display.tracks[i + 1];
            boolean needed = part.parent < 0 ? rootMoved : dirty[part.parent];
            needed |= everything || track != null && track.moved || part.size.moved;
            dirty[i] = needed;
            part.changes = 0;
            part.ownChanges = 0;
            part.size.moved = false;
            if (!needed) {
                continue;
            }

            float[] base = part.parent < 0 ? root : parts[part.parent].world;
            Affine.multiply(base, 0, part.rest, 0, part.world, 0);
            if (track != null) {
                frameOf(track, frame);
                Affine.multiply(part.world, 0, frame, 0, part.world, 0);
            }
            if (part.size.now != 1f) {
                Affine.scale(part.world, 0, part.size.now, part.size.now, part.size.now);
            }
            if (!part.isDisplay()) {
                continue;
            }
            System.arraycopy(part.world, 0, local, 0, Affine.SIZE);
            Affine.scale(local, 0, part.sizeX, part.sizeY, part.sizeZ);
            if (part.isBlock() && part.centered) {
                Affine.translate(local, 0, -0.5f, -0.5f, -0.5f);
            }
            decomposer.decompose(local, 0, current, 0);
            part.changes = changes(part.sent, current);
            if (part.changes != 0) {
                System.arraycopy(current, 0, part.sent, 0, Decomposer.SIZE);
            }
            if (cullBox(part, display.pitch == 0f)) {
                part.changes |= DisplayPacket.BOX;
            }
            if (ownTilted) {
                Affine.multiply(toOwn, 0, local, 0, ownLocal, 0);
                decomposer.decompose(ownLocal, 0, ownCurrent, 0);
                hideIf(display.ownHidden, ownCurrent);
                part.ownChanges = changes(part.own, ownCurrent) | part.changes & DisplayPacket.BOX;
                System.arraycopy(ownCurrent, 0, part.own, 0, Decomposer.SIZE);
            } else if (display.wearer != null) {
                turnAround(current, display.ownTurn - display.turn, ownCurrent);
                hideIf(display.ownHidden, ownCurrent);
                part.ownChanges = changes(part.own, ownCurrent) | part.changes & DisplayPacket.BOX;
                System.arraycopy(ownCurrent, 0, part.own, 0, Decomposer.SIZE);
            }
        }
        for (Track track : display.tracks) {
            if (track != null) {
                track.moved = false;
            }
        }
    }

    // a track in a crossfade is a mix of the old and the new matrix. blending matrices squashes a big turn
    // a little in the middle, for the usual short fades between two poses you cant see it
    private void frameOf(Track track, float[] out) {
        poseOf(track, out);
        Track from = track.from;
        if (from == null) {
            return;
        }
        poseOf(from, fading);
        mix(fading, out, track.weight(), out);
    }

    private void poseOf(Track track, float[] out) {
        track.frames.copyTo(track.frame, out, 0);
        if (track.between > 0f) {
            track.frames.copyTo(track.ahead, next, 0);
            mix(out, next, track.between, out);
        }
    }

    private static void mix(float[] a, float[] b, float w, float[] out) {
        for (int i = 0; i < Affine.SIZE; i++) {
            out[i] = a[i] + (b[i] - a[i]) * w;
        }
    }

    // same way round as the entity yaw, the client turns a display by minus its yaw. the tilt
    // turns around the pivot height and not the origin, so move the pivot there and back again.
    // the drop goes in before the turn, so a sneaking hat stays as far from the neck as a standing one
    private static void posed(DisplayState display, float turnDegrees, float tiltDegrees, float[] out) {
        Affine.fromPose(0f, 0f, 0f, tiltDegrees, -turnDegrees, 0f, 1f, 1f, 1f, out, 0);
        float pivot = display.pivot;
        float above = pivot - display.drop;
        out[9] = -out[3] * above;
        out[10] = pivot - out[4] * above;
        out[11] = -out[5] * above;
    }

    // a backpack the wearer would see in first person when it looks down shrinks to nothing, only for it
    private static void hideIf(boolean hidden, float[] values) {
        if (hidden) {
            values[Decomposer.SX] = 0f;
            values[Decomposer.SY] = 0f;
            values[Decomposer.SZ] = 0f;
        }
    }

    // turn plus move only, so the inverse is the transposed turn and the move turned back
    private static void invertRigid(float[] m, float[] out) {
        out[0] = m[0];
        out[1] = m[3];
        out[2] = m[6];
        out[3] = m[1];
        out[4] = m[4];
        out[5] = m[7];
        out[6] = m[2];
        out[7] = m[5];
        out[8] = m[8];
        out[9] = -(m[0] * m[9] + m[1] * m[10] + m[2] * m[11]);
        out[10] = -(m[3] * m[9] + m[4] * m[10] + m[5] * m[11]);
        out[11] = -(m[6] * m[9] + m[7] * m[10] + m[8] * m[11]);
    }

    // the wearer only sees the body turned by a bit more around Y. that turn sits in front of the whole
    // root (the pivot and drop are straight up), so it only turns the translation and the left rotation
    static void turnAround(float[] from, float degrees, float[] to) {
        System.arraycopy(from, 0, to, 0, Decomposer.SIZE);
        if (degrees == 0f) {
            return;
        }
        double half = Math.toRadians(-degrees) * 0.5;
        float s = (float) Math.sin(half);
        float c = (float) Math.cos(half);
        float x = from[Decomposer.TX];
        float z = from[Decomposer.TZ];
        to[Decomposer.TX] = (c * c - s * s) * x + 2f * s * c * z;
        to[Decomposer.TZ] = -2f * s * c * x + (c * c - s * s) * z;
        float lx = from[Decomposer.LX];
        float ly = from[Decomposer.LY];
        float lz = from[Decomposer.LZ];
        float lw = from[Decomposer.LW];
        to[Decomposer.LX] = c * lx + s * lz;
        to[Decomposer.LY] = c * ly + s * lw;
        to[Decomposer.LZ] = c * lz - s * lx;
        to[Decomposer.LW] = c * lw - s * ly;
    }

    // client only culls a display when width and height arent 0 and the box goes from the feet up
    // so a part only gets a box while all of it is above the spawn point. box never shrinks
    private boolean cullBox(PartState part, boolean upright) {
        if (!upright || part.kind == PartState.TEXT || part.billboard != 0) {
            return noBox(part);
        }
        float low = part.isBlock() ? 0f : -0.5f;
        float high = low + 1f;
        float radius = 0f;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            float cx = (corner & 1) == 0 ? low : high;
            float cy = (corner & 2) == 0 ? low : high;
            float cz = (corner & 4) == 0 ? low : high;
            float x = local[0] * cx + local[3] * cy + local[6] * cz + local[9];
            float y = local[1] * cx + local[4] * cy + local[7] * cz + local[10];
            float z = local[2] * cx + local[5] * cy + local[8] * cz + local[11];
            radius = Math.max(radius, x * x + z * z);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (minY < -BELOW_TOLERANCE) {
            return noBox(part);
        }
        float width = 2f * (float) Math.sqrt(radius) + BOX_MARGIN;
        float height = maxY + BOX_MARGIN;
        if (width <= part.cullWidth && height <= part.cullHeight) {
            return false;
        }
        part.cullWidth = Math.max(part.cullWidth, width * BOX_GROWTH);
        part.cullHeight = Math.max(part.cullHeight, height * BOX_GROWTH);
        return true;
    }

    private static boolean noBox(PartState part) {
        if (part.cullWidth == 0f && part.cullHeight == 0f) {
            return false;
        }
        part.cullWidth = 0f;
        part.cullHeight = 0f;
        return true;
    }

    private static int changes(float[] sent, float[] now) {
        int changes = 0;
        if (differs(sent, now, Decomposer.TX, Decomposer.TZ)) {
            changes |= DisplayPacket.TRANSLATION;
        }
        if (differs(sent, now, Decomposer.LX, Decomposer.LW)) {
            changes |= DisplayPacket.LEFT_ROTATION;
        }
        if (differs(sent, now, Decomposer.SX, Decomposer.SZ)) {
            changes |= DisplayPacket.SCALE;
        }
        if (differs(sent, now, Decomposer.RX, Decomposer.RW)) {
            changes |= DisplayPacket.RIGHT_ROTATION;
        }
        return changes;
    }

    private static boolean differs(float[] a, float[] b, int from, int to) {
        for (int i = from; i <= to; i++) {
            if (Float.compare(a[i], b[i]) != 0) {
                return true;
            }
        }
        return false;
    }
}
