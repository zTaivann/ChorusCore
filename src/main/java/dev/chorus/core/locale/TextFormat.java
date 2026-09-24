package dev.chorus.core.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Reads both ways of writing coloured text, so nobody has to learn a new one. */
public final class TextFormat {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Colours and weights only. A click, a hover or a selector in text a player wrote would
     * act on whoever reads it, and on a sign a click runs with the server's permissions.
     */
    private static final MiniMessage STYLING = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.color(), StandardTags.decorations(),
                    StandardTags.gradient(), StandardTags.rainbow(), StandardTags.transition(),
                    StandardTags.reset()))
            .build();

    /** Indexed by the digit or letter after the marker, in the order the game numbers them. */
    private static final String[] COLOURS = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white"};

    private static final char[] CODES = "0123456789abcdef".toCharArray();

    /** Either marker: {@code &} as typed in a config, {@code §} as pasted out of somewhere. */
    private static final String MARKERS = "&§";

    private static final int HEX_LENGTH = 6;

    private TextFormat() {
    }

    /** Whether the text holds an old-style code at all, which is the common case of "no". */
    public static boolean hasLegacy(String raw) {
        for (int at = 0; at < raw.length() - 1; at++) {
            char current = raw.charAt(at);
            if (MARKERS.indexOf(current) < 0) {
                continue;
            }
            char next = raw.charAt(at + 1);
            if (next == current || isCode(next)) {
                return true;
            }
        }
        return false;
    }

    /** The same text with every old-style code rewritten as a tag. */
    public static String toTags(String raw) {
        if (!hasLegacy(raw)) {
            return raw;
        }

        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int at = 0; at < raw.length(); at++) {
            char current = raw.charAt(at);
            if (MARKERS.indexOf(current) < 0 || at + 1 >= raw.length()) {
                out.append(current);
                continue;
            }

            if (raw.charAt(at + 1) == current) {
                // Doubled: "&&c" is the two characters "&c".
                out.append(current);
                at++;
                continue;
            }

            char code = Character.toLowerCase(raw.charAt(at + 1));
            if (code == '#' && hex(raw, at + 2) != null) {
                out.append('<').append('#').append(hex(raw, at + 2)).append('>');
                at += 1 + HEX_LENGTH;
                continue;
            }
            // &x&a&1&b&2&c&3, the way the old proxies wrote a hex colour.
            String spread = spreadHex(raw, at);
            if (spread != null) {
                out.append('<').append('#').append(spread).append('>');
                at += 13;
                continue;
            }

            String tag = tagFor(code);
            if (tag == null) {
                out.append(current);
                continue;
            }
            out.append(tag);
            at++;
        }
        return out.toString();
    }

    /** A line of text in either format, ready to send. */
    public static Component parse(String raw) {
        return MINI_MESSAGE.deserialize(toTags(raw));
    }

    /** A line of text a player wrote: its colours and weights are read, nothing else is. */
    public static Component parsePlayer(String raw) {
        return STYLING.deserialize(toTags(raw));
    }

    /** The same for something written on an item. */
    public static Component forItem(String raw) {
        return upright(parse(raw));
    }

    /** A line of lore, which needs one thing more. */
    public static Component forLore(String raw) {
        return asLore(parse(raw));
    }

    /** Item text as it should be drawn: never italic unless the text itself asked. */
    public static Component upright(Component text) {
        return text.style().decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET
                ? text.decoration(TextDecoration.ITALIC, false)
                : text;
    }

    /** A line of lore as it should be drawn: upright, and grey where nothing said otherwise. */
    public static Component asLore(Component line) {
        Component upright = upright(line);
        return upright.color() == null ? upright.color(NamedTextColor.GRAY) : upright;
    }

    /** The reverse, for writing an item somebody built in game back into the config. */
    public static String toText(@Nullable Component text) {
        if (text == null) {
            return "";
        }

        Component written = text;
        if (written.style().decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE) {
            written = written.decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET);
            if (NamedTextColor.GRAY.equals(written.color())) {
                written = written.color(null);
            }
        }
        return MINI_MESSAGE.serialize(written);
    }

    private static String tagFor(char code) {
        for (int index = 0; index < CODES.length; index++) {
            if (CODES[index] == code) {
                // A colour clears what came before it, exactly as the game does.
                return "<reset><" + COLOURS[index] + ">";
            }
        }
        return switch (code) {
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    private static boolean isCode(char code) {
        char lower = Character.toLowerCase(code);
        return (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f')
                || "klmnor#x".indexOf(lower) >= 0;
    }

    /** The six characters after a {@code &#}, or null when they are not a colour. */
    private static String hex(String raw, int from) {
        if (from + HEX_LENGTH > raw.length()) {
            return null;
        }
        String digits = raw.substring(from, from + HEX_LENGTH);
        for (int at = 0; at < HEX_LENGTH; at++) {
            if (Character.digit(digits.charAt(at), 16) < 0) {
                return null;
            }
        }
        return digits.toLowerCase(Locale.ROOT);
    }

    /** {@code &x&a&1&b&2&c&3} into {@code a1b2c3}, or null when it is not that. */
    private static String spreadHex(String raw, int at) {
        if (Character.toLowerCase(raw.charAt(at + 1)) != 'x' || at + 13 >= raw.length()) {
            return null;
        }
        StringBuilder digits = new StringBuilder(HEX_LENGTH);
        for (int pair = 0; pair < HEX_LENGTH; pair++) {
            int marker = at + 2 + pair * 2;
            if (MARKERS.indexOf(raw.charAt(marker)) < 0
                    || Character.digit(raw.charAt(marker + 1), 16) < 0) {
                return null;
            }
            digits.append(Character.toLowerCase(raw.charAt(marker + 1)));
        }
        return digits.toString();
    }
}
