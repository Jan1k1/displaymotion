package org.jan1k.displaymotion.paper;

import org.bukkit.entity.Player;

/**
 * A player clicked a button of a {@link Menu}.
 * @param player who clicked
 * @param menu the menu, so a button can close it or change a label
 * @param button the id of the button
 * @param leftClick true for a left click (attack), false for a right click
 */
public record MenuClick(Player player, Menu menu, String button, boolean leftClick) {

    /** True for a right click. */
    public boolean rightClick() {
        return !leftClick;
    }
}
