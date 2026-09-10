package dev.chorus.core.economy;

import org.bukkit.configuration.ConfigurationSection;

public record EconomySettings(double minimumPayment, double maximumPayment, boolean logPayments,
                              int logKeepDays, int logPageSize, int topSize) {

    private static final int MAX_PAGE = 50;

    public static EconomySettings read(ConfigurationSection economy) {
        ConfigurationSection log = economy.getConfigurationSection("log");
        return new EconomySettings(
                Math.max(0.01, economy.getDouble("minimum-payment", 0.01)),
                Math.max(0, economy.getDouble("maximum-payment", 0)),
                log == null || log.getBoolean("enabled", true),
                log == null ? 30 : Math.max(0, log.getInt("keep-days", 30)),
                clamp(log == null ? 10 : log.getInt("page-size", 10)),
                clamp(economy.getInt("top-size", 10)));
    }

    /** A maximum of zero means there is no ceiling. */
    public boolean isAboveMaximum(double amount) {
        return maximumPayment > 0 && amount > maximumPayment;
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(MAX_PAGE, value));
    }
}
