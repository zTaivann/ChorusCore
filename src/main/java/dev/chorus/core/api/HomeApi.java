package dev.chorus.core.api;

import dev.chorus.core.home.Home;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Homes of players who are online. Nothing here touches the database, so it is all safe to
 * call from the server thread as often as you like.
 */
public interface HomeApi {

    List<Home> list(UUID owner);

    Optional<Home> find(UUID owner, String name);

    int count(UUID owner);

    /** {@link Integer#MAX_VALUE} for a player with chorus.home.unlimited. */
    int limit(Player player);

    /** Writes to storage first, then updates what the player sees. */
    CompletableFuture<Void> save(Home home);

    CompletableFuture<Boolean> delete(UUID owner, String name);
}
