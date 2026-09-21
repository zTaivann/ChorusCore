package dev.chorus.core.locale;

import dev.chorus.core.api.MessageApi;
import dev.chorus.core.config.ConfigFiles;
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/** Reads the messages folder and hands out ready-to-send components. */
public final class Messages implements MessageApi {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Placeholders are written {@code %name%} in the file and become {@code <chorus_name>}
     * for MiniMessage. The prefix is what keeps a placeholder called "key" or "lang" from
     * being mistaken for the MiniMessage tag of the same name.
     */
    private static final String SLOT_PREFIX = "chorus_";

    private static final String FOLDER = "messages";
    private static final String SUFFIX = ".yml";
    private static final String PALETTE = "palette.yml";
    private static final String PREFIX_KEY = "prefix";

    /** The files that travel inside the jar, written out on first start. */
    static final List<String> FILES = List.of(
            "core.yml", "chat.yml", "economy.yml", "homes.yml", "items.yml", "kits.yml",
            "menus.yml", "players.yml", "shops.yml", "spawn.yml", "staff.yml", "teleport.yml",
            "utility.yml", "warps.yml", "world.yml");

    /** One line per language somebody has contributed. */
    static final List<String> SHIPPED_LANGUAGES = List.of("es");

    /** The two files the plugin used before the folder, still read while they are there. */
    private static final String LEGACY_MESSAGES = "messages.yml";
    private static final List<String> LEGACY = List.of(LEGACY_MESSAGES, "menus.yml");

    private static final String LEGACY_LANGUAGE_PREFIX = "messages_";

    /** Null on a view, which reads everything from the plugin's own. */
    private final @Nullable Plugin plugin;
    private final @Nullable ConfigFiles configs;
    private final @Nullable Messages source;

    private final Set<String> alreadyReported = new HashSet<>();

    /** Every translation found on disk, by its language code. */
    private final Map<String, Translations> languages = new HashMap<>();

    /** Lines one command was given instead of the ones in the files, already made ready. */
    private volatile Map<String, String> overrides = Map.of();

    private volatile Palette palette = Palette.EMPTY;
    private Translations english = new Translations(null, Palette.EMPTY);

    /** What the console and anybody with no language of their own gets. */
    private volatile Translations standard = english;
    private volatile boolean perPlayer;

    private Messages(@Nullable Plugin plugin, @Nullable ConfigFiles configs,
                     @Nullable Messages source) {
        this.plugin = plugin;
        this.configs = configs;
        this.source = source;
    }

    public static Messages load(Plugin plugin, ConfigFiles configs) {
        Messages loaded = new Messages(plugin, configs, null);
        loaded.reload();
        return loaded;
    }

    /** The same lines, with room for a few of its own. */
    public Messages forCommand() {
        return new Messages(null, null, root());
    }

    /** The lines this view says instead of the ones in the files. A blank one says nothing. */
    public void override(Map<String, String> lines) {
        if (lines.isEmpty()) {
            overrides = Map.of();
            return;
        }
        Messages root = root();
        Map<String, String> ready = new LinkedHashMap<>(lines.size());
        lines.forEach((key, raw) -> ready.put(key, root.ready(raw)));
        overrides = Map.copyOf(ready);
    }

    /** Re-reads the already reloaded files. Reloading the files themselves is the caller's job. */
    public void reload() {
        alreadyReported.clear();
        languages.clear();
        palette = Palette.read(configs().get(PALETTE).data(), loop -> logger().warning(
                "The colour " + loop + " in " + PALETTE + " stands for itself"));
        writeOutShipped();

        english = new Translations(null, palette);
        String shared = palette.apply(sharedPrefix());
        english.prefix(shared);
        readInto(english, FOLDER, shared);
        readLegacyInto(english, shared);
        readTranslations(shared);

        standard = english;
    }

    /** Which language everybody gets, and whether players get their own instead. */
    public void apply(ConfigurationSection language) {
        perPlayer = language.getBoolean("per-player", false);

        String wanted = language.getString("default", "").trim().toLowerCase(Locale.ROOT);
        Translations found = wanted.isEmpty() ? null : languages.get(wanted);
        if (!wanted.isEmpty() && found == null) {
            logger().warning("There is no messages/" + wanted + " folder, so English is being used.");
        }
        standard = found != null ? found : english;
    }

    /** The codes of the translations that were found, for the startup line. */
    public List<String> languages() {
        return List.copyOf(new TreeSet<>(root().languages.keySet()));
    }

    @Override
    public void send(CommandSender target, String key, String... placeholders) {
        String custom = overrides.get(key);
        if (custom != null) {
            if (!custom.isEmpty()) {
                target.sendMessage(fill(custom, placeholders));
            }
            return;
        }

        Translations speaking = speaking(target);
        if (speaking.isMuted(key)) {
            return;
        }
        target.sendMessage(render(speaking, key, placeholders));
    }

    /** What {@link #send} would have sent, in that reader's own language. */
    public Component render(CommandSender target, String key, String... placeholders) {
        String custom = overrides.get(key);
        if (custom != null) {
            return custom.isEmpty() ? Component.empty() : fill(custom, placeholders);
        }

        Translations speaking = speaking(target);
        return speaking.isMuted(key) ? Component.empty() : render(speaking, key, placeholders);
    }

