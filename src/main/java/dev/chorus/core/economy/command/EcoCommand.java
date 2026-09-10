package dev.chorus.core.economy.command;

import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * {@code /eco give|take|set <player> <amount>}: the administrative side of the economy,
 * where money is created and destroyed rather than moved.
 */
public final class EcoCommand extends ChorusCommand {

    private static final List<String> ACTIONS = List.of("give", "take", "set");

    private final Economy economy;
    private final AuditLog audit;

    public EcoCommand(CommandSupport support, Economy economy, AuditLog audit) {
        super(support, "eco", "chorus.economy.admin");
        this.economy = economy;
        this.audit = audit;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!economy.enabled()) {
            messages.send(sender, "economy.unavailable");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "economy.eco-usage");
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            messages.send(sender, "economy.eco-usage");
            return;
        }

        OfflinePlayer target = resolve(sender, args[1]);
        if (target == null) {
            return;
        }

        double amount = parseAmount(args[2]);
        // Only "set" has any use for zero: the other two would be asking for nothing.
        if (amount < 0 || (amount == 0 && !action.equals("set"))) {
            messages.send(sender, "economy.invalid-amount");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        String name = target.getName() == null ? args[1] : target.getName();
        boolean done = switch (action) {
            case "give" -> economy.deposit(target, amount);
            case "take" -> take(target, amount);
            default -> set(target, amount);
        };
        if (!done) {
            messages.send(sender, "economy.eco-failed", "player", name);
            return;
        }

        settle(sender);
        audit.record(sender, "eco-" + action, name, economy.format(amount));

        // Both keys are written out in full rather than pasted together from the action, so
        // the check that nothing in messages.yml is orphaned can actually find them.
        String toSender = switch (action) {
            case "give" -> "economy.eco-give-done";
            case "take" -> "economy.eco-take-done";
            default -> "economy.eco-set-done";
        };
        messages.send(sender, toSender,
                "player", name,
                "amount", economy.format(amount),
                "balance", economy.format(economy.balance(target)));

        if (target instanceof Player online && !online.equals(sender)) {
            String toTarget = switch (action) {
                case "give" -> "economy.eco-give-received";
                case "take" -> "economy.eco-take-received";
                default -> "economy.eco-set-received";
            };
            messages.send(online, toTarget,
                    "amount", economy.format(amount),
                    "balance", economy.format(economy.balance(target)));
        }
    }

    /** Taking more than someone has empties the account rather than failing or going negative. */
    private boolean take(OfflinePlayer target, double amount) {
        double balance = economy.balance(target);
        return economy.withdraw(target, Math.min(amount, balance));
    }

    private boolean set(OfflinePlayer target, double amount) {
        double balance = economy.balance(target);
        if (amount > balance) {
            return economy.deposit(target, amount - balance);
        }
        if (amount < balance) {
            return economy.withdraw(target, balance - amount);
        }
        return true;
    }

    /**
     * Online players first, then anyone the server already has on file. Never a lookup with
     * Mojang: that is a web request, and this command runs on the server thread.
     */
    private @Nullable OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = sender.getServer().getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = sender.getServer().getOfflinePlayerIfCached(name);
        if (offline == null || !offline.hasPlayedBefore()) {
            messages.send(sender, "error.player-not-found", "player", name);
            return null;
        }
        return offline;
    }

    private static double parseAmount(String raw) {
        double amount;
        try {
            amount = Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException notANumber) {
            return -1;
        }
        if (!Double.isFinite(amount) || amount < 0) {
            return -1;
        }
        return Math.round(amount * 100.0) / 100.0;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], ACTIONS);
        }
        return args.length == 2 ? onlineNames(sender, args[1], true) : List.of();
    }
}
