package dev.chorus.core.economy.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.TargetedCommand;
import dev.chorus.core.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BalanceCommand extends TargetedCommand {

    private final Economy economy;

    public BalanceCommand(CommandSupport support, Economy economy) {
        super(support, "balance", "chorus.economy.balance");
        this.economy = economy;
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!economy.enabled()) {
            messages.send(sender, "economy.unavailable");
            return;
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        String balance = economy.format(economy.balance(target));
        if (target.equals(sender)) {
            messages.send(sender, "economy.balance", "balance", balance);
        } else {
            messages.send(sender, "economy.balance-other",
                    "player", target.getName(), "balance", balance);
        }
    }
}