    /** One line to everybody online, and to the console. */
    public void broadcast(Server server, String key, String... placeholders) {
        String custom = overrides.get(key);
        if (custom != null) {
            if (!custom.isEmpty()) {
                server.sendMessage(fill(custom, placeholders));
            }
            return;
        }

        Map<Translations, Component> rendered = new IdentityHashMap<>(2);
        for (Player player : server.getOnlinePlayers()) {
            Translations speaking = speaking(player);
            if (speaking.isMuted(key)) {
                continue;
            }
            player.sendMessage(rendered.computeIfAbsent(speaking,
                    language -> render(language, key, placeholders)));
        }

        Translations console = root().standard;
        if (!console.isMuted(key)) {
            server.getConsoleSender().sendMessage(rendered.computeIfAbsent(console,
                    language -> render(language, key, placeholders)));
        }
    }

    /** For lines a command builds itself, such as the clickable teleport buttons. */
    @Override
    public Component prefix() {
        return root().standard.prefix();
    }

    /**
     * Parses a line that came from somewhere other than the messages folder, such as a kit
     * action or a custom command, honouring {@code %prefix%} and the palette the same way.
     */
    public Component parse(String raw) {
        return MINI_MESSAGE.deserialize(root().ready(raw));
    }

    /**
     * A message as plain text, for the small words that end up inside another message —
     * "no limit", "free" and "none".
     */
    public String plain(String key, String... placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(render(key, placeholders));
    }

    /** A message as separate lines, split where the template says {@code <newline>}. */
    public List<Component> renderLines(String key, String... placeholders) {
        String template = template(key);
        if (template == null) {
            return List.of(cached(root().standard, key));
        }
        return fillLines(template, placeholders);
    }

    /** The same, with one value drawn rather than written out. */
    public List<Component> renderLines(String key, String slot, Component value,
                                       String... placeholders) {
        String template = template(key);
        if (template == null) {
            return List.of(cached(root().standard, key));
        }
        String tag = SLOT_PREFIX + slot;
        return fillLines(template.replace("%" + slot + "%", "<" + tag + ">"),
                Placeholder.component(tag, value), placeholders);
    }

    @Override
    public Component render(String key, String... placeholders) {
        String custom = overrides.get(key);
        if (custom != null) {
            return custom.isEmpty() ? Component.empty() : fill(custom, placeholders);
        }
        return render(root().standard, key, placeholders);
    }

    /** Package-private so the checks can exercise the split without standing up a server. */
    static List<Component> fillLines(String template, String... placeholders) {
        return fillLines(template, TagResolver.empty(), placeholders);
    }

    private static List<Component> fillLines(String template, TagResolver extra,
                                             String... placeholders) {
        String[] parts = template.split("<newline>", -1);
        List<Component> lines = new ArrayList<>(parts.length);
        for (String part : parts) {
            lines.add(fill(part, extra, placeholders));
        }
        return lines;
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
        return fill(template, TagResolver.empty(), placeholders);
    }

