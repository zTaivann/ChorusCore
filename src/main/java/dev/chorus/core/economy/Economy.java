package dev.chorus.core.economy;

import dev.chorus.core.locale.Messages;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

/**
 * What the plugin needs from an economy. Prices are ignored while it is disabled.
 */
public interface Economy {

    boolean enabled();

    /** A line saying what was found, for the startup log. */
    String status();

    /** The same, for /chorus status in the reader's language. */
    String status(Messages messages, CommandSender reader);

    double balance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String format(double amount);

    /** Looks the provider up again, for /chorus reload. */
    default void refresh() {
    }
}
