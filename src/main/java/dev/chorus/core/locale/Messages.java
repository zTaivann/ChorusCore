package dev.chorus.core.locale;

import dev.chorus.core.api.MessageApi;
import dev.chorus.core.config.ConfigFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads messages.yml and hands out ready-to-send components.
 *
 * <p>A line with no placeholders is parsed once on load and handed out as it is. A line with
 * placeholders is parsed on the spot, with the values handed to MiniMessage as unparsed tags
 * rather than pasted into the text.
 *
 * <p>That last part is not a detail. Substituting into the finished component only works
 * while the placeholder survives as one piece of text, and several tags do not leave it that
 * way: {@code <gradient>} colours a line character by character, so {@code %home%} ends up as
 * six separate components and no search for it can ever find it. Resolving during the parse
 * puts the value in before any of that happens. Values still cannot smuggle in tags of their
 * own, because an unparsed tag is inserted as plain text.
 */
public final class Messages implements MessageApi {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Placeholders are written {@code %name%} in the file and become {@code <chorus_name>}
     * for MiniMessage. The prefix is what keeps a placeholder called "key" or "lang" from
     * being mistaken for the MiniMessage tag of the same name.
     */
    private static final String SLOT_PREFIX = "chorus_";

    private final Plugin plugin;
    private final ConfigFile file;
    private final Map<String, Component> entries = new HashMap<>();
    private final Map<String, String> templates = new HashMap<>();
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
        templates.clear();
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
            String filled = template.replace("%prefix%", prefix);
            templates.put(key, filled);
            entries.put(key, MINI_MESSAGE.deserialize(filled));
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

    /**
     * A message as plain text, for the small words that end up inside another message —
     * "no limit", "free", "none". They live in messages.yml like everything else rather
     * than being written into the Java, so a translated file translates them too.
     */
    public String plain(String key, String... placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(render(key, placeholders));
    }

    /**
     * A message as separate lines, split where the template says {@code <newline>}.
     *
     * <p>For item lore, which is a list of lines rather than one piece of text. A newline
     * inside a single lore line is not a line break at all: the game draws it as the missing
     * character it is, a little box in the middle of the sentence.
     *
     * <p>Each line is parsed on its own, so a colour opened before a break does not bleed
     * into the line after it, which is what you want on a tooltip anyway.
     */
    public List<Component> renderLines(String key, String... placeholders) {
        String template = templates.get(key);
        if (template == null) {
            return List.of(cached(key));
        }
        return fillLines(template, placeholders);
    }

    /** Package-private so the checks can exercise the split without standing up a server. */
    static List<Component> fillLines(String template, String... placeholders) {
        String[] parts = template.split("<newline>", -1);
        List<Component> lines = new ArrayList<>(parts.length);
        for (String part : parts) {
            lines.add(fill(part, placeholders));
        }
        return lines;
    }

    public Component render(String key, String... placeholders) {
        if (placeholders.length == 0) {
            return cached(key);
        }

        String template = templates.get(key);
        if (template == null) {
            return cached(key);
        }
        return fill(template, placeholders);
    }

    /**
     * Puts the values into a template as it is parsed. Package-private so the checks can
     * exercise the part that actually goes wrong without standing up a whole server.
     */
    static Component fill(String template, String... placeholders) {
        // The tags are named after the placeholders that were actually passed, so a %word%
        // nobody filled in is left alone rather than turning into an empty gap.
        TagResolver.Builder resolvers = TagResolver.builder();
        String filled = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String slot = SLOT_PREFIX + placeholders[i];
            filled = filled.replace("%" + placeholders[i] + "%", "<" + slot + ">");
            resolvers.resolver(Placeholder.unparsed(slot, placeholders[i + 1]));
        }
        return MINI_MESSAGE.deserialize(filled, resolvers.build());
    }

    private Component cached(String key) {
        Component message = entries.get(key);
        if (message != null) {
            return message;
        }
        // A blank template is an admin silencing the message, not a mistake.
        if (muted.contains(key)) {
            return Component.empty();
        }
        if (alreadyReported.add(key)) {
            plugin.getLogger().warning("Missing message '" + key + "' in messages.yml");
        }
        return Component.text(key);
    }
}
