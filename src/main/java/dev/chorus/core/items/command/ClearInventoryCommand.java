package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.TargetedCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ClearInventoryCommand extends TargetedCommand {

    public ClearInventoryCommand(CommandSupport support) {
        super(support, "clearinventory", "chorus.items.clearinventory");
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!ready(sender)) {
            return;
        }

        target.getInventory().clear();
        // clear() leaves worn armour and the offhand behind, which is never what is meant.
        target.getInventory().setArmorContents(null);
        target.getInventory().setItemInOffHand(null);

        settle(sender);
        announce(sender, target, "items.cleared", "items.cleared-other", "items.cleared-received");
    }
}
