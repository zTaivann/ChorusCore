package dev.chorus.core.economy;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleFunction;
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
 * <p>Every change goes through {@link #change}, which reads the old amount and writes the new
 * one in a single step the map will not let anything else interleave with. Checking a balance
 * and then taking money from it as two separate operations is how a shop gets paid twice on a
 * server that ticks more than one thread.
 *
 * <p>Writes to the database are queued rather than waited on. A balance that changed is
 * already correct for everybody reading it; the row catching up a moment later changes
 * nothing.
 */
public final class Balances {

    /** How long a /baltop ranking is reused before it is worked out again. */
    private static final long RANKING_MILLIS = 30_000;

    private final BalanceRepository repository;
    private final Executor worker;
    private final Logger logger;
    private final Map<UUID, Entry> accounts = new ConcurrentHashMap<>();

    private volatile Currency currency;
    private volatile List<Ranked> ranking = List.of();
    private volatile long rankedAt;

    /** Bumped by every change, so a ranking built over one is known to be out of date. */
    private final AtomicLong changes = new AtomicLong();
    private volatile long rankedFor = -1;

    public Balances(BalanceRepository repository, Executor worker, Logger logger, Currency currency) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
        this.currency = currency;
    }

    public void apply(Currency updated) {
        this.currency = updated;
        this.changes.incrementAndGet();
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
        changes.incrementAndGet();
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
        changes.incrementAndGet();
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
        if (taken <= 0) {
            return false;
        }
        // Null refuses the change, which is how "not enough" is said without a second read.
        return change(player, balance -> balance + 1e-9 >= taken ? balance - taken : null);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        double given = currency.round(amount);
        if (given <= 0) {
            return false;
        }
        return change(player, balance -> balance + given);
    }

    public void set(OfflinePlayer player, double amount) {
        change(player, balance -> amount);
    }

    /**
     * Reads and writes one balance as a single step.
     *
     * @param update the new amount, or null to leave it alone
     * @return whether anything changed
     */
    private boolean change(OfflinePlayer player, DoubleFunction<@Nullable Double> update) {
        UUID id = player.getUniqueId();
        String fallback = player.getName() == null ? id.toString() : player.getName();
        Entry[] written = new Entry[1];

        accounts.compute(id, (key, existing) -> {
            double current = existing == null
                    ? currency.clamp(currency.startingBalance())
                    : existing.balance;
            Double next = update.apply(current);
            if (next == null) {
                return existing;
            }
            Entry updated = new Entry(existing == null ? fallback : existing.name,
                    currency.clamp(next));
            written[0] = updated;
            return updated;
        });

        if (written[0] == null) {
            return false;
        }
        write(id, written[0].name, written[0].balance);
        changes.incrementAndGet();
        return true;
    }

    public void forget(UUID player) {
        accounts.remove(player);
        changes.incrementAndGet();
        worker.execute(() -> {
            try {
                repository.delete(player);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not delete the account of " + player, exception);
            }
        });
    }

    /**
     * The richest accounts, richest first.
     *
     * <p>Ranked from memory rather than asked of the database, and the ranking is kept for
     * half a minute: sorting every account on a server with a hundred thousand of them is not
     * something to do again because two people ran /baltop in the same breath.
     */
    public List<Ranked> top(int limit, int offset) {
        List<Ranked> ranked = rank();
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

    /** Throws the ranking away, so the next /baltop is worked out from scratch. */
    public void refresh() {
        changes.incrementAndGet();
    }

    private List<Ranked> rank() {
        long generation = changes.get();
        long now = System.currentTimeMillis();
        List<Ranked> cached = ranking;
        if (rankedFor == generation && now - rankedAt < RANKING_MILLIS) {
            return cached;
        }

        List<Ranked> ranked = new ArrayList<>(accounts.size());
        accounts.forEach((id, entry) -> ranked.add(new Ranked(id, entry.name, entry.balance)));
        ranked.sort(Comparator.comparingDouble(Ranked::balance).reversed());

        ranking = List.copyOf(ranked);
        rankedAt = now;
        // Last, and with the value read before the sort: a change that landed while this was
        // building leaves the generation behind, and the next call works it out again.
        rankedFor = generation;
        return ranking;
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
