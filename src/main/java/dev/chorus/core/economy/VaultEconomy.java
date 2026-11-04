package dev.chorus.core.economy;

import dev.chorus.core.locale.Messages;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.logging.Logger;

/** Vault bridge. */
public final class VaultEconomy implements Economy, Listener {

    private final Server server;
    private final Logger logger;

    private volatile net.milkbowl.vault.economy.Economy provider;
    private volatile String status = "not looked up yet";

    public VaultEconomy(Server server, Logger logger) {
        this.server = server;
        this.logger = logger;
        refresh();
    }

    /** Fires once every plugin has enabled, which is the first moment Vault is reliable. */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        refresh();
    }

    @Override
    public void refresh() {
        net.milkbowl.vault.economy.Economy found = null;
        String reason;

        if (!server.getPluginManager().isPluginEnabled("Vault")) {
            reason = "no Vault installed, so every price is ignored";
        } else {
            RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> registration =
                    server.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (registration == null) {
                // Vault on its own keeps no balances: an economy plugin has to register behind it.
                reason = "Vault is installed but no economy plugin has registered with it, "
                        + "so every price is ignored";
            } else {
                found = registration.getProvider();
                reason = "using the Vault economy provided by " + found.getName();
            }
        }

        if (found != null && provider == null) {
            logger.info("Hooked into the Vault economy provided by " + found.getName());
        }
        provider = found;
        this.status = reason;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public String status(Messages messages, CommandSender reader) {
        net.milkbowl.vault.economy.Economy current = provider;
        if (current != null) {
            return messages.plain(reader, "core.economy-vault", "provider", current.getName());
        }
        return messages.plain(reader, server.getPluginManager().isPluginEnabled("Vault")
                ? "core.economy-vault-empty"
                : "core.economy-vault-missing");
    }

    @Override
    public boolean enabled() {
        return provider != null;
    }

    @Override
    public double balance(OfflinePlayer player) {
        net.milkbowl.vault.economy.Economy vault = provider;
        return vault == null ? 0 : vault.getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        net.milkbowl.vault.economy.Economy vault = provider;
        return vault == null || vault.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        net.milkbowl.vault.economy.Economy vault = provider;
        return vault != null && vault.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        net.milkbowl.vault.economy.Economy vault = provider;
        return vault != null && vault.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public String format(double amount) {
        net.milkbowl.vault.economy.Economy vault = provider;
        return vault == null ? String.format(Locale.ROOT, "%.2f", amount) : vault.format(amount);
    }
}
