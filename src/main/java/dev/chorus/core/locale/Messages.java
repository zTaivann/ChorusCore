package dev.chorus.core.locale;

import dev.chorus.core.api.MessageApi;
import dev.chorus.core.config.ConfigFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads messages.yml and hands out ready-to-send components.
 *
 * <p>Templates are parsed once on load, so only the placeholder substitution runs per
 * message. Values are inserted into the parsed component instead of into the raw string,
 * which stops a player name from smuggling in MiniMessage tags.
 */
public final class Messages implements MessageApi {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final ConfigFile file;
    private final Map<String, Component> entries = new HashMap<>();
    private final Set<String> muted = new HashSet<>();
    private final Set<String> alreadyReported = new HashSet<>();

    private Component prefix = Component.empty();
    private String rawPrefix = "";

    private Messages(Plugin plugin, ConfigFile file) {
        this.plugin = plugin;
        this.file = file;
    }

    public static Messages load(Plugin plugin, ConfigFile file) {
        Messages messages = new Messages(plugin, file);
        messages.reload();
        return messages;
    }

    /** Re-reads the already reloaded file. Reloading the file itself is the caller's job. */
    public void reload() {
        entries.clear();
        muted.clear();
        alreadyReported.clear();

        YamlConfiguration data = file.data();
        String prefix = data.getString("prefix", "");
        this.rawPrefix = prefix;
        this.prefix = prefix.isEmpty() ? Component.empty() : MINI_MESSAGE.deserialize(prefix);

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
            entries.put(key, MINI_MESSAGE.deserialize(template.replace("%prefix%", prefix)));
        }
    }

    public void send(CommandSender target, String key, String... placeholders) {
        if (muted.contains(key)) {
            return;
        }
        target.sendMessage(render(key, placeholders));
    }

    /** For lines a command builds itself, such as the clickable teleport buttons. */
    public Component prefix() {
        return prefix;
    }

    /**
     * Parses a line that came from somewhere other than messages.yml, such as a kit name or
     * a custom command, honouring {@code %prefix%} the same way.
     */
    public Component parse(String raw) {
        return MINI_MESSAGE.deserialize(raw.replace("%prefix%", rawPrefix));
    }

    public Component render(String key, String... placeholders) {
        Component message = entries.get(key);
        if (message == null) {
            // A blank template is an admin silencing the message, not a mistake.
            if (muted.contains(key)) {
                return Component.empty();
            }
            if (alreadyReported.add(key)) {
                plugin.getLogger().warning("Missing message '" + key + "' in messages.yml");
            }
            return Component.text(key);
        }

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replaceText(substitution(placeholders[i], placeholders[i + 1]));
        }
        return message;
    }

    private static TextReplacementConfig substitution(String placeholder, String value) {
        return TextReplacementConfig.builder()
                .matchLiteral("%" + placeholder + "%")
                .replacement(value)
                .build();
    }
}
