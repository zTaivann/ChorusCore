package dev.chorus.core.economy;

import org.bukkit.OfflinePlayer;

/**
 * What the plugin needs from an economy. Every price is ignored while this reports itself
 * as disabled, so a server without Vault behaves exactly as it did before prices existed.
 */
public interface Economy {

    boolean enabled();

    double balance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String format(double amount);

    /** Looks the provider up again, for /chorus reload. */
    default void refresh() {
    }
}
