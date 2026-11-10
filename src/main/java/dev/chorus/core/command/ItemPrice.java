package dev.chorus.core.command;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Items taken as part of a price, written {@code 'diamond 2'}. Only plain stacks count.
 *
 * @param material null for none
 */
public record ItemPrice(@Nullable Material material, int amount) {

    public static final ItemPrice NONE = new ItemPrice(null, 0);

    /** The main inventory, without armour or the off hand. */
    private static final int STORAGE_SLOTS = 36;

    public boolean none() {
        return material == null || amount <= 0;
    }

    /** {@code price-item}, keeping {@code base} when it is missing or cannot be read. */
    public static ItemPrice read(ConfigurationSection block, ItemPrice base, String owner,
                                 Consumer<String> onProblem) {
        if (!block.contains("price-item")) {
            return base;
        }
        String written = block.getString("price-item", "").trim();
        if (written.isEmpty()) {
            return NONE;
        }
        String[] parts = written.split("\\s+");
        Material material = Material.matchMaterial(parts[0]);
        int amount = parts.length > 1 ? Numbers.integer(parts[1], -1) : 1;
        if (material == null || material.isAir() || !material.isItem() || amount < 1
                || parts.length > 2) {
            onProblem.accept(owner + " has a price-item it cannot read: '" + written
                    + "'. Write the item and how many, like 'diamond 2'.");
            return base;
        }
        return new ItemPrice(material, amount);
    }

    /** As messages show it, the way /give and /worth name items. */
    public String itemName() {
        return material == null ? "" : material.name().toLowerCase(Locale.ROOT);
    }

    /** How many of them the main inventory holds. */
    public int countIn(PlayerInventory inventory) {
        int found = 0;
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (counts(stack)) {
                found += stack.getAmount();
            }
        }
        return found;
    }

    /** Takes them all, or nothing when there are not enough. */
    public boolean takeFrom(PlayerInventory inventory) {
        if (none() || countIn(inventory) < amount) {
            return false;
        }
        int left = amount;
        for (int slot = 0; slot < STORAGE_SLOTS && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!counts(stack)) {
                continue;
            }
            int taken = Math.min(left, stack.getAmount());
            left -= taken;
            if (taken == stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - taken);
                inventory.setItem(slot, stack);
            }
        }
        return true;
    }

    /** Puts them back, dropping at the player's feet whatever does not fit. */
    public void giveTo(Player player) {
        if (none()) {
            return;
        }
        player.getInventory().addItem(new ItemStack(material, amount)).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
    }

    private boolean counts(@Nullable ItemStack stack) {
        return stack != null && stack.getType() == material && !stack.hasItemMeta();
    }
}
