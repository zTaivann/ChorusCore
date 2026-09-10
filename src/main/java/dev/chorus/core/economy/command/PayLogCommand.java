package dev.chorus.core.economy.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.economy.Payment;
import dev.chorus.core.economy.PaymentLog;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/** {@code /paylog [player]}: the most recent transfers, newest first. */
public final class PayLogCommand extends ChorusCommand {

    private static final String OTHERS_PERMISSION = "chorus.economy.paylog.others";

    private final PaymentLog log;
    private final Economy economy;

    public PayLogCommand(CommandSupport support, PaymentLog log, Economy economy) {
        super(support, "paylog", "chorus.economy.paylog");
        this.log = log;
        this.economy = economy;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!log.enabled()) {
            messages.send(sender, "economy.paylog-disabled");
            return;
        }

        UUID subject;
        if (args.length > 0) {
            if (!sender.hasPermission(OTHERS_PERMISSION)) {
                messages.send(sender, "error.no-permission");
                return;
            }
            // "*" is the only way to read the whole server's log, and it needs the same
            // permission as reading somebody else's.
            if (args[0].equals("*")) {
                subject = null;
            } else {
                OfflinePlayer target = resolve(sender, args[0]);
                if (target == null) {
                    return;
                }
                subject = target.getUniqueId();
            }
        } else if (sender instanceof Player self) {
            subject = self.getUniqueId();
        } else {
            subject = null;
        }
        if (!ready(sender)) {
            return;
        }

        settle(sender);
        log.recent(subject, log.pageSize()).thenAccept(payments -> show(sender, payments));
    }

    private void show(CommandSender sender, List<Payment> payments) {
        if (payments.isEmpty()) {
            messages.send(sender, "economy.paylog-empty");
            return;
        }

        long now = System.currentTimeMillis();
        messages.send(sender, "economy.paylog-header", "count", String.valueOf(payments.size()));
        for (Payment payment : payments) {
            messages.send(sender, "economy.paylog-entry",
                    "payer", payment.payerName(),
                    "payee", payment.payeeName(),
                    "amount", economy.format(payment.amount()),
                    "ago", Durations.format(Math.max(0, now - payment.paidAt())));
        }
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(OTHERS_PERMISSION)) {
            return List.of();
        }
        return onlineNames(sender, args[0], true);
    }
}
