package dev.chorus.core.audit;

import dev.chorus.core.storage.Queries;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** What staff did, and to whom. */
public final class AuditLog {

    private static final int MAX_DETAIL = 250;
    private static final int MAX_PAGE = 50;

    private final AuditRepository repository;
    private final Executor worker;
    private final Executor mainThread;
    private final Logger logger;

    private volatile boolean enabled = true;
    private volatile int keepDays = 60;
    private volatile int pageSize = 10;

    public AuditLog(AuditRepository repository, Executor worker, Executor mainThread, Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    public void apply(ConfigurationSection log) {
        this.enabled = log.getBoolean("enabled", true);
        this.keepDays = Math.max(0, log.getInt("keep-days", 60));
        this.pageSize = Math.max(1, Math.min(MAX_PAGE, log.getInt("page-size", 10)));
    }

    public boolean enabled() {
        return enabled;
    }

    public int pageSize() {
        return pageSize;
    }

    public void record(CommandSender actor, String action, @Nullable String subject,
                       @Nullable String detail) {
        if (!enabled) {
            return;
        }
        AuditEntry entry = new AuditEntry(actor.getName(), action, subject, trim(detail),
                System.currentTimeMillis());

        worker.execute(() -> {
            try {
                repository.record(entry);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "A staff action could not be written to the log", exception);
            }
        });
    }

    /** A null name asks for everything. Completes on the server thread. */
    public CompletableFuture<List<AuditEntry>> recent(@Nullable String name, int limit) {
        return Queries.run(() -> repository.findRecent(name, limit), worker, mainThread);
    }

    /** Drops entries older than the configured window. Run once, at startup. */
    public void prune() {
        if (!enabled || keepDays <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepDays);

        worker.execute(() -> {
            try {
                int removed = repository.deleteBefore(cutoff);
                if (removed > 0) {
                    logger.info("Dropped " + removed + " staff log entries older than "
                            + keepDays + " days.");
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "The staff log could not be pruned", exception);
            }
        });
    }

    /** A sudoed command can be any length, and the column is not. */
    private static @Nullable String trim(@Nullable String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL - 3) + "...";
    }
}
