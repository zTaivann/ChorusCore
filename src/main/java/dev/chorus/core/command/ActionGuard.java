package dev.chorus.core.command;

import dev.chorus.core.economy.Economy;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.Schedulers;
import dev.chorus.core.players.Playtime;
import dev.chorus.core.rules.Requirement;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

/** The requirement, cooldown, daily limit and price checks every command runs. */
public final class ActionGuard {

    public static final Runnable NOTHING_TO_REFUND = () -> { };

    private static final String COOLDOWN_BYPASS = "chorus.bypass.cooldown";
    private static final String PRICE_BYPASS = "chorus.bypass.price";
    private static final BiPredicate<UUID, String> NO_KITS = (player, kit) -> false;

    private final Messages messages;
    private final Cooldowns cooldowns;
    private final DailyUses uses;
    private final Economy economy;
    private final Schedulers schedulers;

    private final PrepaidPrices prepaid = new PrepaidPrices();

    private volatile BiPredicate<UUID, String> claimedKits = NO_KITS;

    public ActionGuard(Messages messages, Cooldowns cooldowns, DailyUses uses, Economy economy,
                       Schedulers schedulers) {
        this.messages = messages;
        this.cooldowns = cooldowns;
        this.uses = uses;
        this.economy = economy;
        this.schedulers = schedulers;
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
        if (!hasUsesLeft(player, Cooldowns.timer(command, rules.cooldownGroup()),
                rules.cooldownScope(), rules.daily())) {
            return false;
        }

        double price = priceFor(player, rules);
        if (price > 0 && !economy.has(player, price)) {
            messages.send(player, "economy.insufficient",
                    "price", economy.format(price),
                    "balance", economy.format(economy.balance(player)));
            return false;
        }
        ItemPrice items = itemsFor(player, rules);
        if (!items.none()) {
            int held = items.countIn(player.getInventory());
            if (held < items.amount()) {
                messages.send(player, "economy.insufficient-items",
                        "amount", String.valueOf(items.amount()), "item", items.itemName(),
                        "held", String.valueOf(held));
                return false;
            }
        }
        return true;
    }

    /**
     * Takes the price now when the rules charge on use, for an action that finishes later.
     * Running what it returns gives the price back, when the rules allow it, if the action
     * never happens. Call it on the player's own thread.
     */
    public Runnable chargeUpfront(Player player, String command, CommandRules rules) {
        if (rules.chargeOn() != ChargeOn.USE) {
            return NOTHING_TO_REFUND;
        }
        double price = priceFor(player, rules);
        boolean money = price > 0 && economy.withdraw(player, price);
        if (money) {
            messages.send(player, "economy.charged", "price", economy.format(price));
        }
        ItemPrice items = itemsFor(player, rules);
        boolean taken = items.takeFrom(player.getInventory());
        if (taken) {
            charged(player, "economy.charged-items", items);
        }
        if (!money && !taken) {
            return NOTHING_TO_REFUND;
        }
        prepaid.add(player.getUniqueId(), command, new PrepaidPrices.Payment(
                money ? price : 0, taken ? items : ItemPrice.NONE));
        AtomicBoolean done = new AtomicBoolean();
        return () -> {
            if (done.compareAndSet(false, true)) {
                refund(player, command, rules);
            }
        };
    }

    public void charge(Player player, String command, CommandRules rules) {
        if (rules.cooldownSeconds() > 0 && !player.hasPermission(COOLDOWN_BYPASS)) {
            cooldowns.start(rules.cooldownScope().owner(player),
                    Cooldowns.timer(command, rules.cooldownGroup()),
                    rules.cooldownSeconds(), System.currentTimeMillis());
        }
        countUse(player, Cooldowns.timer(command, rules.cooldownGroup()), rules.cooldownScope(),
                rules.daily());

        if (prepaid.take(player.getUniqueId(), command) != null) {
            return;
        }
        double price = priceFor(player, rules);
        if (price > 0 && economy.withdraw(player, price)) {
            messages.send(player, "economy.charged", "price", economy.format(price));
        }
        ItemPrice items = itemsFor(player, rules);
        if (!items.none()) {
            schedulers.withEntity(player, () -> {
                if (items.takeFrom(player.getInventory())) {
                    charged(player, "economy.charged-items", items);
                }
            });
        }
    }

    private void refund(Player player, String command, CommandRules rules) {
        PrepaidPrices.Payment paid = prepaid.take(player.getUniqueId(), command);
        if (paid == null || !rules.refundOnCancel()) {
            return;
        }
        if (paid.money() > 0 && economy.deposit(player, paid.money())) {
            messages.send(player, "economy.refunded", "price", economy.format(paid.money()));
        }
        if (!paid.items().none()) {
            schedulers.withEntity(player, () -> {
                paid.items().giveTo(player);
                charged(player, "economy.refunded-items", paid.items());
            });
        }
    }

    private void charged(Player player, String key, ItemPrice items) {
        messages.send(player, key, "amount", String.valueOf(items.amount()),
                "item", items.itemName());
    }

    /** False, with the player told so, when the day's uses of this timer are spent. */
    public boolean hasUsesLeft(Player player, String timer, CooldownScope scope, DailyLimit daily) {
        if (!daily.limited() || player.hasPermission(COOLDOWN_BYPASS)) {
            return true;
        }
        long now = System.currentTimeMillis();
        long day = uses.dayStart(now, daily.resetAt());
        if (uses.used(scope.owner(player), timer, day) < daily.uses()) {
            return true;
        }
        messages.send(player, "cooldown.used-up", "uses", String.valueOf(daily.uses()),
                "time", Durations.format(uses.dayEnd(day) - now));
        return false;
    }

    /** One more of the day's uses, once the command has worked. */
    public void countUse(Player player, String timer, CooldownScope scope, DailyLimit daily) {
        if (daily.limited() && !player.hasPermission(COOLDOWN_BYPASS)) {
            uses.add(scope.owner(player), timer,
                    uses.dayStart(System.currentTimeMillis(), daily.resetAt()));
        }
    }

    /**
     * Zero for a free command, a player who bypasses prices, or a server with no economy, and
     * otherwise the price less the biggest discount the player has.
     */
    private double priceFor(Player player, CommandRules rules) {
        if (rules.price() <= 0 || !economy.enabled() || player.hasPermission(PRICE_BYPASS)) {
            return 0;
        }
        return Discount.apply(rules.price(), player, rules.discounts());
    }

    private ItemPrice itemsFor(Player player, CommandRules rules) {
        return player.hasPermission(PRICE_BYPASS) ? ItemPrice.NONE : rules.priceItem();
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
