package dev.chorus.core.shops;

import dev.chorus.core.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Moving items and money in the same breath.
 *
 * <p>Each of these does the money side only once the item side is certain, and puts the
 * money back if the item side then fails. A trade that half happened is worse than one that
 * did not.
 */
public final class ShopService {

    private final Economy economy;

    public ShopService(Economy economy) {
        this.economy = economy;
    }

    /** How many of this material the player is carrying, counting every stack. */
    public static int count(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material && plain(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Room for this many, counting part-used stacks. */
    public static int room(Player player, Material material, int wanted) {
        int space = 0;
        int stackSize = material.getMaxStackSize();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                space += stackSize;
            } else if (stack.getType() == material && plain(stack)) {
                space += Math.max(0, stackSize - stack.getAmount());
            }
            if (space >= wanted) {
                return wanted;
            }
        }
        return space;
    }

    /**
     * Takes exactly this many out of the inventory.
     *
     * <p>Only plain items: a renamed or enchanted one is somebody's, not stock, and selling
     * it by the stack would be a way to lose it for the price of the metal.
     */
    public static int take(Player player, Material material, int wanted) {
        Inventory inventory = player.getInventory();
        int left = wanted;
        ItemStack[] contents = inventory.getStorageContents();

        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material || !plain(stack)) {
                continue;
            }
            int taken = Math.min(left, stack.getAmount());
            left -= taken;
            if (taken == stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - taken);
            }
        }
        inventory.setStorageContents(contents);
        return wanted - left;
    }

    /** @return how many did not fit and were not given. */
    public static int give(Player player, Material material, int amount) {
        int left = amount;
        int stackSize = material.getMaxStackSize();
        while (left > 0) {
            int batch = Math.min(stackSize, left);
            Map<Integer, ItemStack> rejected = player.getInventory()
                    .addItem(new ItemStack(material, batch));
            if (!rejected.isEmpty()) {
                int refused = 0;
                for (ItemStack stack : rejected.values()) {
                    refused += stack.getAmount();
                }
                return left - batch + refused;
            }
            left -= batch;
        }
        return 0;
    }

    public Economy economy() {
        return economy;
    }

    /** An item with no name, lore, enchantments or damage on it. */
    private static boolean plain(ItemStack stack) {
        return !stack.hasItemMeta();
    }
}
