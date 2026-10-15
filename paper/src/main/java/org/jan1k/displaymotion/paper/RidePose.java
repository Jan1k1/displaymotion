package org.jan1k.displaymotion.paper;

// how a riding display sits on its vehicle: turn around Y, tilt around X at a pivot height, and a drop.
// pivot is blocks from the passenger point, the tilt is only used with a pivot. the drop moves it down before
// the turn and tilt, so it is how much lower the pivot sits then the one the model was made for.
// own and ownTilt are for the player that wears it: its game shows its own body turned a bit different,
// and its own head moves before the server knows, so a hat for it is a bit ahead
// ownHidden shrinks it to nothing for the wearer only, for a backpack while it looks down
record RidePose(float yaw, float tilt, float pivot, float drop, float own, float ownTilt, boolean ownHidden) {

    RidePose(float yaw, float tilt, float pivot, float drop, float own, float ownTilt) {
        this(yaw, tilt, pivot, drop, own, ownTilt, false);
    }

    RidePose(float yaw, float tilt, float pivot, float drop, float own) {
        this(yaw, tilt, pivot, drop, own, tilt, false);
    }

    RidePose(float yaw, float tilt, float pivot, float drop) {
        this(yaw, tilt, pivot, drop, yaw, tilt, false);
    }

    RidePose hiddenForWearer(boolean hidden) {
        return new RidePose(yaw, tilt, pivot, drop, own, ownTilt, hidden);
    }

    static RidePose turn(float yaw) {
        return new RidePose(yaw, 0f, 0f, 0f);
    }
}
