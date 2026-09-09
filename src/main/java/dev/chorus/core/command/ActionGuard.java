package dev.chorus.core.command;

import dev.chorus.core.economy.Economy;
import dev.chorus.core.locale.Messages;
import org.bukkit.entity.Player;

/**
 * The cooldown and price checks every priced command runs.
 *
 * <p>Split in two on purpose: {@link #allow} answers "may they" before anything happens, and
 * {@link #charge} runs once it actually did. A teleport cancelled halfway therefore costs
 * nothing and starts no cooldown.
 */
public final class ActionGuard {

    private static final String COOLDOWN_BYPASS = "chorus.bypass.cooldown";
    private static final String PRICE_BYPASS = "chorus.bypass.price";

    private final Messages messages;
    private final Cooldowns cooldowns;
    private final Economy economy;

    public ActionGuard(Messages messages, Cooldowns cooldowns, Economy economy) {
        this.messages = messages;
        this.cooldowns = cooldowns;
        this.economy = economy;
    }

    public boolean allow(Player player, String command, CommandRules rules) {
        if (onCooldown(player, command, rules)) {
            return false;
        }

        double price = priceFor(player, rules);
        if (price > 0 && !economy.has(player, price)) {
            messages.send(player, "economy.insufficient",
                    "price", economy.format(price),
                    "balance", economy.format(economy.balance(player)));
            return false;
        }
        return true;
    }

    public void charge(Player player, String command, CommandRules rules) {
        if (rules.cooldownSeconds() > 0 && !player.hasPermission(COOLDOWN_BYPASS)) {
            cooldowns.start(player.getUniqueId(), command, rules.cooldownSeconds(), System.currentTimeMillis());
        }

        double price = priceFor(player, rules);
        if (price > 0 && economy.withdraw(player, price)) {
            messages.send(player, "economy.charged", "price", economy.format(price));
        }
    }

    /** Zero for a free command, a player who bypasses prices, or a server with no economy. */
    private double priceFor(Player player, CommandRules rules) {
        if (rules.price() <= 0 || !economy.enabled() || player.hasPermission(PRICE_BYPASS)) {
            return 0;
        }
        return rules.price();
    }

    private boolean onCooldown(Player player, String command, CommandRules rules) {
        if (rules.cooldownSeconds() <= 0 || player.hasPermission(COOLDOWN_BYPASS)) {
            return false;
        }
        long left = cooldowns.remaining(player.getUniqueId(), command, System.currentTimeMillis());
        if (left <= 0) {
            return false;
        }
        messages.send(player, "cooldown.wait", "time", Durations.format(left));
        return true;
    }
}
