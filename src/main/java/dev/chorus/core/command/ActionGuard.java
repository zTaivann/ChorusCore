package dev.chorus.core.command;

import dev.chorus.core.economy.Economy;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.players.Playtime;
import dev.chorus.core.rules.Requirement;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

/** The requirement, cooldown and price checks every command runs. */
public final class ActionGuard {

    private static final String COOLDOWN_BYPASS = "chorus.bypass.cooldown";
    private static final String PRICE_BYPASS = "chorus.bypass.price";
    private static final BiPredicate<UUID, String> NO_KITS = (player, kit) -> false;

    private final Messages messages;
    private final Cooldowns cooldowns;
    private final Economy economy;

    private volatile BiPredicate<UUID, String> claimedKits = NO_KITS;

    public ActionGuard(Messages messages, Cooldowns cooldowns, Economy economy) {
        this.messages = messages;
        this.cooldowns = cooldowns;
        this.economy = economy;
    }

    /** Set by the kits module, so a command may require a kit; null when it stops. */
    public void claimedKits(@Nullable BiPredicate<UUID, String> lookup) {
        this.claimedKits = lookup == null ? NO_KITS : lookup;
    }

    /** The first requirement this player does not meet, or null when they meet them all. */
    public @Nullable Requirement unmet(Player player, List<Requirement> requirements) {
        if (requirements.isEmpty()) {
            return null;
        }
        BiPredicate<UUID, String> kits = claimedKits;
        return Requirement.firstUnmet(requirements, player, new Requirement.Context() {
            @Override
            public double balance() {
                return economy.balance(player);
            }

            @Override
            public long playtimeSeconds() {
                return TimeUnit.MILLISECONDS.toSeconds(Playtime.of(player));
            }

            @Override
            public boolean hasClaimed(String kit) {
                return kits.test(player.getUniqueId(), kit);
            }
        });
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
            cooldowns.start(rules.cooldownScope().owner(player),
                    Cooldowns.timer(command, rules.cooldownGroup()),
                    rules.cooldownSeconds(), System.currentTimeMillis());
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
        long left = cooldowns.remaining(rules.cooldownScope().owner(player),
                Cooldowns.timer(command, rules.cooldownGroup()), System.currentTimeMillis());
        if (left <= 0) {
            return false;
        }
        messages.send(player, rules.cooldownScope().waitMessage(), "time", Durations.format(left));
        return true;
    }
}
