package dev.chorus.core.economy;

import dev.chorus.core.locale.Messages;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Locale;

/** Used when the economy is disabled in config.yml, or its balance table could not be opened. */
public final class NoEconomy implements Economy {

    private final boolean failed;

    private NoEconomy(boolean failed) {
        this.failed = failed;
    }

    public static NoEconomy disabled() {
        return new NoEconomy(false);
    }

    public static NoEconomy failed() {
        return new NoEconomy(true);
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String status() {
        return failed ? "the balance table could not be opened" : "disabled in config.yml";
    }

    @Override
    public String status(Messages messages, CommandSender reader) {
        return messages.plain(reader, failed ? "core.economy-failed" : "core.economy-disabled");
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
