package dev.chorus.core.economy;

import dev.chorus.core.storage.Queries;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes down who paid whom, so a dispute about a trade has an answer.
 *
 * <p>Recording is fire and forget: a payment has already gone through by the time it reaches
 * here, and the player who made it should not be kept waiting on a write, nor told it failed
 * when their money moved perfectly well.
 */
public final class PaymentLog {

    private final PaymentRepository repository;
    private final Executor worker;
    private final Executor mainThread;
    private final Logger logger;

    private volatile EconomySettings settings;

    PaymentLog(PaymentRepository repository, Executor worker, Executor mainThread, Logger logger,
               EconomySettings settings) {
        this.repository = repository;
        this.worker = worker;
        this.mainThread = mainThread;
        this.logger = logger;
        this.settings = settings;
    }

    void apply(EconomySettings updated) {
        this.settings = updated;
    }

    public boolean enabled() {
        return settings.logPayments();
    }

    public int pageSize() {
        return settings.logPageSize();
    }

    public void record(Player payer, Player payee, double amount) {
        if (!settings.logPayments()) {
            return;
        }
        Payment payment = new Payment(payer.getUniqueId(), payer.getName(),
                payee.getUniqueId(), payee.getName(), amount, System.currentTimeMillis());

        worker.execute(() -> {
            try {
                repository.record(payment);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "A payment could not be written to the log", exception);
            }
        });
    }

    /** A null player asks for everyone's. Completes on the server thread. */
    public CompletableFuture<List<Payment>> recent(@Nullable UUID player, int limit) {
        return Queries.run(() -> repository.findRecent(player, limit), worker, mainThread);
    }

    /** Drops entries older than the configured window. Run once, at startup. */
    void prune() {
        int days = settings.logKeepDays();
        if (!settings.logPayments() || days <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);

        worker.execute(() -> {
            try {
                int removed = repository.deleteBefore(cutoff);
                if (removed > 0) {
                    logger.info("Dropped " + removed + " payment log entries older than "
                            + days + " days.");
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "The payment log could not be pruned", exception);
            }
        });
    }
}
