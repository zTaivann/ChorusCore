package dev.chorus.core.config;

import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

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
}
