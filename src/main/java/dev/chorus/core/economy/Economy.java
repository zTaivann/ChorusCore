package dev.chorus.core.economy;

import org.bukkit.OfflinePlayer;

/**
 * What the plugin needs from an economy. Every price is ignored while this reports itself
 * as disabled, so a server without Vault behaves exactly as it did before prices existed.
 */
public interface Economy {

    boolean enabled();

    /**
     * A line saying what was found, for the startup log and for /chorus status. "Vault is
     * installed" and "there is an economy" are different things, and telling them apart is
     * the difference between a server owner fixing it in a minute and giving up.
     */
    String status();

    double balance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String format(double amount);

    /** Looks the provider up again, for /chorus reload. */
    default void refresh() {
    }
}
