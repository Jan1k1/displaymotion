package org.jan1k.displaymotion.paper;

/** Which way a riding display turns, see {@link MotionDisplay#ride(org.bukkit.entity.Entity, Follow)}. */
public enum Follow {
    /**
     * Turns with the body. Good for backpacks, wings and things on the back. On a player it also leans forward
     * when they sneak. Not living entities turn like {@link #HEAD}.
     */
    BODY,
    /**
     * Moves like the head. Good for hats. On a player it turns and tilts around the neck and goes down
     * when they sneak. Other entities only turn left and right.
     */
    HEAD,
    /** Keeps the turn it had when it started to ride. */
    NONE
}
