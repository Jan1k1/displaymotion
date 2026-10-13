package org.jan1k.displaymotion;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class DisplayMotionPlugin extends JavaPlugin {

    private ItemDisplay display;

    @Override
    public void onEnable() {
        getLogger().info("displaymotion");
    }

    @Override
    public void onDisable() {
        if (display != null) {
            display.remove();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendPlainMessage("players only");
            return true;
        }

        // last one stays in the world until we remove it
        if (display != null) {
            display.remove();
        }

        display = player.getWorld().spawn(player.getLocation().add(0, 1, 0), ItemDisplay.class, entity -> {
            entity.setItemStack(ItemStack.of(Material.DIAMOND));
        });
        return true;
    }
}
