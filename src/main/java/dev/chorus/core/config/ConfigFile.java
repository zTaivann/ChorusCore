package dev.chorus.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * One YAML file in the plugin folder, backed by the copy shipped in the jar.
 *
 * <p>Keys added by a later version resolve against the bundled defaults, so an admin never
 * has to delete their file after an update to pick up new options.
 */
public final class ConfigFile {

    private final Plugin plugin;
    private final String path;

    private YamlConfiguration data;

    private ConfigFile(Plugin plugin, String path) {
        this.plugin = plugin;
        this.path = path;
    }

    static ConfigFile load(Plugin plugin, String path) {
        ConfigFile file = new ConfigFile(plugin, path);
        file.reload();
        return file;
    }

    public void reload() {
        File onDisk = new File(plugin.getDataFolder(), path);
        if (!onDisk.exists()) {
            plugin.saveResource(path, false);
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(onDisk);
        try (InputStream bundled = plugin.getResource(path)) {
            if (bundled != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8)));
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled " + path, exception);
        }
        this.data = loaded;
    }

    /**
     * Writes the file back out.
     *
     * <p>Only for the parts of the config a command edits, such as the kits. Comments live
     * with the key above them and survive as long as that key does, so the file a person
     * wrote stays a file a person can read.
     *
     * @return whether it was written.
     */
    public boolean save() {
        try {
            data.save(new File(plugin.getDataFolder(), path));
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not write " + path, exception);
            return false;
        }
    }

    public YamlConfiguration data() {
        return data;
    }

    /** Never null, so callers do not need a branch for a section an admin deleted. */
    public ConfigurationSection section(String name) {
        ConfigurationSection section = data.getConfigurationSection(name);
        return section != null ? section : new MemoryConfiguration();
    }
}
