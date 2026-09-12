package dev.chorus.core.economy.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Somebody's balance, whether or not they are online. */
public final class BalanceCommand extends ChorusCommand {

    private static final String OTHERS = "chorus.economy.balance.others";

    private final Economy economy;

    public BalanceCommand(CommandSupport support, Economy economy) {
        super(support, "balance", "chorus.economy.balance");
        this.economy = economy;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!economy.enabled()) {
            messages.send(sender, "economy.unavailable");
            return;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                messages.send(sender, "error.players-only");
                return;
            }
            if (!ready(sender)) {
                return;
            }
            settle(sender);
            messages.send(sender, "economy.balance", "balance", economy.format(economy.balance(self)));
            return;
        }

        if (!sender.hasPermission(OTHERS)) {
            messages.send(sender, "error.no-permission");
            return;
        }
        OfflinePlayer target = known(sender, args[0]);
        if (target == null) {
            return;
        }
        if (!ready(sender)) {
            return;
        }

        settle(sender);
        messages.send(sender, "economy.balance-other",
                "player", target.getName() == null ? args[0] : target.getName(),
                "balance", economy.format(economy.balance(target)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(OTHERS)) {
            return List.of();
        }
        return onlineNames(sender, args[0], true);
    }
}
