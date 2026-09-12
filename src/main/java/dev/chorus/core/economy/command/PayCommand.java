package dev.chorus.core.economy.command;

import dev.chorus.core.api.event.ChorusPaymentEvent;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Confirmations;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.economy.EconomyService;
import dev.chorus.core.economy.EconomySettings;
import dev.chorus.core.economy.PaymentLog;
import dev.chorus.core.flags.PlayerFlag;
import dev.chorus.core.flags.PlayerFlagService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;

public final class PayCommand extends PlayerCommand {

    private final Economy economy;
    private final EconomyService service;
    private final PaymentLog log;
    private final PlayerFlagService flags;
    private final Confirmations confirmations;
    private final Logger logger;

    public PayCommand(CommandSupport support, Economy economy, EconomyService service,
                      PaymentLog log, PlayerFlagService flags, Confirmations confirmations,
                      Logger logger) {
        super(support, "pay", "chorus.economy.pay");
        this.economy = economy;
        this.service = service;
        this.log = log;
        this.flags = flags;
        this.confirmations = confirmations;
        this.logger = logger;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!economy.enabled()) {
            messages.send(player, "economy.unavailable");
            return;
        }
        if (args.length < 2) {
            messages.send(player, "economy.pay-usage");
            return;
        }

        Player target = player.getServer().getPlayerExact(args[0]);
        if (target == null || !player.canSee(target)) {
            messages.send(player, "error.player-not-found", "player", args[0]);
            return;
        }
        if (target.equals(player)) {
            messages.send(player, "economy.pay-self");
            return;
        }
        if (flags.isSet(target.getUniqueId(), PlayerFlag.PAYMENTS_BLOCKED)) {
            messages.send(player, "economy.pay-refused", "player", target.getName());
            return;
        }

        double amount = parseAmount(args[1]);
        if (amount <= 0) {
            messages.send(player, "economy.invalid-amount");
            return;
        }

        EconomySettings settings = service.settings();
        if (amount < settings.minimumPayment()) {
            messages.send(player, "economy.pay-minimum",
                    "amount", economy.format(settings.minimumPayment()));
            return;
        }
        if (settings.isAboveMaximum(amount)) {
            messages.send(player, "economy.pay-maximum",
                    "amount", economy.format(settings.maximumPayment()));
            return;
        }
        if (!economy.has(player, amount)) {
            messages.send(player, "economy.insufficient",
                    "price", economy.format(amount),
                    "balance", economy.format(economy.balance(player)));
            return;
        }
        if (settings.needsConfirming(amount) && !confirmations.confirmed(player,
                "pay:" + target.getName() + ':' + amount, "economy.pay-confirm",
                "player", target.getName(), "amount", economy.format(amount))) {
            return;
        }
        if (!ready(player)) {
            return;
        }

        ChorusPaymentEvent event = new ChorusPaymentEvent(player, target, amount);
        player.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        if (!economy.withdraw(player, amount)) {
            messages.send(player, "economy.transfer-failed");
            return;
        }
        if (!economy.deposit(target, amount)) {
            // The money already left the sender, so it has to go back before anyone is told.
            if (!economy.deposit(player, amount)) {
                logger.severe("Took " + amount + " from " + player.getName() + " for a payment to "
                        + target.getName() + ", but neither the payment nor the refund went "
                        + "through. That money needs restoring by hand.");
            }
            messages.send(player, "economy.transfer-failed");
            return;
        }

        settle(player);
        rules().feedback().play(target);
        log.record(player, target, amount);

        String formatted = economy.format(amount);
        messages.send(player, "economy.pay-sent", "player", target.getName(), "amount", formatted);
        messages.send(target, "economy.pay-received", "player", player.getName(), "amount", formatted);
    }

    /** Returns zero for anything that is not a sane, positive amount of money. */
    private static double parseAmount(String raw) {
        double amount;
        try {
            amount = Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException notANumber) {
            return 0;
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            return 0;
        }
        return Math.round(amount * 100.0) / 100.0;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player)) {
            return List.of();
        }

        return onlineNames(sender, args[args.length - 1], false);
    }
}
