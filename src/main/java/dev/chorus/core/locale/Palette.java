package dev.chorus.core.locale;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Colours with names, put into a line before it is parsed. */
public final class Palette {

    /** For the moments before a palette has been read, and for the tests. */
    public static final Palette EMPTY = new Palette(Map.of());

    private static final String OPEN = "<c:";
    private static final char OPEN_END = '>';
    private static final String CLOSE = "</c>";

    /** How many times an entry may stand for another before it is called a loop. */
    private static final int MAX_DEPTH = 8;

    /** Tags with nothing to close. Closing one of these would put a stray tag in the line. */
    private static final Set<String> UNCLOSEABLE = Set.of("reset", "newline", "br");

    private final Map<String, Entry> colours;

    /** One name: what it opens, and what closes it again. */
    private record Entry(String open, String close) {
    }

    private Palette(Map<String, Entry> colours) {
        this.colours = colours;
    }

    public static Palette read(ConfigurationSection data, Consumer<String> onProblem) {
        Map<String, String> raw = new HashMap<>();
        for (String key : data.getKeys(false)) {
            String value = data.getString(key);
            if (value != null && !value.isBlank()) {
                raw.put(key.toLowerCase(Locale.ROOT), value.trim());
            }
        }
        if (raw.isEmpty()) {
            return EMPTY;
        }

        Map<String, Entry> resolved = new HashMap<>(raw.size());
        for (String name : raw.keySet()) {
            String open = resolve(name, raw, new LinkedHashSet<>(), onProblem);
            resolved.put(name, new Entry(open, closingFor(open)));
        }
        return new Palette(Map.copyOf(resolved));
    }

    /** The names this palette knows, so an unknown one can be told apart from a typo. */
    public Set<String> names() {
        return colours.keySet();
    }

    /** The same line with every name it uses filled in. */
    public String apply(String raw) {
        if (colours.isEmpty() || (!raw.contains(OPEN) && !raw.contains(CLOSE))) {
            return raw;
        }

        StringBuilder out = new StringBuilder(raw.length() + 32);
        Deque<String> open = new ArrayDeque<>();

        int at = 0;
        while (at < raw.length()) {
            if (raw.startsWith(CLOSE, at)) {
                out.append(open.isEmpty() ? CLOSE : open.pop());
                at += CLOSE.length();
                continue;
            }
            if (!raw.startsWith(OPEN, at)) {
                out.append(raw.charAt(at));
                at++;
                continue;
            }

            int end = raw.indexOf(OPEN_END, at + OPEN.length());
            Entry entry = end < 0 ? null
                    : colours.get(raw.substring(at + OPEN.length(), end).toLowerCase(Locale.ROOT));
            if (entry == null) {
                out.append(raw.charAt(at));
                at++;
                continue;
            }

            out.append(entry.open());
            open.push(entry.close());
            at = end + 1;
        }
        return out.toString();
    }

    /** One entry with every name it uses filled in, or the entry itself when it uses none. */
    private static String resolve(String name, Map<String, String> raw, Set<String> visiting,
                                  Consumer<String> onProblem) {
        String value = raw.get(name);
        if (value == null || !value.contains(OPEN)) {
            return value == null ? "" : value;
        }
        if (!visiting.add(name) || visiting.size() > MAX_DEPTH) {
            onProblem.accept(String.join(" -> ", visiting) + " -> " + name);
            return "";
        }

        StringBuilder out = new StringBuilder(value.length() + 16);
        int at = 0;
        while (at < value.length()) {
            if (!value.startsWith(OPEN, at)) {
                out.append(value.charAt(at));
                at++;
                continue;
            }
            int end = value.indexOf(OPEN_END, at + OPEN.length());
            String other = end < 0 ? null : value.substring(at + OPEN.length(), end)
                    .toLowerCase(Locale.ROOT);
            if (other == null || !raw.containsKey(other)) {
                out.append(value.charAt(at));
                at++;
                continue;
            }
            out.append(resolve(other, raw, visiting, onProblem));
            at = end + 1;
        }

        visiting.remove(name);
        return out.toString();
    }

    /** Every tag an entry opens, closed again in the reverse order. */
    private static String closingFor(String open) {
        List<String> tags = new ArrayList<>(2);
        int at = 0;
        while (at < open.length()) {
            if (open.charAt(at) != '<') {
                at++;
                continue;
            }
            int end = open.indexOf(OPEN_END, at);
            if (end < 0) {
                break;
            }
            String tag = tagName(open.substring(at + 1, end));
            if (tag != null && !UNCLOSEABLE.contains(tag)) {
                tags.add(tag);
            }
            at = end + 1;
        }

        StringBuilder closing = new StringBuilder(tags.size() * 8);
        for (int index = tags.size() - 1; index >= 0; index--) {
            closing.append("</").append(tags.get(index)).append(OPEN_END);
        }
        return closing.toString();
    }

    /** The name of a tag written out in full, or null when it is a closing tag. */
    private static @Nullable String tagName(String inside) {
        if (inside.isEmpty() || inside.charAt(0) == '/') {
            return null;
        }
        int argument = inside.indexOf(':');
        String name = (argument < 0 ? inside : inside.substring(0, argument)).toLowerCase(Locale.ROOT);
        return name.isBlank() ? null : name;
    }
}
