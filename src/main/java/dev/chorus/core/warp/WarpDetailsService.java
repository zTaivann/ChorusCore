package dev.chorus.core.warp;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every warp's settings, held in memory.
 *
 * <p>A server has tens of warps, not thousands, and {@code /warps} asks about all of them at
 * once to sort and group the menu. Loading the lot at startup makes that free; the writes are
 * rare enough to go to the database in the background as they happen.
 */
public final class WarpDetailsService {

    private final WarpDetailsRepository repository;
    private final Executor worker;
    private final Logger logger;

    /** Warp names are already lower case by the time they get here, but a warp is a name. */
    private final Map<String, WarpDetails> cache = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    WarpDetailsService(WarpDetailsRepository repository, Executor worker, Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
    }

    /** Blocking. Called once while the module is starting up. */
    void load() throws SQLException {
        cache.clear();
        for (WarpDetails details : repository.findAll()) {
            cache.put(details.warp(), details);
        }
    }

    public WarpDetails of(String warp) {
        return cache.getOrDefault(warp, WarpDetails.blank(warp));
    }

    public List<WarpDetails> all() {
        return List.copyOf(cache.values());
    }

    public void save(WarpDetails details) {
        // Nothing to keep a row for once every field is back to its default.
        if (details.isBlank() && details.uses() == 0) {
            forget(details.warp());
            return;
        }
        cache.put(details.warp(), details);
        write(() -> repository.save(details), details.warp());
    }

    /** Counted in memory and written through, so the number survives a restart. */
    public void countUse(String warp) {
        save(of(warp).used());
    }

    public void forget(String warp) {
        cache.remove(warp);
        write(() -> repository.delete(warp), warp);
    }

    private void write(Change change, String warp) {
        worker.execute(() -> {
            try {
                change.run();
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "The settings of warp '" + warp + "' could not be saved",
                        exception);
            }
        });
    }

    @FunctionalInterface
    private interface Change {
        void run() throws SQLException;
    }
}