    private static Component fill(String template, TagResolver extra, String... placeholders) {
        // Resolved during the parse: a gradient splits a value into one component per character.
        // The tags are named after the placeholders passed, so an unfilled %word% is left.
        TagResolver.Builder resolvers = TagResolver.builder();
        resolvers.resolver(extra);
        String filled = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String slot = SLOT_PREFIX + placeholders[i];
            filled = filled.replace("%" + placeholders[i] + "%", "<" + slot + ">");
            resolvers.resolver(Placeholder.unparsed(slot, placeholders[i + 1]));
        }
        return MINI_MESSAGE.deserialize(filled, resolvers.build());
    }

    /** This view's line for a key, or the one the language chain has. */
    private @Nullable String template(String key) {
        String custom = overrides.get(key);
        return custom != null ? custom : root().standard.template(key);
    }

    /** Admin-written text, made ready the way a line from a file is. Root only. */
    private String ready(String raw) {
        return TextFormat.toTags(palette.apply(raw.replace("%prefix%", standard.rawPrefix())));
    }

    /** The plugin's own, from a view. */
    private Messages root() {
        return source == null ? this : source;
    }

    /** The language a player reads in, or the server's own for the console. */
    private Translations speaking(CommandSender target) {
        Messages root = root();
        if (!root.perPlayer || !(target instanceof Player player)) {
            return root.standard;
        }
        Translations found =
                root.languages.get(player.locale().getLanguage().toLowerCase(Locale.ROOT));
        return found != null ? found : root.standard;
    }

    /** What {@code %prefix%} means in a file that does not set one of its own. */
    private String sharedPrefix() {
        File legacy = new File(plugin().getDataFolder(), LEGACY_MESSAGES);
        if (legacy.exists()) {
            String own = prefixIn(YamlConfiguration.loadConfiguration(legacy));
            if (!own.isEmpty()) {
                return own;
            }
        }
        return prefixIn(configs().get(FOLDER + "/core.yml").data());
    }

    /** The folder as it ships, for a server that has just installed the plugin. */
    private void writeOutShipped() {
        List<String> paths = new ArrayList<>(FILES.size() * (SHIPPED_LANGUAGES.size() + 1));
        FILES.forEach(file -> paths.add(FOLDER + "/" + file));
        SHIPPED_LANGUAGES.forEach(code ->
                FILES.forEach(file -> paths.add(FOLDER + "/" + code + "/" + file)));

        for (String path : paths) {
            if (!new File(plugin().getDataFolder(), path).exists()
                    && plugin().getResource(path) != null) {
                plugin().saveResource(path, false);
            }
        }
    }

    /**
     * Every YAML file directly inside one of the plugin's own folders.
     *
     * @param path the folder inside the plugin's folder, which is also where the jar keeps
     *             the copies these fall back to.
     */
    private void readInto(Translations language, String path, String shared) {
        for (String name : found(new File(plugin().getDataFolder(), path))) {
            YamlConfiguration data = configs().get(path + "/" + name).data();
            language.read(data, prefixOr(data, shared));
        }
    }

    /** The same for a folder read straight off disk. */
    private void readInto(Translations language, File folder, String shared) {
        for (String name : found(folder)) {
            YamlConfiguration data =
                    YamlConfiguration.loadConfiguration(new File(folder, name));
            language.read(data, prefixOr(data, shared));
        }
    }

    /**
     * The files the plugin used before the folder, read last so that a server which has not
     * moved its lines across yet still sees them.
     */
    private void readLegacyInto(Translations language, String shared) {
        for (String name : LEGACY) {
            File file = new File(plugin().getDataFolder(), name);
            if (!file.exists()) {
                continue;
            }
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            language.read(data, prefixOr(data, shared));
            logger().info(name + " is still being read, and its lines win over the ones in "
                    + FOLDER + "/. Delete it once you have moved across what you changed.");
        }
    }

    /** Every folder inside the messages folder, one per language. */
    private void readTranslations(String shared) {
        File[] inside = new File(plugin().getDataFolder(), FOLDER).listFiles(File::isDirectory);
        if (inside != null) {
            for (File folder : inside) {
                Translations language = language(folder.getName().toLowerCase(Locale.ROOT));
                List<String> names = found(folder);
                String own = firstPrefix(folder, names);
                if (!own.isEmpty()) {
                    language.prefix(own);
                }
                readInto(language, folder, own.isEmpty() ? shared : palette.apply(own));
            }
        }

        File[] legacy = plugin().getDataFolder().listFiles((folder, name) ->
                name.startsWith(LEGACY_LANGUAGE_PREFIX) && name.endsWith(SUFFIX));
        if (legacy == null) {
            return;
        }
        for (File file : legacy) {
            String name = file.getName();
            String code = name.substring(LEGACY_LANGUAGE_PREFIX.length(),
                    name.length() - SUFFIX.length()).toLowerCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            Translations language = language(code);
            String prefix = prefixIn(data);
            if (!prefix.isEmpty()) {
                language.prefix(prefix);
            }
            language.read(data, prefix.isEmpty() ? shared : palette.apply(prefix));
            logger().info(name + " is still being read. Its lines belong in "
                    + FOLDER + "/" + code + "/ now.");
        }
    }

    private Translations language(String code) {
        return languages.computeIfAbsent(code, any -> new Translations(english, palette));
    }

    /** A translation's own prefix, which is then the one the rest of its files use. */
    private static String firstPrefix(File folder, List<String> names) {
        for (String name : names) {
            String prefix = prefixIn(YamlConfiguration.loadConfiguration(new File(folder, name)));
            if (!prefix.isEmpty()) {
                return prefix;
            }
        }
        return "";
    }

    /** The YAML files directly inside a folder, in a settled order. */
    private static List<String> found(File folder) {
        String[] names = folder.list((where, name) ->
                name.endsWith(SUFFIX) && new File(where, name).isFile());
        if (names == null) {
            return List.of();
        }
        List<String> sorted = new ArrayList<>(Arrays.asList(names));
        sorted.sort(null);
        return sorted;
    }

    private static String prefixIn(ConfigurationSection data) {
        return data.getString(PREFIX_KEY, "");
    }

    /** What {@code %prefix%} means in one file: its own, or the one everything shares. */
    private String prefixOr(ConfigurationSection data, String shared) {
        String own = prefixIn(data);
        return own.isEmpty() ? shared : palette.apply(own);
    }

    private Plugin plugin() {
        Plugin owner = root().plugin;
        if (owner == null) {
            throw new IllegalStateException("A view of the messages has no plugin of its own");
        }
        return owner;
    }

    private ConfigFiles configs() {
        ConfigFiles files = root().configs;
        if (files == null) {
            throw new IllegalStateException("A view of the messages has no files of its own");
        }
        return files;
    }

    private Logger logger() {
        return plugin().getLogger();
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
        Messages root = root();
        if (root.alreadyReported.add(key)) {
            root.logger().warning("Missing line '" + key + "' in the " + FOLDER + " folder");
        }
        return Component.text(key);
    }
}
