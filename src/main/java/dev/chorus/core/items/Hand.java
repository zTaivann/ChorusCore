package dev.chorus.core.items;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Handing items to a player. */
public final class Hand {

    private Hand() {
    }

    /** Into their inventory, and whatever does not fit onto the ground at their feet. */
    public static void giveOrDrop(Player player, ItemStack... items) {
        for (ItemStack left : player.getInventory().addItem(items).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }
}
