package dev.chorus.core.command;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** How many times each command was used today, kept across restarts. */
public final class DailyUses {

    /** The longest day, with an hour for a clock change. */
    private static final long KEEP_MILLIS = TimeUnit.HOURS.toMillis(26);

    private final DailyUseRepository repository;
    private final Executor worker;
    private final Logger logger;
    private final ZoneId zone;
    private final Map<Key, Count> counts = new ConcurrentHashMap<>();

    private record Key(UUID owner, String timer) {
    }

    private record Count(long dayStart, int uses) {
    }

    public DailyUses(DailyUseRepository repository, Executor worker, Logger logger, ZoneId zone) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
        this.zone = zone;
    }

    /** Blocking. Reads what is left of the current day, at startup. */
    public void load() throws SQLException {
        long cutoff = System.currentTimeMillis() - KEEP_MILLIS;
        repository.deleteBefore(cutoff);
        for (DailyUseRepository.Row row : repository.findSince(cutoff)) {
            counts.put(new Key(row.owner(), row.timer()), new Count(row.dayStart(), row.uses()));
        }
    }

    /** How many times it was used in the day that began at {@code dayStart}. */
    public int used(UUID owner, String timer, long dayStart) {
        Count count = counts.get(new Key(owner, timer));
        return count == null || count.dayStart() != dayStart ? 0 : count.uses();
    }

    /** One more use in the day that began at {@code dayStart}, saved behind the scenes. */
    public void add(UUID owner, String timer, long dayStart) {
        Count now = counts.merge(new Key(owner, timer), new Count(dayStart, 1),
                (old, first) -> old.dayStart() == dayStart
                        ? new Count(dayStart, old.uses() + 1) : first);
        DailyUseRepository.Row row =
                new DailyUseRepository.Row(owner, timer, now.dayStart(), now.uses());
        worker.execute(() -> {
            try {
                repository.save(row);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "A command use could not be saved", exception);
            }
        });
    }

    /** Drops the counts of days that are over. */
    public void sweep(long now) {
        long cutoff = now - KEEP_MILLIS;
        counts.values().removeIf(count -> count.dayStart() < cutoff);
    }

    public void clear() {
        counts.clear();
    }

    /** When the current day began, for a day that starts at {@code reset} on the server's clock. */
    public long dayStart(long now, LocalTime reset) {
        ZonedDateTime at = Instant.ofEpochMilli(now).atZone(zone);
        ZonedDateTime start = at.with(reset);
        if (start.isAfter(at)) {
            start = start.minusDays(1);
        }
        return start.toInstant().toEpochMilli();
    }

    /** When the day that began at {@code dayStart} is over. */
    public long dayEnd(long dayStart) {
        return Instant.ofEpochMilli(dayStart).atZone(zone).plusDays(1).toInstant().toEpochMilli();
    }
}
