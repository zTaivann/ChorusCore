package dev.chorus.core.economy;

import dev.chorus.core.storage.Storage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Works out which economy the server is going to use and wires it up.
 *
 * <p>The built-in ledger is offered to the rest of the server through Vault at a low
 * priority, so a server that already runs a dedicated economy plugin keeps using it and one
 * that does not gets a working /pay without installing anything else. Vault is only a
 * bridge, so it is never required: with no Vault at all the ledger is simply used directly.
 */
public final class EconomySetup implements Listener {

    private final Plugin plugin;
    private final Storage storage;
    private final Executor worker;

    private EconomyMode mode = EconomyMode.AUTO;
    private Economy economy = new NoEconomy();
    private @Nullable Balances balances;
    private boolean offered;

    public EconomySetup(Plugin plugin, Storage storage, Executor worker) {
        this.plugin = plugin;
        this.storage = storage;
        this.worker = worker;
    }

    public Economy economy() {
        return economy;
    }

    public @Nullable Balances balances() {
        return balances;
    }

    public EconomyMode mode() {
        return mode;
    }

    /** Reads the config, opens the ledger if it is wanted, and picks what to use. */
    public void start(ConfigurationSection economySection) {
        mode = economySection.getBoolean("enabled", true)
                ? EconomyMode.parse(economySection.getString("provider", "auto"))
                : EconomyMode.OFF;

        if (mode == EconomyMode.OFF) {
            economy = new NoEconomy();
            return;
        }

        if (mode != EconomyMode.VAULT) {
            balances = openLedger(economySection);
        }
        if (balances == null && mode != EconomyMode.VAULT) {
            // The table could not be opened, so there is nothing to hand out.
            economy = new NoEconomy();
            return;
        }

        boolean vault = plugin.getServer().getPluginManager().isPluginEnabled("Vault");
        if (balances != null && vault) {
            offer(balances, mode == EconomyMode.SELF ? ServicePriority.High : ServicePriority.Low);
        }

        economy = switch (mode) {
            case SELF -> new ChorusEconomy(balances);
            case VAULT -> new VaultEconomy(plugin.getServer(), plugin.getLogger());
            // Through Vault when it is there, so another economy plugin wins and every
            // plugin on the server agrees on one set of balances. Directly when it is not.
            case AUTO -> vault
                    ? new VaultEconomy(plugin.getServer(), plugin.getLogger())
                    : new ChorusEconomy(balances);
            case OFF -> new NoEconomy();
        };
    }

    public void reload(ConfigurationSection economySection) {
        if (balances != null) {
            balances.apply(Currency.read(economySection));
        }
        economy.refresh();
    }

    /** Gives a new player their opening balance the first time they arrive. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (balances != null) {
            balances.open(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        }
    }

    private @Nullable Balances openLedger(ConfigurationSection economySection) {
        SqlBalanceRepository repository = new SqlBalanceRepository(storage);
        try {
            repository.createTables();
            Balances ledger = new Balances(repository, worker, plugin.getLogger(),
                    Currency.read(economySection));
            ledger.load();
            return ledger;
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "The balances table could not be opened, so prices are switched off", exception);
            return null;
        }
    }

    private void offer(Balances ledger, ServicePriority priority) {
        if (offered) {
            return;
        }
        try {
            plugin.getServer().getServicesManager().register(
                    net.milkbowl.vault.economy.Economy.class,
                    new VaultBridge(plugin.getServer(), ledger), plugin, priority);
            offered = true;
        } catch (LinkageError | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Vault is installed but the built-in economy could not be offered to it",
                    exception);
        }
    }
}
