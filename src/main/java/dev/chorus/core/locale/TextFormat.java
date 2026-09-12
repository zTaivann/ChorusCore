package dev.chorus.core.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Reads both ways of writing coloured text, so nobody has to learn a new one.
 *
 * <p>MiniMessage is what the plugin ships with, and {@code &}-codes are what a decade of
 * Minecraft servers are written in. Either works, and both work in the same line:
 *
 * <pre>
 *   &amp;c&amp;lRed Bold
 *   &lt;red&gt;&lt;bold&gt;Red Bold
 *   &amp;6Gold and &lt;gradient:#1f8f8c:#3fb8b4&gt;a gradient
 * </pre>
 *
 * <p>The old codes are turned into tags and then handed to MiniMessage, which is what lets
 * the two mix. A colour code emits a {@code <reset>} before its colour, because that is what
 * the game does: in vanilla, {@code &l&cText} is red and <em>not</em> bold, the colour having
 * cleared the bold. Emitting the colour alone would quietly change what an existing config
 * looks like.
 *
 * <p>A doubled marker writes the marker itself: {@code &&c} is the two characters {@code &c},
 * which is how a line talks about a colour code without becoming one.
 *
 * <p>Nothing here is applied to a value that came from a player. A name with {@code &c} in it
 * stays four characters of text, exactly as it does with MiniMessage tags.
 */
public final class TextFormat {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

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

    /**
     * The same text with every old-style code rewritten as a tag.
     *
     * <p>Returns the string untouched when there is nothing to rewrite, which is most lines
     * and every line this plugin ships with.
     */
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
                // Doubled, which is how the marker itself is written: "&&c" is the two
                // characters "&c" and not a red anything.
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

    /**
     * The same for something written on an item.
     *
     * <p>Item names and lore are drawn in italics unless told otherwise, which nobody ever
     * wants and which is not something the text asked for. Turning it off on the parent still
     * lets {@code <i>} or {@code &o} inside the text win, because a style set on a child
     * beats the one it inherits.
     */
    public static Component forItem(String raw) {
        return Component.text()
                .decoration(TextDecoration.ITALIC, false)
                .append(parse(raw))
                .build();
    }

    /**
     * The reverse, for writing an item somebody built in game back into the config.
     *
     * <p>The italic-off that {@link #forItem} puts on the outside comes back off here. It
     * belongs to how the game draws an item, not to what the line says, and writing it down
     * would put a second one on the next time the text came through, and a third the time
     * after that, until the file was more instruction than text.
     */
    public static String toText(@Nullable Component text) {
        if (text == null) {
            return "";
        }
        Component written =
                text.style().decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE
                        ? text.decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET)
                        : text;
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
