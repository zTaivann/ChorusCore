package dev.chorus.core.economy;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The ledger offered to the rest of the server through Vault, so a shop or job plugin can
 * spend the same money these commands do.
 */
@SuppressWarnings("deprecation")
public final class VaultBridge implements net.milkbowl.vault.economy.Economy {

    private static final String NO_BANKS = "This economy has no banks";
    private static final String NO_PLAYER = "No player by that name";

    private final Server server;
    private final Balances balances;

    public VaultBridge(Server server, Balances balances) {
        this.server = server;
        this.balances = balances;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "ChorusCore";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return balances.currency().decimals();
    }

    @Override
    public String format(double amount) {
        return balances.currency().format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return balances.currency().plural();
    }

    @Override
    public String currencyNameSingular() {
        return balances.currency().singular();
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return balances.exists(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String world) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(String name) {
        OfflinePlayer player = cached(name);
        return player != null && hasAccount(player);
    }

    @Override
    public boolean hasAccount(String name, String world) {
        return hasAccount(name);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return balances.of(player.getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(String name) {
        OfflinePlayer player = cached(name);
        return player == null ? 0 : getBalance(player);
    }

    @Override
    public double getBalance(String name, String world) {
        return getBalance(name);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return balances.has(player.getUniqueId(), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(String name, double amount) {
        OfflinePlayer player = cached(name);
        return player != null && has(player, amount);
    }

    @Override
    public boolean has(String name, String world, double amount) {
        return has(name, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return refused(player, "Cannot withdraw a negative amount");
        }
        if (!balances.withdraw(player, amount)) {
            return refused(player, "Not enough money");
        }
        return new EconomyResponse(amount, getBalance(player),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String name, double amount) {
        OfflinePlayer player = cached(name);
        return player == null ? missing() : withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String name, String world, double amount) {
        return withdrawPlayer(name, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return refused(player, "Cannot deposit a negative amount");
        }
        if (!balances.deposit(player, amount)) {
            return refused(player, "The deposit was refused");
        }
        return new EconomyResponse(amount, getBalance(player),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String name, double amount) {
        OfflinePlayer player = cached(name);
        return player == null ? missing() : depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String name, String world, double amount) {
        return depositPlayer(name, amount);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (balances.exists(player.getUniqueId())) {
            return false;
        }
        balances.open(player.getUniqueId(),
                player.getName() == null ? player.getUniqueId().toString() : player.getName());
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String world) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String name) {
        OfflinePlayer player = cached(name);
        return player != null && createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String name, String world) {
        return createPlayerAccount(name);
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer owner) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, String owner) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    private @Nullable OfflinePlayer cached(String name) {
        OfflinePlayer online = server.getPlayerExact(name);
        return online != null ? online : server.getOfflinePlayerIfCached(name);
    }

    private EconomyResponse refused(OfflinePlayer player, String why) {
        return new EconomyResponse(0, getBalance(player),
                EconomyResponse.ResponseType.FAILURE, why);
    }

    private static EconomyResponse missing() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, NO_PLAYER);
    }

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, NO_BANKS);
    }
}
