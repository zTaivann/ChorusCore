package dev.chorus.core.staff;

import dev.chorus.core.storage.Queries;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Notes are read rarely and written rarely, so nothing here is cached. */
public final class NoteService {

    private static final int MAX_LENGTH = 250;

    private final NoteRepository repository;
    private final Executor worker;
    private final Executor mainThread;

    NoteService(NoteRepository repository, Executor worker, Executor mainThread) {
        this.repository = repository;
        this.worker = worker;
        this.mainThread = mainThread;
    }

    public CompletableFuture<Void> add(StaffNote note) {
        return Queries.run(() -> {
            repository.add(note);
            return null;
        }, worker, mainThread);
    }

    public CompletableFuture<List<StaffNote>> find(UUID subject) {
        return Queries.run(() -> repository.findFor(subject), worker, mainThread);
    }

    public CompletableFuture<Integer> clear(UUID subject) {
        return Queries.run(() -> repository.clear(subject), worker, mainThread);
    }

    /** The column has a limit and a chat message does not. */
    public static String trim(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
    }
}
