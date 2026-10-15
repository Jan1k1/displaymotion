package org.jan1k.displaymotion.paper;

/**
 * What the player that wears a riding display sees of it, see {@link MotionDisplay#selfView(SelfView)}.
 * Other players always see it. The server cannot tell first person from F5, so a choice counts for both.
 */
public enum SelfView {
    /** The wearer sees it. The default for {@link Follow#HEAD}. */
    SHOW,
    /**
     * Hidden from the wearer while they look down more than 35 degrees, the only time a backpack shows
     * in first person. The default for {@link Follow#BODY}.
     */
    HIDE_LOOKING_DOWN,
    /** The wearer never sees it. */
    HIDE
}
