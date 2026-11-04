package dev.chorus.core.economy;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The ledger the plugin keeps when it is the economy itself. */
public final class Balances {

    private final BalanceRepository repository;
    private final Executor worker;
    private final Logger logger;
    private final Map<UUID, Entry> accounts = new ConcurrentHashMap<>();

    private volatile Currency currency;
    private volatile List<Ranked> ranking = List.of();
    private volatile long rankedAt;

    /** How long a /baltop ranking is reused before it is worked out again. */
    private volatile long rankingMillis = 30_000;

    /** Players whose balance has changed since it was last written. */
    private final Set<UUID> unsaved = ConcurrentHashMap.newKeySet();

    /** Bumped by every balance change. */
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

    public void rankingSeconds(int seconds) {
        this.rankingMillis = Math.max(0, seconds) * 1000L;
    }

    /** Blocking, once, at startup. */
    public void load() throws SQLException {
        accounts.clear();
        for (BalanceRepository.Account account : repository.all()) {
            accounts.put(account.player(),
                    new Entry(account.name(), account.balance(), account.hidden()));
        }
        changes.incrementAndGet();
    }

    public int size() {
        return accounts.size();
    }

    /** Opens an account at the starting balance if this player has never had one. */
    public void open(UUID player, String name) {
        open(player, name, currency.startingBalance());
    }

    /** The same, with a starting balance of its own for a new account. */
    public void open(UUID player, String name, double startingBalance) {
        boolean[] changed = new boolean[1];
        accounts.compute(player, (id, existing) -> {
            if (existing != null && existing.name.equals(name)) {
                return existing;
            }
            changed[0] = true;
            return existing == null
                    ? new Entry(name, currency.clamp(startingBalance), false)
                    : new Entry(name, existing.balance, existing.hidden);
        });
        if (changed[0]) {
            write(player);
            changes.incrementAndGet();
        }
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
            Entry updated = existing == null
                    ? new Entry(fallback, currency.clamp(next), false)
                    : new Entry(existing.name, currency.clamp(next), existing.hidden);
            written[0] = updated;
            return updated;
        });

        if (written[0] == null) {
            return false;
        }
        write(id);
        changes.incrementAndGet();
        return true;
    }

    /** Leaves a player out of /baltop, or puts them back, remembering it while they are away. */
    public void hide(UUID player, boolean hidden) {
        boolean[] changed = new boolean[1];
        accounts.computeIfPresent(player, (id, existing) -> {
            if (existing.hidden == hidden) {
                return existing;
            }
            changed[0] = true;
            return new Entry(existing.name, existing.balance, hidden);
        });
        if (!changed[0]) {
            return;
        }
        changes.incrementAndGet();
        worker.execute(() -> {
            try {
                repository.hide(player, hidden);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not save whether " + player
                        + " shows in /baltop", exception);
            }
        });
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

    /** Every account /baltop may show, richest first. */
    public List<Ranked> ranking() {
        return rank();
    }

    /** What the accounts /baltop may show hold between them. */
    public double total() {
        double sum = 0;
        for (Entry entry : accounts.values()) {
            if (!entry.hidden) {
                sum += entry.balance;
            }
        }
        return sum;
    }

    /** Drops the cached ranking. */
    public void refresh() {
        changes.incrementAndGet();
    }

    private List<Ranked> rank() {
        long generation = changes.get();
        long now = System.currentTimeMillis();
        List<Ranked> cached = ranking;
        if (rankedFor == generation && now - rankedAt < rankingMillis) {
            return cached;
        }

        List<Ranked> ranked = new ArrayList<>(accounts.size());
        accounts.forEach((id, entry) -> {
            if (!entry.hidden) {
                ranked.add(new Ranked(id, entry.name, entry.balance));
            }
        });
        ranked.sort(Comparator.comparingDouble(Ranked::balance).reversed());

        ranking = List.copyOf(ranked);
        rankedAt = now;
        // The generation is read before the sort, so a change landing during it is not lost.
        rankedFor = generation;
        return ranking;
    }

    /** Many changes before the storage thread gets round to it become one write of the last. */
    private void write(UUID player) {
        if (unsaved.add(player)) {
            worker.execute(() -> flush(player));
        }
    }

    private void flush(UUID player) {
        unsaved.remove(player);
        Entry entry = accounts.get(player);
        if (entry == null) {
            return;
        }
        try {
            repository.save(player, entry.name, entry.balance);
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Could not save the balance of " + entry.name, exception);
        }
    }

    public record Ranked(UUID player, String name, double balance) {
    }

    private record Entry(String name, double balance, boolean hidden) {
    }
}
