package dev.chorus.core.rules;

import dev.chorus.core.papi.PlaceholderValues;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Fills {@code %placeholders%} in a line, through PlaceholderAPI when it is installed. */
public final class Placeholders {

    private static final String PLUGIN = PlaceholderValues.PLUGIN;


    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static volatile Boolean available;

    private Placeholders() {
    }

    /** Whatever the plugin knows how to fill in itself, plus PlaceholderAPI when present. */
    public static String fill(Player player, String text) {
        if (text.indexOf('%') < 0) {

            return text;
        }
        String filled = text
                .replace("%player%", player.getName())
                .replace("%date%", LocalDate.now().format(DATE))
                .replace("%time%", LocalTime.now().format(TIME));
        return present() ? PapiBridge.fill(player, filled) : filled;
    }

    /** Looked up once until the next reload. */
    private static boolean present() {
        Boolean known = available;
        if (known == null) {
            known = Bukkit.getPluginManager().isPluginEnabled(PLUGIN);
            available = known;
        }
        return known;
    }

    /** Looks for PlaceholderAPI again on the next fill. */
    public static void forget() {
        available = null;
    }
}
