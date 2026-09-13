package dev.chorus.core.utility.signs;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The signs that stand in for a command.
 *
 * <p>Each one runs the command a player would have typed, which is what keeps them honest:
 * the permission, the cooldown, the price and the message all come from that command rather
 * than being written a second time here and drifting apart from it.
 *
 * <p>{@link #FREE} is the exception. There is no command for handing an item over, so it is
 * the one kind the listener carries out itself.
 */
public enum ServiceSign {

    HEAL("[heal]", "heal"),
    FEED("[feed]", "feed"),
    REPAIR("[repair]", "fix"),
    DISPOSAL("[disposal]", "trash"),
    WORKBENCH("[workbench]", "craft"),
    ENCHANT("[enchant]", "enchant"),
    GAMEMODE("[gamemode]", "gamemode"),
    KIT("[kit]", "kit"),
    BALANCE("[balance]", "balance"),
    SPAWN("[spawn]", "spawn"),
    MAIL("[mail]", "mail"),
    TIME("[time]", "time"),
    WEATHER("[weather]", "weather"),
    SPAWNMOB("[spawnmob]", "spawnmob"),

    /** Hands over an amount of an item. The amount is line two, the item line three. */
    FREE("[free]", "");

    private static final Map<String, ServiceSign> BY_HEADER = byHeader();

    private final String header;
    private final String command;

    ServiceSign(String header, String command) {
        this.header = header;
        this.command = command;
    }

    public String header() {
        return header;
    }

    /** What is typed instead. Empty for the one kind that is not a command. */
    public String command() {
        return command;
    }

    public String createPermission() {
        return "chorus.signs.create." + name().toLowerCase(Locale.ROOT);
    }

    public String usePermission() {
        return "chorus.signs.use." + name().toLowerCase(Locale.ROOT);
    }

    public static ServiceSign of(String firstLine) {
        return BY_HEADER.get(firstLine.trim().toLowerCase(Locale.ROOT));
    }

    public static Set<String> headers() {
        return BY_HEADER.keySet();
    }

    private static Map<String, ServiceSign> byHeader() {
        Map<String, ServiceSign> headers = new HashMap<>();
        for (ServiceSign sign : values()) {
            headers.put(sign.header, sign);
        }
        return Map.copyOf(headers);
    }
}
