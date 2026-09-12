package dev.chorus.core.economy;

import org.bukkit.OfflinePlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ledger the plugin keeps when it is the economy itself.
 *
 * <p>Every account is held in memory. A balance has to be readable the instant a priced
 * command runs and there is no room for a database round trip in the middle of one, so the
 * table is read once at startup and written back whenever an amount changes. The whole of a
 * large server's ledger is a few megabytes, and a lookup costs a hash.
 *
 * <p>Writes are queued rather than waited on. A balance that changed is already correct for
 * everybody reading it; the row catching up a moment later changes nothing.
 */
public final class Balances {

    private final BalanceRepository repository;
    private final Executor worker;
    private final Logger logger;
    private final Map<UUID, Entry> accounts = new ConcurrentHashMap<>();

    private volatile Currency currency;

    public Balances(BalanceRepository repository, Executor worker, Logger logger, Currency currency) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
        this.currency = currency;
    }

    public void apply(Currency updated) {
        this.currency = updated;
    }

    public Currency currency() {
        return currency;
    }

    /** Blocking, once, at startup. */
    public void load() throws SQLException {
        accounts.clear();
        for (BalanceRepository.Account account : repository.all()) {
            accounts.put(account.player(), new Entry(account.name(), account.balance()));
        }
    }

    public int size() {
        return accounts.size();
    }

    /** Opens an account at the starting balance if this player has never had one. */
    public void open(UUID player, String name) {
        Entry existing = accounts.get(player);
        if (existing != null) {
            if (!existing.name.equals(name)) {
                accounts.put(player, new Entry(name, existing.balance));
                write(player, name, existing.balance);
            }
            return;
        }
        double opening = currency.clamp(currency.startingBalance());
        accounts.put(player, new Entry(name, opening));
        write(player, name, opening);
    }

    public boolean exists(UUID player) {
        return accounts.containsKey(player);
    }

    public double of(UUID player) {
        Entry entry = accounts.get(player);
        return entry == null ? currency.clamp(currency.startingBalance()) : entry.balance;
    }

    public boolean has(UUID player, double amount) {
        return of(player) + 1e-9 >= currency.round(amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        double taken = currency.round(amount);
        if (taken <= 0 || !has(player.getUniqueId(), taken)) {
            return false;
        }
        set(player, of(player.getUniqueId()) - taken);
        return true;
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        double given = currency.round(amount);
        if (given <= 0) {
            return false;
        }
        set(player, of(player.getUniqueId()) + given);
        return true;
    }

    /** The one place a balance changes, so clamping and the write happen exactly once. */
    public void set(OfflinePlayer player, double amount) {
        UUID id = player.getUniqueId();
        Entry existing = accounts.get(id);
        String name = existing != null ? existing.name
                : player.getName() == null ? id.toString() : player.getName();
        double balance = currency.clamp(amount);

        accounts.put(id, new Entry(name, balance));
        write(id, name, balance);
    }

    public void forget(UUID player) {
        accounts.remove(player);
        worker.execute(() -> {
            try {
                repository.delete(player);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not delete the account of " + player, exception);
            }
        });
    }

    /**
     * The richest accounts, newest balance first.
     *
     * <p>Sorted from memory rather than asked of the database, which keeps /baltop off the
     * worker threads entirely and lets it answer for players who have never been online at
     * the same time as anybody.
     */
    public List<Ranked> top(int limit, int offset) {
        List<Ranked> ranked = new ArrayList<>(accounts.size());
        accounts.forEach((id, entry) -> ranked.add(new Ranked(id, entry.name, entry.balance)));
        ranked.sort(Comparator.comparingDouble(Ranked::balance).reversed());

        int from = Math.min(offset, ranked.size());
        return List.copyOf(ranked.subList(from, Math.min(from + limit, ranked.size())));
    }

    public double total() {
        double sum = 0;
        for (Entry entry : accounts.values()) {
            sum += entry.balance;
        }
        return sum;
    }

    private void write(UUID player, String name, double balance) {
        worker.execute(() -> {
            try {
                repository.save(player, name, balance);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not save the balance of " + name, exception);
            }
        });
    }

    public record Ranked(UUID player, String name, double balance) {
    }

    private record Entry(String name, double balance) {
    }
}
