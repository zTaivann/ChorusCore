package dev.chorus.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Shared access to the files that ship inside the jar. */
public final class Resources {

    public static final Path SOURCES = Path.of("src", "main", "java");
    public static final Path FOLDER = Path.of("src", "main", "resources");

    /** Where every line the plugin says lives, one file per part of the plugin. */
    public static final String MESSAGES = "messages";

    /** Every module file that carries a commands section. */
    public static final List<String> MODULES = List.of(
            "modules/homes.yml", "modules/warps.yml", "modules/spawn.yml", "modules/teleport.yml",
            "modules/utility.yml", "modules/economy.yml", "modules/chat.yml", "modules/staff.yml",
            "modules/players.yml", "modules/items.yml", "modules/kits.yml",
            "modules/world.yml", "modules/shops.yml");

    private Resources() {
    }

    public static YamlConfiguration read(String name) {
        return YamlConfiguration.loadConfiguration(FOLDER.resolve(name).toFile());
    }

    /** The message files inside a folder, by the path {@link #read} takes. */
    public static List<String> messageFiles(String folder) {
        String[] found = FOLDER.resolve(folder).toFile()
                .list((where, name) -> name.endsWith(".yml"));
        if (found == null) {
            return List.of();
        }
        return Arrays.stream(found).sorted().map(name -> folder + "/" + name).toList();
    }

    /** The language folders inside the messages folder, by their code. */
    public static List<String> languages() {
        String[] found = FOLDER.resolve(MESSAGES).toFile().list((where, name) ->
                new java.io.File(where, name).isDirectory());
        if (found == null) {
            return List.of();
        }
        return Arrays.stream(found).sorted().toList();
    }

    /** Never null, so a test never has to branch on a missing section. */
    public static ConfigurationSection section(ConfigurationSection parent, String name) {
        ConfigurationSection child = parent.getConfigurationSection(name);
        return child != null ? child : new MemoryConfiguration();
    }
}
