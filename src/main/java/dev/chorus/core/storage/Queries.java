package dev.chorus.core.storage;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Queries {

    /** How long a player joining waits for their data before being told to try again. */
    private static final long WAIT_SECONDS = 10;

    private static final ThreadLocal<Boolean> ON_STORAGE = ThreadLocal.withInitial(() -> false);

    @FunctionalInterface
    public interface Call<T> {
        T run() throws SQLException;
    }

    private Queries() {
    }

    /** The one thread every query runs on. */
    public static ThreadFactory storageThreads() {
        return task -> {
            Thread thread = new Thread(() -> {
                ON_STORAGE.set(true);
                task.run();
            }, "chorus-storage");
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Runs a query off the server thread and hands the result back on it. Both outcomes go
     * through the same hop, so a caller never has to wonder which thread its callback is on.
     */
    public static <T> CompletableFuture<T> run(Call<T> call, Executor worker, Executor mainThread) {
        CompletableFuture<T> result = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                T value = call.run();
                mainThread.execute(() -> result.complete(value));
            } catch (Exception exception) {
                mainThread.execute(() -> result.completeExceptionally(exception));
            }
        });
        return result;
    }

    /**
     * Runs a query on the storage thread and waits for it, for a thread that is allowed to
     * wait, such as the one a player joins on. Queued behind every write already waiting, so
     * it reads what was saved a moment before rather than what was there before that.
     */
    public static <T> T await(Call<T> call, Executor worker) throws SQLException {
        // Already on it: waiting for the queue would be waiting for this very thread.
        if (ON_STORAGE.get()) {
            return call.run();
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                result.complete(call.run());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        });

        try {
            return result.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof SQLException sql) {
                throw sql;
            }
            throw new SQLException(failed.getCause());
        } catch (TimeoutException slow) {
            throw new SQLException("The database did not answer within " + WAIT_SECONDS + "s", slow);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for the database", interrupted);
        }
    }
}
