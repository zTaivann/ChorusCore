package dev.chorus.core.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Vault bridge.
 *
 * <p>The provider is looked up when the server finishes loading rather than on demand:
 * economy plugins register their service while they start and there is no guarantee they
 * got there before this one did. Caching it means a priced command costs a field read
 * instead of walking the plugin and service managers every time it runs.
 */
public final class VaultEconomy implements Economy, Listener {

    private final Server server;
    private final Logger logger;

    private volatile net.milkbowl.vault.economy.Economy provider;

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
        if (server.getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> registration =
                    server.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (registration != null) {
                found = registration.getProvider();
            }
        }

        if (found != null && provider == null) {
            logger.info("Hooked into the Vault economy provided by " + found.getName());
        }
        provider = found;
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
