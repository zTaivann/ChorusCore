package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Shared groundwork for the commands that act on whatever the player is holding. */
abstract class HeldItemCommand extends PlayerCommand {

    protected HeldItemCommand(CommandSupport support, String name, String permission) {
        super(support, name, permission);
    }

    protected final @Nullable ItemStack held(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            messages.send(player, "items.empty-hand");
            return null;
        }
        return item;
    }
}
