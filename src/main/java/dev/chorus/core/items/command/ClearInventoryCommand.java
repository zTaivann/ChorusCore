package dev.chorus.core.items.command;

import dev.chorus.core.backup.BackupReason;
import dev.chorus.core.backup.InventoryBackups;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Confirmations;
import dev.chorus.core.command.TargetedCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ClearInventoryCommand extends TargetedCommand {

    private final InventoryBackups backups;
    private final Confirmations confirmations;

    public ClearInventoryCommand(CommandSupport support, InventoryBackups backups,
                                 Confirmations confirmations) {
        super(support, "clearinventory", "chorus.items.clearinventory");
        this.backups = backups;
        this.confirmations = confirmations;
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!confirmations.confirmed(sender, "clear:" + target.getName(),
                target.equals(sender) ? "items.clear-confirm" : "items.clear-confirm-other",
                "player", target.getName())) {
            return;
        }
        if (!ready(sender)) {
            return;
        }

        backups.take(target, BackupReason.CLEAR, "", sender.getName());
        target.getInventory().clear();
        // clear() leaves worn armour and the offhand behind, which is never what is meant.
        target.getInventory().setArmorContents(null);
        target.getInventory().setItemInOffHand(null);

        settle(sender);
        announce(sender, target, "items.cleared", "items.cleared-other", "items.cleared-received");
    }
}
