package dev.chorus.core.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueriesTest {

    private ExecutorService worker;

    @BeforeEach
    void open() {
        worker = Executors.newSingleThreadExecutor(Queries.storageThreads());
    }

    @AfterEach
    void close() {
        worker.shutdownNow();
    }

    /** A login reads what the last session wrote, never what it had before. */
    @Test
    void aLoadWaitsForTheWritesQueuedBeforeIt() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            order.add("write");
        });

        CompletableFuture<String> load = CompletableFuture.supplyAsync(() -> {
            try {
                return Queries.await(() -> {
                    order.add("load");
                    return "done";
                }, worker);
            } catch (SQLException failed) {
                throw new IllegalStateException(failed);
            }
        });
        release.countDown();

        assertEquals("done", load.get(5, TimeUnit.SECONDS));
        assertEquals(List.of("write", "load"), order);
    }

    /** Waiting for the queue from inside it would be waiting for itself. */
    @Test
    void onTheStorageThreadItRunsAtOnce() throws Exception {
        CompletableFuture<String> nested = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                nested.complete(Queries.await(() -> "inline", worker));
            } catch (SQLException failed) {
                nested.completeExceptionally(failed);
            }
        });

        assertEquals("inline", nested.get(5, TimeUnit.SECONDS));
    }
}
