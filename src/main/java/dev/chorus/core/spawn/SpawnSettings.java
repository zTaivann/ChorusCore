package dev.chorus.core.spawn;

import org.bukkit.configuration.ConfigurationSection;

public record SpawnSettings(boolean teleportOnFirstJoin, boolean teleportOnRespawn,
                            boolean perWorld, String fallbackWorld) {

    public static SpawnSettings read(ConfigurationSection spawn) {
        return new SpawnSettings(
                spawn.getBoolean("teleport-on-first-join", true),
                spawn.getBoolean("teleport-on-respawn", false),
                spawn.getBoolean("per-world", false),
                spawn.getString("fallback-world", "").trim());
    }
}
