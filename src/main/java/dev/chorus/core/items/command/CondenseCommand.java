package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.items.ItemService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/** Packs loose materials into their block form. */
public final class CondenseCommand extends PlayerCommand {

    private final ItemService items;

    public CondenseCommand(CommandSupport support, ItemService items) {
        super(support, "condense", "chorus.items.condense");
        this.items = items;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int packed = 0;
        for (Map.Entry<Material, Material> recipe : items.settings().condenseRecipes().entrySet()) {
            int loose = count(inventory, recipe.getKey());
            int blocks = loose / 9;
            if (blocks == 0) {
                continue;
            }
            take(inventory, recipe.getKey(), blocks * 9);
            give(player, new ItemStack(recipe.getValue(), blocks));
            packed += blocks;
        }

        if (packed == 0) {
            messages.send(player, "items.condense-nothing");
            return;
        }
        settle(player);
        messages.send(player, "items.condense-done", "count", String.valueOf(packed));
    }

    private static int count(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isPlain(item, material)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private static void take(PlayerInventory inventory, Material material, int wanted) {
        ItemStack[] contents = inventory.getStorageContents();
        int left = wanted;
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isPlain(item, material)) {
                continue;
            }
            int taken = Math.min(left, item.getAmount());
            left -= taken;
            if (taken == item.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - taken);
                inventory.setItem(slot, item);
            }
        }
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static boolean isPlain(ItemStack item, Material material) {
        return item != null && item.getType() == material && !item.hasItemMeta();
    }
}
