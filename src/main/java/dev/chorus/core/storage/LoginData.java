package dev.chorus.core.storage;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import dev.chorus.core.locale.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * What a player needs in memory before they are let in. Loaded on the login thread, and let go
 * when their last connection closes, whether or not they ever reached the world.
 */
public final class LoginData implements Listener {

    /** One kind of data kept for each player. */
    public interface Part {

        /** Blocking. */
        void load(UUID player) throws SQLException;

        void unload(UUID player);
    }

    private record Entry(String what, Part part, boolean required) {
    }

    private final Messages messages;
    private final Logger logger;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final Map<UUID, Integer> connections = new ConcurrentHashMap<>();

    public LoginData(Messages messages, Logger logger) {
        this.messages = messages;
        this.logger = logger;
    }

    /**
     * @param what     how the log names it when it cannot be read
     * @param required whether a player is kept out when it cannot be read
     */
    public void add(String what, Part part, boolean required) {
        entries.add(new Entry(what, part, required));
    }

    /** For players already online when the plugin starts, who had no login to load at. */
    public void loadOnline(Collection<? extends Player> online, Executor worker) {
        List<Player> players = List.copyOf(online);
        if (players.isEmpty()) {
            return;
        }
        players.forEach(player -> connections.merge(player.getUniqueId(), 1, Integer::sum));
        worker.execute(() -> players.forEach(player ->
                loadAll(player.getUniqueId(), player.getName())));
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED
                && !loadAll(event.getUniqueId(), event.getName())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    messages.render("error.load-failed"));
        }
    }

    /** A refused login never gets a close event, so what was loaded for it goes here. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDecided(AsyncPlayerPreLoginEvent event) {
        UUID id = event.getUniqueId();
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            connections.merge(id, 1, Integer::sum);
        } else if (!connections.containsKey(id)) {
            unloadAll(id);
        }
    }

    /** After the quit for a player who joined. The same id logging in twice keeps its data. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(PlayerConnectionCloseEvent event) {
        UUID id = event.getPlayerUniqueId();
        if (connections.computeIfPresent(id, (key, open) -> open > 1 ? open - 1 : null) == null) {
            unloadAll(id);
        }
    }

    public void clear() {
        connections.clear();
        entries.clear();
    }

    /** False when something the player cannot go without could not be read. */
    private boolean loadAll(UUID id, String name) {
        for (Entry entry : entries) {
            try {
                entry.part().load(id);
            } catch (SQLException exception) {
                logger.log(entry.required() ? Level.SEVERE : Level.WARNING,
                        "Could not load the " + entry.what() + " of " + name, exception);
                if (entry.required()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void unloadAll(UUID id) {
        entries.forEach(entry -> entry.part().unload(id));
    }
}
