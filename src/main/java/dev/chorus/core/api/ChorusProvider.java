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
 *
 * <p>Add {@code depend: [ ChorusCore ]} or {@code softdepend: [ ChorusCore ]} to your
 * plugin.yml. With softdepend, check the result rather than assuming it is there.
 */
public final class ChorusProvider {

    private ChorusProvider() {
    }

    public static Optional<ChorusApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(ChorusApi.class));
    }
}
