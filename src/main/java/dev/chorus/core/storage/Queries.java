package dev.chorus.core.storage;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class Queries {

    @FunctionalInterface
    public interface Call<T> {
        T run() throws SQLException;
    }

    private Queries() {
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
}
