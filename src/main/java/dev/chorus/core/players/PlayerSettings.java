package dev.chorus.core.players;

import org.bukkit.configuration.ConfigurationSection;

public record PlayerSettings(int autoAfkMinutes, int kickAfterMinutes, boolean broadcast) {

    public static PlayerSettings read(ConfigurationSection players) {
        return new PlayerSettings(
                Math.max(0, players.getInt("auto-afk-minutes", 5)),
                Math.max(0, players.getInt("kick-after-minutes", 0)),
                players.getBoolean("broadcast", true));
    }

    public boolean autoAfk() {
        return autoAfkMinutes > 0;
    }

    public boolean kicks() {
        return kickAfterMinutes > 0;
    }
}
