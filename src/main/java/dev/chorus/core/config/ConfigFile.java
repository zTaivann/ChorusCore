package dev.chorus.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/** One YAML file in the plugin folder, backed by the copy shipped in the jar. */
public final class ConfigFile {

    private final Plugin plugin;
    private final String path;

    private volatile YamlConfiguration data;

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

    /** The copy of a file that ships inside the jar, or null when there is none. */
    public static @Nullable YamlConfiguration bundled(Plugin plugin, String path) {
        try (InputStream inside = plugin.getResource(path)) {
            if (inside == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(inside, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            return null;
        }
    }

    /** Never null, so callers do not need a branch for a section an admin deleted. */
    public ConfigurationSection section(String name) {
        ConfigurationSection section = data.getConfigurationSection(name);
        return section != null ? section : new MemoryConfiguration();
    }
}
