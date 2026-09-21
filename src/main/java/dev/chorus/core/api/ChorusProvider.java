package dev.chorus.core.api;

import org.bukkit.Bukkit;

import java.util.Optional;

/**
 * The way in.
 *
 * <pre>
 * ChorusProvider.get().flatMap(ChorusApi::homes)
 *         .ifPresent(homes -&gt; homes.list(player.getUniqueId()));
 * </pre>
 */
public final class ChorusProvider {

    private ChorusProvider() {
    }

    public static Optional<ChorusApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(ChorusApi.class));
    }
}
