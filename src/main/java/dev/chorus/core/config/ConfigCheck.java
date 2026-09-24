package dev.chorus.core.config;

import dev.chorus.core.feedback.ParticleCue;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads the config files and reports what will not work, by file and line. */
public final class ConfigCheck {

    /** Sections a server fills in itself, where a key the jar has never heard of is normal. */
    private static final Set<String> OPEN = Set.of(
            "kits.definitions", "custom", "shops.worth", "items.condense",
            "homes.world-limits", "storage.mysql.properties");

    /** Keys naming a block, an item or a button. */
    private static final Set<String> MATERIALS = Set.of(
            "icon", "filler", "navigation-filler", "previous-page", "next-page", "close",
            "back", "new-kit", "delete", "preview");

    /** Sections whose keys are material names rather than options. */
    private static final Set<String> MATERIAL_KEYS = Set.of("shops.worth.", "items.condense.");

    private static final Pattern SOUND = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)+");

    /** Enough to show what is wrong without burying the console. */
    private static final int MAX_PROBLEMS = 25;

    /** How different two names may be and still be a suggestion. */
    private static final int MAX_DISTANCE = 3;

    private ConfigCheck() {
    }

    /**
     * @param worlds whether to check that the worlds named in the config exist. False at
     *               startup, where a world another plugin creates has not been made yet.
     */
    public static List<ConfigProblem> run(Plugin plugin, Server server, List<String> paths,
                                          boolean worlds) {
        List<ConfigProblem> problems = new ArrayList<>();
        for (String path : paths) {
            if (problems.size() >= MAX_PROBLEMS) {
                break;
            }
            check(plugin, server, path, worlds, problems);
        }
        return List.copyOf(problems);
    }

    private static void check(Plugin plugin, Server server, String path, boolean worlds,
                              List<ConfigProblem> problems) {
        File onDisk = new File(plugin.getDataFolder(), path);
        if (!onDisk.isFile()) {
            return;
        }

        YamlConfiguration mine = YamlConfiguration.loadConfiguration(onDisk);
        YamlConfiguration shipped = ConfigFile.bundled(plugin, path);
        ConfigLines lines = read(onDisk);

        for (String key : mine.getKeys(true)) {
            if (problems.size() >= MAX_PROBLEMS) {
                return;
            }
            if (shipped != null && !isOpen(key)) {
                if (!shipped.contains(key)) {
                    problems.add(new ConfigProblem(path, lines.of(key),
                            "'" + last(key) + "' is not an option.", suggestion(shipped, key)));
                    continue;
                }
                String wrong = mismatch(shipped, mine, key);
                if (wrong != null) {
                    problems.add(new ConfigProblem(path, lines.of(key), wrong));
                    continue;
                }
            }
            value(server, path, key, mine, lines, worlds, problems);
        }

        if (worlds) {
            worlds(server, path, mine, lines, problems);
        }
    }

    /** Whatever a single value has to be, beyond being of the right kind. */
    private static void value(Server server, String path, String key, YamlConfiguration mine,
                              ConfigLines lines, boolean worlds, List<ConfigProblem> problems) {
        String name = last(key);
        String text = mine.isString(key) ? mine.getString(key, "").trim() : null;

        if (namesAMaterial(key) && !isMaterial(name)) {
            problems.add(new ConfigProblem(path, lines.of(key),
                    "'" + name + "' is not a material."));
            return;
        }
        if (text == null || text.isBlank()) {
            return;
        }

        if ((MATERIALS.contains(name) || namesAMaterial(key + ".")) && !isMaterial(text)) {
            problems.add(new ConfigProblem(path, lines.of(key),
                    "'" + text + "' is not a material."));
        } else if (key.endsWith("sound.key") && !SOUND.matcher(text).matches()) {
            problems.add(new ConfigProblem(path, lines.of(key),
                    "'" + text + "' is not shaped like a Minecraft sound name."));
        } else if (key.endsWith("particle.name") && !particleExists(mine, key)) {
            problems.add(new ConfigProblem(path, lines.of(key),
                    "'" + text + "' is not a particle this server knows."));
        } else if (worlds && name.equals("fallback-world") && server.getWorld(text) == null) {
            problems.add(new ConfigProblem(path, lines.of(key),
                    "there is no world called '" + text + "' on this server."));
        }
    }

    /** Every world named in a worlds list, and every world a home limit is set for. */
    private static void worlds(Server server, String path, YamlConfiguration mine,
                               ConfigLines lines, List<ConfigProblem> problems) {
        for (String key : mine.getKeys(true)) {
            if (problems.size() >= MAX_PROBLEMS) {
                return;
            }
            if (last(key).equals("worlds") && mine.isList(key)) {
                for (String world : mine.getStringList(key)) {
                    String wanted = world.startsWith("!") ? world.substring(1) : world;
                    if (!wanted.isBlank() && server.getWorld(wanted) == null) {
                        problems.add(new ConfigProblem(path, lines.of(key),
                                "there is no world called '" + wanted + "' on this server."));
                    }
                }
            }
            if (key.startsWith("homes.world-limits.") && server.getWorld(last(key)) == null) {
                problems.add(new ConfigProblem(path, lines.of(key),
                        "there is no world called '" + last(key) + "' on this server."));
            }
        }
    }

    /** The kind a value is written as, when it is not the kind the option takes. */
    private static @Nullable String mismatch(YamlConfiguration shipped, YamlConfiguration mine,
                                             String key) {
        Object wanted = shipped.get(key);
        Object given = mine.get(key);
        if (wanted == null || given == null) {
            return null;
        }
        String expected = kind(wanted);
        String actual = kind(given);
        if (expected.equals(actual)) {
            return null;
        }
        // A number or a yes written where text is expected is read as text either way.
        if (expected.equals("text") && !actual.equals("a list") && !actual.equals("a block")) {
            return null;
        }
        return "'" + last(key) + "' takes " + expected + ", not " + actual + ".";
    }

    private static String kind(Object value) {
        if (value instanceof Boolean) {
            return "true or false";
        }
        if (value instanceof Number) {
            return "a number";
        }
        if (value instanceof List<?>) {
            return "a list";
        }
        if (value instanceof ConfigurationSection || value instanceof java.util.Map<?, ?>) {
            return "a block";
        }
        return "text";
    }

    /** The option with the closest name to the one that was written, or null. */
    private static @Nullable String suggestion(YamlConfiguration shipped, String key) {
        int dot = key.lastIndexOf('.');
        String parent = dot < 0 ? "" : key.substring(0, dot);
        ConfigurationSection siblings = parent.isEmpty()
                ? shipped : shipped.getConfigurationSection(parent);
        if (siblings == null) {
            return null;
        }

        String wrong = last(key);
        String best = null;
        int closest = MAX_DISTANCE + 1;
        for (String candidate : siblings.getKeys(false)) {
            int distance = distance(wrong, candidate);
            if (distance < closest) {
                closest = distance;
                best = candidate;
            }
        }
        return closest <= MAX_DISTANCE ? best : null;
    }

    private static int distance(String from, String to) {
        int[] previous = new int[to.length() + 1];
        int[] current = new int[to.length() + 1];
        for (int at = 0; at <= to.length(); at++) {
            previous[at] = at;
        }

        for (int row = 1; row <= from.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= to.length(); column++) {
                int swap = from.charAt(row - 1) == to.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + swap);
            }
            int[] used = previous;
            previous = current;
            current = used;
        }
        return previous[to.length()];
    }

    private static boolean particleExists(YamlConfiguration mine, String key) {
        // ParticleCue reads the particle block out of the command block, not out of itself.
        int particle = key.lastIndexOf('.');
        int command = key.lastIndexOf('.', particle - 1);
        ConfigurationSection block = command < 0
                ? mine : mine.getConfigurationSection(key.substring(0, command));
        return block == null
                || ParticleCue.read(block, ParticleCue.NONE, name -> { }).particle() != null;
    }

    /** Whether the key itself is a material name, such as an entry in the worth list. */
    private static boolean namesAMaterial(String key) {
        for (String section : MATERIAL_KEYS) {
            if (key.startsWith(section) && key.indexOf('.', section.length()) < 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMaterial(String name) {
        return Material.matchMaterial(name.toUpperCase(Locale.ROOT)) != null;
    }

    private static boolean isOpen(String key) {
        for (String open : OPEN) {
            if (key.equals(open) || key.startsWith(open + ".")) {
                return true;
            }
        }
        // A messages block inside a command holds message keys, not options.
        int at = key.indexOf(".messages");
        return at >= 0 && (key.length() == at + ".messages".length()
                || key.charAt(at + ".messages".length()) == '.');
    }

    private static String last(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? key : key.substring(dot + 1);
    }

    private static ConfigLines read(File file) {
        try {
            return ConfigLines.of(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            return ConfigLines.of(List.of());
        }
    }
}
