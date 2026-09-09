package dev.chorus.core.economy;

import org.bukkit.OfflinePlayer;

import java.util.Locale;

/** Used when the server has no economy, or when prices are switched off in the config. */
public final class NoEconomy implements Economy {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public double balance(OfflinePlayer player) {
        return 0;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return true;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public String format(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }
}
