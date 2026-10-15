package org.jan1k.displaymotion.paper;

// how a riding display sits on its vehicle: turn around Y, tilt around X at a pivot height, and a drop.
// pivot and drop are blocks from the passenger point, the tilt is only used with a pivot
record RidePose(float yaw, float tilt, float pivot, float drop) {

    static RidePose turn(float yaw) {
        return new RidePose(yaw, 0f, 0f, 0f);
    }
}
