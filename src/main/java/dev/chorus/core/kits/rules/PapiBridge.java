package dev.chorus.core.kits.rules;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * The one place that names a PlaceholderAPI type.
 *
 * <p>Kept apart from {@link Placeholders} on purpose: the JVM links a class the first time
 * it is used, so a server without PlaceholderAPI never loads this one and never notices
 * that the type is missing.
 */
final class PapiBridge {

    private PapiBridge() {
    }

    static String fill(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
