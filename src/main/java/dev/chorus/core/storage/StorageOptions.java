package dev.chorus.core.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public record StorageOptions(SqlDialect dialect, String file, Remote remote) {

    public record Remote(String host, int port, String database, String username,
                         String password, int poolSize, Map<String, String> properties) {
    }

    public static StorageOptions read(ConfigurationSection root) {
        ConfigurationSection storage = childOrEmpty(root, "storage");
        ConfigurationSection remote = childOrEmpty(storage, "mysql");
        ConfigurationSection extras = childOrEmpty(remote, "properties");

        Map<String, String> properties = new LinkedHashMap<>();
        for (String key : extras.getKeys(false)) {
            properties.put(key, String.valueOf(extras.get(key)));
        }

        return new StorageOptions(
                SqlDialect.parse(storage.getString("type", "sqlite")),
                storage.getString("file", "chorus.db"),
                new Remote(
                        remote.getString("host", "localhost"),
                        remote.getInt("port", 3306),
                        remote.getString("database", "chorus"),
                        remote.getString("username", "root"),
                        remote.getString("password", ""),
                        Math.max(1, remote.getInt("pool-size", 8)),
                        Map.copyOf(properties)));
    }

    private static ConfigurationSection childOrEmpty(ConfigurationSection parent, String path) {
        ConfigurationSection child = parent.getConfigurationSection(path);
        return child != null ? child : new MemoryConfiguration();
    }
}
