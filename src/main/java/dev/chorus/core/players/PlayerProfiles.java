package dev.chorus.core.players;

import dev.chorus.core.locale.TextFormat;
import dev.chorus.core.storage.Queries;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The record of who has been on the server: names, nicknames, where they last were and the
 * address they came from.
 */
public final class PlayerProfiles {

    private final PlayerProfileRepository repository;
    private final Executor worker;
    private final Executor mainThread;
    private final Logger logger;

    private final Map<UUID, String> nicknames = new ConcurrentHashMap<>();
    private final Map<UUID, String> addresses = new ConcurrentHashMap<>();

    public PlayerProfiles(PlayerProfileRepository repository, Executor worker, Executor mainThread,
                          Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    /** Writes down the visit and brings back whatever this player is called. */
    public void arrived(Player player) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        String address = addressOf(player);
        addresses.put(id, address);
        long now = System.currentTimeMillis();

        worker.execute(() -> {
            try {
                repository.seen(id, name, address, now);
                PlayerProfile profile = repository.find(id);
                String nickname = profile == null ? null : profile.nickname();
                mainThread.execute(() -> {
                    if (nickname == null || nickname.isEmpty()) {
                        nicknames.remove(id);
                        return;
                    }
                    nicknames.put(id, nickname);
                    Player online = player.getServer().getPlayer(id);
                    if (online != null) {
                        show(online, nickname);
                    }
                });
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not record the visit of " + name, exception);
            }
        });
    }

    public void left(Player player) {
        UUID id = player.getUniqueId();
        var where = player.getLocation();
        String world = where.getWorld() == null ? "" : where.getWorld().getName();
        double x = where.getX();
        double y = where.getY();
        double z = where.getZ();
        float yaw = where.getYaw();
        float pitch = where.getPitch();
        long now = System.currentTimeMillis();

        nicknames.remove(id);
        addresses.remove(id);
        worker.execute(() -> {
            try {
                repository.left(id, now, world, x, y, z, yaw, pitch);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not record where " + id + " logged out", exception);
            }
        });
    }

    public Optional<String> nickname(UUID player) {
        return Optional.ofNullable(nicknames.get(player));
    }

    /** Sets or clears a nickname, showing it at once and saving it behind the scenes. */
    public void nickname(Player player, @Nullable String nickname) {
        UUID id = player.getUniqueId();
        if (nickname == null || nickname.isEmpty()) {
            nicknames.remove(id);
            show(player, player.getName());
        } else {
            nicknames.put(id, nickname);
            show(player, nickname);
        }

        worker.execute(() -> {
            try {
                repository.nickname(id, nickname);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not save the nickname of " + player.getName(), exception);
            }
        });
    }

    /** Whoever is currently going by this nickname, among the players online. */
    public Optional<Player> byNickname(Player asker, String nickname) {
        String wanted = SqlPlayerProfileRepository.plain(nickname);
        for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
            if (!SqlPlayerProfileRepository.plain(entry.getValue()).equals(wanted)) {
                continue;
            }
            Player online = asker.getServer().getPlayer(entry.getKey());
            if (online != null) {
                return Optional.of(online);
            }
        }
        return Optional.empty();
    }

    /** True while somebody else is already going by this name. */
    public boolean taken(UUID asking, String nickname) {
        String wanted = SqlPlayerProfileRepository.plain(nickname);
        for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
            if (!entry.getKey().equals(asking)
                    && SqlPlayerProfileRepository.plain(entry.getValue()).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    public CompletableFuture<Optional<PlayerProfile>> find(String name) {
        return Queries.run(() -> Optional.ofNullable(repository.findByName(name)), worker, mainThread);
    }

    public CompletableFuture<Optional<PlayerProfile>> find(UUID player) {
        return Queries.run(() -> Optional.ofNullable(repository.find(player)), worker, mainThread);
    }

    /** Other accounts that have connected from the same address. */
    public CompletableFuture<List<String>> alts(PlayerProfile profile) {
        return Queries.run(() -> repository.sharing(profile.address(), profile.player()),
                worker, mainThread);
    }

    /** The address somebody online is connected from, without a port on the end. */
    public String addressOf(Player player) {
        String cached = addresses.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        var socket = player.getAddress();
        if (socket == null || socket.getAddress() == null) {
            return "";
        }
        return socket.getAddress().getHostAddress();
    }

    /** Names for tab completion: everyone online, then anyone who has been here before. */
    public List<String> knownNames(Player asker, String prefix, int limit) {
        List<String> names = new ArrayList<>();
        String typed = prefix.toLowerCase(Locale.ROOT);
        for (Player online : asker.getServer().getOnlinePlayers()) {
            if (asker.canSee(online) && online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                names.add(online.getName());
            }
        }
        try {
            for (String stored : repository.namesLike(prefix, limit)) {
                if (!names.contains(stored)) {
                    names.add(stored);
                }
            }
        } catch (SQLException ignored) {
            // Tab completion is not worth a stack trace; what is online is enough.
        }
        return names;
    }

    public void clear() {
        nicknames.clear();
        addresses.clear();
    }

    private static void show(Player player, String name) {
        Component rendered = TextFormat.parse(name);
        player.displayName(rendered);
        player.playerListName(rendered);
    }
}
