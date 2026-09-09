package dev.chorus.core.economy;

import org.bukkit.configuration.ConfigurationSection;

public record EconomySettings(double minimumPayment, double maximumPayment) {

    public static EconomySettings read(ConfigurationSection economy) {
        return new EconomySettings(
                Math.max(0.01, economy.getDouble("minimum-payment", 0.01)),
                Math.max(0, economy.getDouble("maximum-payment", 0)));
    }

    /** A maximum of zero means there is no ceiling. */
    public boolean isAboveMaximum(double amount) {
        return maximumPayment > 0 && amount > maximumPayment;
    }
}
