package org.jan1k.displaymotion.paper;

/** Which way a riding display turns, see {@link MotionDisplay#ride(org.bukkit.entity.Entity, Follow)}. */
public enum Follow {
    /** Turns with the body. Good for backpacks, wings and things on the back. Not living entities turn like {@link #HEAD}. */
    BODY,
    /** Turns with where the entity looks. Good for hats. Only left and right, it does not tilt up and down. */
    HEAD,
    /** Keeps the turn it had when it started to ride. */
    NONE
}
