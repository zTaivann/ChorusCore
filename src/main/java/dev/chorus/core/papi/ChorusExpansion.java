package dev.chorus.core.papi;

import dev.chorus.core.api.ChorusApi;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes the plugin's numbers to PlaceholderAPI, which is how a scoreboard or tab plugin
 * shows them without depending on ChorusCore at all.
 */
public final class ChorusExpansion extends PlaceholderExpansion {

    private final ChorusApi api;
    private final PlaceholderValues values;

    public ChorusExpansion(ChorusApi api, PlaceholderValues values) {
        this.api = api;
        this.values = values;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "chorus";
    }

    @Override
    public @NotNull String getAuthor() {
        return "zTaivann";
    }

    @Override
    public @NotNull String getVersion() {
        return api.version();
    }

    /** The plugin outlives a PlaceholderAPI reload, so the expansion should not be dropped. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameters) {
        return values.of(player, parameters);
    }
}
