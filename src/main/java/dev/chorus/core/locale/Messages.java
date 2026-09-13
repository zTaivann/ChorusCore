package dev.chorus.core.locale;

import dev.chorus.core.api.MessageApi;
import dev.chorus.core.config.ConfigFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads messages.yml and menus.yml and hands out ready-to-send components.
 *
 * <p>Two files, one set of keys. A line that goes to chat lives in messages.yml and a word
 * that appears on a screen lives in menus.yml, which is a distinction anybody editing them
 * cares about and none of the code does. The key says which file it is in: everything
 * beginning {@code menu.} is a screen.
 *
 * <p>Other languages live beside them as {@code messages_es.yml}, {@code messages_fr.yml} and
 * so on. A translation only writes down what it translates and everything else comes from
 * messages.yml, so one is never out of date, only incomplete.
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

    private static final String TRANSLATION_PREFIX = "messages_";
    private static final String TRANSLATION_SUFFIX = ".yml";

    /**
     * The translations that travel inside the jar, written out on first start.
     *
     * <p>Written down rather than found, because a jar cannot be asked what is in it without
     * opening itself. One line per language somebody has contributed.
     */
    private static final List<String> SHIPPED = List.of("messages_es.yml");

    private final Plugin plugin;
    private final ConfigFile messages;
    private final ConfigFile menus;
    private final Set<String> alreadyReported = new HashSet<>();

    /** Every translation found on disk, by its two-letter code. */
    private final Map<String, Translations> languages = new HashMap<>();

    private Translations english = new Translations(null);

    /** What the console and anybody with no language of their own gets. */
    private volatile Translations standard = english;
    private volatile boolean perPlayer;

    private Messages(Plugin plugin, ConfigFile messages, ConfigFile menus) {
        this.plugin = plugin;
        this.messages = messages;
        this.menus = menus;
    }

    public static Messages load(Plugin plugin, ConfigFile messages, ConfigFile menus) {
        Messages loaded = new Messages(plugin, messages, menus);
        loaded.reload();
        return loaded;
    }

    /** Re-reads the already reloaded files. Reloading the files themselves is the caller's job. */
    public void reload() {
        alreadyReported.clear();
        languages.clear();

        String prefix = messages.data().getString("prefix", "");
        english = new Translations(null);
        english.prefix(prefix);
        english.read(messages.data(), prefix);
        english.read(menus.data(), prefix);

        readTranslations(prefix);
        standard = english;
    }

    /**
     * Which language everybody gets, and whether players get their own instead.
     *
     * <p>Separate from {@link #reload()} because the setting lives in config.yml and the
     * lines live in messages.yml, and the two are read at different moments.
     */
    public void apply(ConfigurationSection language) {
        perPlayer = language.getBoolean("per-player", false);

        String wanted = language.getString("default", "").trim().toLowerCase(Locale.ROOT);
        Translations found = wanted.isEmpty() ? null : languages.get(wanted);
        if (!wanted.isEmpty() && found == null) {
            plugin.getLogger().warning("No messages_" + wanted + ".yml, so English is being used.");
        }
        standard = found != null ? found : english;
    }

    /** The codes of the translations that were found, for the startup line. */
    public List<String> languages() {
        return List.copyOf(new TreeSet<>(languages.keySet()));
    }

    public void send(CommandSender target, String key, String... placeholders) {
        Translations speaking = speaking(target);
        if (speaking.isMuted(key)) {
            return;
        }
        target.sendMessage(render(speaking, key, placeholders));
    }

    /**
     * One line to everybody online, and to the console.
     *
     * <p>Rendered once per language rather than once per player, since a broadcast on a full
     * server is the worst place to parse the same template two hundred times.
     */
    public void broadcast(Server server, String key, String... placeholders) {
        Map<Translations, Component> rendered = new IdentityHashMap<>(2);

        for (Player player : server.getOnlinePlayers()) {
            Translations speaking = speaking(player);
            if (speaking.isMuted(key)) {
                continue;
            }
            player.sendMessage(rendered.computeIfAbsent(speaking,
                    language -> render(language, key, placeholders)));
        }

        if (!standard.isMuted(key)) {
            server.getConsoleSender().sendMessage(rendered.computeIfAbsent(standard,
                    language -> render(language, key, placeholders)));
        }
    }

    /** For lines a command builds itself, such as the clickable teleport buttons. */
    public Component prefix() {
        return standard.prefix();
    }

    /**
     * Parses a line that came from somewhere other than messages.yml, such as a kit name or
     * a custom command, honouring {@code %prefix%} the same way.
     */
    public Component parse(String raw) {
        return TextFormat.parse(raw.replace("%prefix%", standard.rawPrefix()));
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
        String template = standard.template(key);
        if (template == null) {
            return List.of(cached(standard, key));
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
        return render(standard, key, placeholders);
    }

    private Component render(Translations speaking, String key, String... placeholders) {
        if (placeholders.length == 0) {
            return cached(speaking, key);
        }

        String template = speaking.template(key);
        if (template == null) {
            return cached(speaking, key);
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

    /** The language a player reads in, or the server's own for the console. */
    private Translations speaking(CommandSender target) {
        if (!perPlayer || !(target instanceof Player player)) {
            return standard;
        }
        Translations found = languages.get(player.locale().getLanguage().toLowerCase(Locale.ROOT));
        return found != null ? found : standard;
    }

    /**
     * Every messages_xx.yml sitting next to messages.yml.
     *
     * <p>They are found rather than listed, so adding a language is dropping in a file and
     * running a reload. Each one falls back to English, which is where the keys it does not
     * translate come from.
     */
    private void readTranslations(String prefix) {
        for (String shipped : SHIPPED) {
            if (!new File(plugin.getDataFolder(), shipped).exists()) {
                plugin.saveResource(shipped, false);
            }
        }

        File[] found = plugin.getDataFolder().listFiles((folder, name) ->
                name.startsWith(TRANSLATION_PREFIX) && name.endsWith(TRANSLATION_SUFFIX));
        if (found == null) {
            return;
        }

        for (File file : found) {
            String name = file.getName();
            String code = name.substring(TRANSLATION_PREFIX.length(),
                    name.length() - TRANSLATION_SUFFIX.length()).toLowerCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }

            Translations language = new Translations(english);
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            language.prefix(data.getString("prefix", ""));
            language.read(data, data.getString("prefix", prefix));
            languages.put(code, language);
        }
    }

    private Component cached(Translations speaking, String key) {
        Component message = speaking.entry(key);
        if (message != null) {
            return message;
        }
        // A blank template is an admin silencing the message, not a mistake.
        if (speaking.isMuted(key)) {
            return Component.empty();
        }
        if (alreadyReported.add(key)) {
            plugin.getLogger().warning("Missing line '" + key + "' in "
                    + (key.startsWith("menu.") ? "menus.yml" : "messages.yml"));
        }
        return Component.text(key);
    }
}
