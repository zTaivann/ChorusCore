package dev.chorus.core.mail;

import dev.chorus.core.storage.Queries;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Letters between players, kept for whoever was not online to hear it said. */
public final class MailService {

    private static final int DEFAULT_KEEP_DAYS = 60;
    private static final int DEFAULT_INBOX_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 8;
    private static final int DEFAULT_LENGTH = 256;

    private final MailRepository repository;
    private final Executor worker;
    private final Executor mainThread;
    private final Logger logger;

    private volatile int keepDays = DEFAULT_KEEP_DAYS;
    private volatile int inboxSize = DEFAULT_INBOX_SIZE;
    private volatile int pageSize = DEFAULT_PAGE_SIZE;
    private volatile int maxLength = DEFAULT_LENGTH;

    public MailService(MailRepository repository, Executor worker, Executor mainThread, Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    public void apply(ConfigurationSection mail) {
        keepDays = Math.max(0, mail.getInt("keep-days", DEFAULT_KEEP_DAYS));
        inboxSize = Math.max(1, mail.getInt("inbox-size", DEFAULT_INBOX_SIZE));
        pageSize = Math.max(1, Math.min(20, mail.getInt("page-size", DEFAULT_PAGE_SIZE)));
        maxLength = Math.max(16, Math.min(512, mail.getInt("max-length", DEFAULT_LENGTH)));
    }

    public int pageSize() {
        return pageSize;
    }

    public int inboxSize() {
        return inboxSize;
    }

    public int maxLength() {
        return maxLength;
    }

    public CompletableFuture<List<Mail>> inbox(UUID recipient) {
        return Queries.run(() -> repository.inbox(recipient), worker, mainThread);
    }

    public CompletableFuture<Integer> unread(UUID recipient) {
        return Queries.run(() -> repository.unread(recipient), worker, mainThread);
    }

    /** @return false when the inbox is already full, so nothing is quietly dropped. */
    public CompletableFuture<Boolean> send(UUID recipient, String sender, String body) {
        long now = System.currentTimeMillis();
        return Queries.run(() -> {
            if (repository.count(recipient) >= inboxSize) {
                return false;
            }
            repository.send(recipient, sender, body, now);
            return true;
        }, worker, mainThread);
    }

    public CompletableFuture<Void> markRead(UUID recipient) {
        return Queries.run(() -> {
            repository.markRead(recipient);
            return null;
        }, worker, mainThread);
    }

    public CompletableFuture<Integer> clear(UUID recipient) {
        return Queries.run(() -> repository.clear(recipient), worker, mainThread);
    }

    public CompletableFuture<Boolean> clear(UUID recipient, long id) {
        return Queries.run(() -> repository.clear(recipient, id), worker, mainThread);
    }

    /** Runs at startup, so a server that has been going for years is not carrying everything. */
    public void prune() {
        int days = keepDays;
        worker.execute(() -> {
            try {
                repository.prune(days);
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Old mail could not be cleared out", exception);
            }
        });
    }
}
