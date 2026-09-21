package dev.chorus.core.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One language's lines, with the language behind it to fall back on. */
final class Translations {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** The key that names the prefix rather than a line to send. */
    private static final String PREFIX = "prefix";

    private final Map<String, Component> entries = new HashMap<>();
    private final Map<String, String> templates = new HashMap<>();
    private final Set<String> muted = new HashSet<>();
    private final @Nullable Translations fallback;
    private final Palette palette;

    private Component prefix = Component.empty();
    private String rawPrefix = "";

    Translations(@Nullable Translations fallback, Palette palette) {
        this.fallback = fallback;
        this.palette = palette;
    }

    /**
     * Reads a file into this language.
     *
     * @param prefix what {@code %prefix%} stands for in this file.
     */
    void read(YamlConfiguration data, String prefix) {
        for (String key : data.getKeys(true)) {
            if (data.isConfigurationSection(key) || key.equals(PREFIX)) {
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
            String filled =
                    TextFormat.toTags(palette.apply(template.replace("%prefix%", prefix)));
            templates.put(key, filled);
            entries.put(key, MINI_MESSAGE.deserialize(filled));
        }
    }

    void prefix(String raw) {
        this.rawPrefix = palette.apply(raw);
        this.prefix = rawPrefix.isEmpty() ? Component.empty() : TextFormat.parse(rawPrefix);
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
