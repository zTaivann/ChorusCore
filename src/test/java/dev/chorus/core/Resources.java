package dev.chorus.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.List;

/** Shared access to the files that ship inside the jar. */
public final class Resources {

    public static final Path SOURCES = Path.of("src", "main", "java");
    public static final Path FOLDER = Path.of("src", "main", "resources");

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

    /** Never null, so a test never has to branch on a missing section. */
    public static ConfigurationSection section(ConfigurationSection parent, String name) {
        ConfigurationSection child = parent.getConfigurationSection(name);
        return child != null ? child : new MemoryConfiguration();
    }
}
