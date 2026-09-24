package dev.chorus.core.config;

import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Every config file the plugin has opened, so that a reload can refresh them all without
 * the core needing to know which modules exist.
 */
public final class ConfigFiles {

    private final Plugin plugin;
    private final Map<String, ConfigFile> opened = new LinkedHashMap<>();

    public ConfigFiles(Plugin plugin) {
        this.plugin = plugin;
    }

    public ConfigFile get(String path) {
        return opened.computeIfAbsent(path, name -> ConfigFile.load(plugin, name));
    }

    public void reloadAll() {
        opened.values().forEach(ConfigFile::reload);
    }

    /** Only the open files whose path passes. */
    public void reload(Predicate<String> which) {
        opened.forEach((path, file) -> {
            if (which.test(path)) {
                file.reload();
            }
        });
    }

    /** Every file that has been opened, by its path inside the plugin folder. */
    public List<String> paths() {
        return List.copyOf(opened.keySet());
    }
}
