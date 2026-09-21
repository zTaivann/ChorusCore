package dev.chorus.core.kits.rules;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** The one place that names a PlaceholderAPI type. */
final class PapiBridge {

    private PapiBridge() {
    }

    static String fill(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
