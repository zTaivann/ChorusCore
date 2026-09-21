package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Pours the loose piles in an inventory into each other until only full stacks are left. */
public final class StackCommand extends PlayerCommand {

    public StackCommand(CommandSupport support) {
        super(support, "stack", "chorus.items.stack");
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] slots = inventory.getStorageContents();
        int merged = 0;

        for (int source = slots.length - 1; source > 0; source--) {
            ItemStack loose = slots[source];
            if (loose == null || loose.getType().isAir() || loose.getAmount() >= loose.getMaxStackSize()) {
                continue;
            }

            for (int target = 0; target < source && loose.getAmount() > 0; target++) {
                ItemStack pile = slots[target];
                // isSimilar covers the name, lore, enchantments and damage.
                if (pile == null || !pile.isSimilar(loose)) {
                    continue;
                }
                int room = pile.getMaxStackSize() - pile.getAmount();
                if (room <= 0) {
                    continue;
                }

                int moved = Math.min(room, loose.getAmount());
                pile.setAmount(pile.getAmount() + moved);
                loose.setAmount(loose.getAmount() - moved);
                merged += moved;
            }

            slots[source] = loose.getAmount() > 0 ? loose : null;
        }

        if (merged == 0) {
            messages.send(player, "items.stack-nothing");
            return;
        }

        inventory.setStorageContents(slots);
        settle(player);
        messages.send(player, "items.stack", "amount", String.valueOf(merged));
    }
}
