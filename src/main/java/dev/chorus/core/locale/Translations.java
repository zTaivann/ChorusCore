package dev.chorus.core.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * One language's lines, with the language behind it to fall back on.
 *
 * <p>A translation only has to write down what it translates. Anything it leaves out is
 * taken from the file it falls back to, which is messages.yml, so a half-finished Spanish
 * file is a half-Spanish server rather than a broken one. That is also what lets a
 * translation survive an update that adds twenty new lines.
 */
final class Translations {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Map<String, Component> entries = new HashMap<>();
    private final Map<String, String> templates = new HashMap<>();
    private final Set<String> muted = new HashSet<>();
    private final @Nullable Translations fallback;

    private Component prefix = Component.empty();
    private String rawPrefix = "";

    Translations(@Nullable Translations fallback) {
        this.fallback = fallback;
    }

    /**
     * Reads a file into this language.
     *
     * <p>Called more than once for the language that is made of two files, since messages
     * and menus are one set of keys split by who edits them.
     */
    void read(YamlConfiguration data, String prefix) {
        for (String key : data.getKeys(true)) {
            if (data.isConfigurationSection(key)) {
                continue;
            }
            String template = data.getString(key);
            if (template == null) {
                continue;
            }
            if (template.isBlank()) {
                muted.add(key);
                continue;
            }
            String filled = TextFormat.toTags(template.replace("%prefix%", prefix));
            templates.put(key, filled);
            entries.put(key, MINI_MESSAGE.deserialize(filled));
        }
    }

    void prefix(String raw) {
        this.rawPrefix = raw;
        this.prefix = raw.isEmpty() ? Component.empty() : TextFormat.parse(raw);
    }

    Component prefix() {
        return fallback != null && rawPrefix.isEmpty() ? fallback.prefix() : prefix;
    }

    String rawPrefix() {
        return fallback != null && rawPrefix.isEmpty() ? fallback.rawPrefix() : rawPrefix;
    }

    /** True when this language, or the one behind it, was told to say nothing for this key. */
    boolean isMuted(String key) {
        if (muted.contains(key)) {
            return true;
        }
        if (templates.containsKey(key)) {
            return false;
        }
        return fallback != null && fallback.isMuted(key);
    }

    /** Null when no language in the chain has this key. */
    @Nullable String template(String key) {
        String template = templates.get(key);
        if (template != null) {
            return template;
        }
        return fallback == null ? null : fallback.template(key);
    }

    @Nullable Component entry(String key) {
        Component entry = entries.get(key);
        if (entry != null) {
            return entry;
        }
        return fallback == null ? null : fallback.entry(key);
    }
}
