package dev.chorus.core.economy;

import dev.chorus.core.command.Numbers;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The economy module's own settings.
 *
 * @param payTax            percent of each /pay taken as tax, from 0 to 100
 * @param topHideEmpty      leaves accounts with nothing in them out of /baltop
 * @param topRefreshSeconds how long a /baltop ranking is reused
 */
public record EconomySettings(double minimumPayment, double maximumPayment, double confirmAbove,
                              double payTax, boolean logPayments, int logKeepDays,
                              int logPageSize, int topSize, boolean topHideEmpty,
                              int topRefreshSeconds) {

    private static final int MAX_PAGE = 50;

    public static EconomySettings read(ConfigurationSection economy) {
        ConfigurationSection log = economy.getConfigurationSection("log");
        return new EconomySettings(
                Math.max(0.01, economy.getDouble("minimum-payment", 0.01)),
                Math.max(0, economy.getDouble("maximum-payment", 0)),
                Math.max(0, economy.getDouble("confirm-above", 0)),
                Math.max(0, Math.min(100, economy.getDouble("pay-tax", 0))),
                log == null || log.getBoolean("enabled", true),
                log == null ? 30 : Math.max(0, log.getInt("keep-days", 30)),
                clamp(log == null ? 10 : log.getInt("page-size", 10)),
                clamp(economy.getInt("top-size", 10)),
                economy.getBoolean("top-hide-empty", false),
                Math.max(0, economy.getInt("top-refresh-seconds", 30)));
    }

    /** Zero never asks. */
    public boolean needsConfirming(double amount) {
        return confirmAbove > 0 && amount >= confirmAbove;
    }

    /** The tax on a payment of this much, to the cent. */
    public double taxOn(double amount) {
        return payTax <= 0 ? 0 : Numbers.cents(amount * payTax / 100);
    }

    /** A maximum of zero means there is no ceiling. */
    public boolean isAboveMaximum(double amount) {
        return maximumPayment > 0 && amount > maximumPayment;
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(MAX_PAGE, value));
    }
}
