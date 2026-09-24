package dev.chorus.core.economy.command;

import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.economy.Balances;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.players.PlayerProfiles;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * {@code /eco give|take|set|reset <player> [amount]}: the administrative side of the economy,
 * where money is created and destroyed rather than moved.
 */
public final class EcoCommand extends ChorusCommand {

    private static final List<String> ACTIONS = List.of("give", "take", "set", "reset");

    private final Economy economy;
    private final @Nullable Balances ledger;
    private final PlayerProfiles profiles;
    private final AuditLog audit;

    public EcoCommand(CommandSupport support, Economy economy, @Nullable Balances ledger,
                      PlayerProfiles profiles, AuditLog audit) {
        super(support, "eco", "chorus.economy.admin");
        this.economy = economy;
        this.ledger = ledger;
        this.profiles = profiles;
        this.audit = audit;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!economy.enabled()) {
            messages.send(sender, "economy.unavailable");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "economy.eco-usage");
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            messages.send(sender, "economy.eco-usage");
            return;
        }
        boolean needsAmount = !action.equals("reset");
        if (needsAmount && args.length < 3) {
            messages.send(sender, "economy.eco-usage");
            return;
        }

        double amount = needsAmount ? parseAmount(args[2]) : 0;
        // Only "set" has any use for zero: the other two would be asking for nothing.
        if (needsAmount && (amount < 0 || (amount == 0 && !action.equals("set")))) {
            messages.send(sender, "economy.invalid-amount");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        OfflinePlayer cached = sender.getServer().getPlayerExact(args[1]);
        if (cached == null) {
            cached = sender.getServer().getOfflinePlayerIfCached(args[1]);
        }
        if (cached != null && cached.hasPlayedBefore()) {
            apply(sender, action, cached, cached.getName() == null ? args[1] : cached.getName(),
                    amount);
            return;
        }

        profiles.find(args[1]).whenComplete((found, failure) -> {
            if (failure != null) {
                messages.send(sender, "error.storage");
                return;
            }
            if (found.isEmpty()) {
                messages.send(sender, "error.player-not-found", "player", args[1]);
                return;
            }
            apply(sender, action, sender.getServer().getOfflinePlayer(found.get().player()),
                    found.get().name(), amount);
        });
    }

    private void apply(CommandSender sender, String action, OfflinePlayer target, String name,
                       double amount) {
        boolean done = switch (action) {
            case "give" -> economy.deposit(target, amount);
            case "take" -> take(target, amount);
            case "reset" -> set(target, opening());
            default -> set(target, amount);
        };
        if (!done) {
            messages.send(sender, "economy.eco-failed", "player", name);
            return;
        }

        settle(sender);
        audit.record(sender, "eco-" + action, name, economy.format(amount));

        // Both keys written out in full, so the check for orphaned lines can find them.
        String toSender = switch (action) {
            case "give" -> "economy.eco-give-done";
            case "take" -> "economy.eco-take-done";
            case "reset" -> "economy.eco-reset-done";
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
                case "reset" -> "economy.eco-reset-received";
                default -> "economy.eco-set-received";
            };
            messages.send(online, toTarget,
                    "amount", economy.format(amount),
                    "balance", economy.format(economy.balance(target)));
        }
    }

    /** What a new player starts with, when this server's money is the plugin's own. */
    private double opening() {
        return ledger == null ? 0 : ledger.currency().startingBalance();
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

    private static double parseAmount(String raw) {
        return Numbers.cents(Numbers.money(raw));
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
