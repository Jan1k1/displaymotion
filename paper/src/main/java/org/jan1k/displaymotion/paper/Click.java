package org.jan1k.displaymotion.paper;

import org.bukkit.entity.Player;

/**
 * A player clicked a hitbox of a display.
 * @param player who clicked
 * @param display the display that owns the hitbox
 * @param hitbox the name of the hitbox, {@code main} for {@link MotionDisplay#hitbox(float, float)}
 * @param leftClick true for a left click (attack), false for a right click
 */
public record Click(Player player, MotionDisplay display, String hitbox, boolean leftClick) {

    /** True for a right click. */
    public boolean rightClick() {
        return !leftClick;
    }
}
