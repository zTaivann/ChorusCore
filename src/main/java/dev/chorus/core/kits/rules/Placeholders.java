package dev.chorus.core.kits.rules;

import dev.chorus.core.papi.PlaceholderValues;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Fills {@code %placeholders%} in a line, through PlaceholderAPI when it is installed. */
public final class Placeholders {

    private static final String PLUGIN = PlaceholderValues.PLUGIN;

    /** Written the way a person reads a date, not the way a machine sorts one. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static volatile Boolean available;

    private Placeholders() {
    }

    /** Whatever the plugin knows how to fill in itself, plus PlaceholderAPI when present. */
    public static String fill(Player player, String text) {
        if (text.indexOf('%') < 0) {
            // Nothing to do, and by far the commonest case. Worth the check.
            return text;
        }
        String filled = text
                .replace("%player%", player.getName())
                .replace("%date%", LocalDate.now().format(DATE))
                .replace("%time%", LocalTime.now().format(TIME));
        return present() ? PapiBridge.fill(player, filled) : filled;
    }

    /**
     * Looked up once. A server does not gain or lose PlaceholderAPI while it is running, and
     * this is asked on every requirement check.
     */
    private static boolean present() {
        Boolean known = available;
        if (known == null) {
            known = Bukkit.getPluginManager().isPluginEnabled(PLUGIN);
            available = known;
        }
        return known;
    }

    /** Called when the plugin reloads, so a fresh install is noticed without a restart. */
    public static void forget() {
        available = null;
    }
}
