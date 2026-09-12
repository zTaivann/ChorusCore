package dev.chorus.core.economy;

import org.bukkit.OfflinePlayer;

/** The plugin using its own ledger, with nothing in between. */
public final class ChorusEconomy implements Economy {

    private final Balances balances;

    public ChorusEconomy(Balances balances) {
        this.balances = balances;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String status() {
        return "built in, " + balances.size() + " accounts";
    }

    @Override
    public double balance(OfflinePlayer player) {
        return balances.of(player.getUniqueId());
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return balances.has(player.getUniqueId(), amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return balances.withdraw(player, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return balances.deposit(player, amount);
    }

    @Override
    public String format(double amount) {
        return balances.currency().format(amount);
    }
}
