package dev.chorus.core.chat;

import org.bukkit.configuration.ConfigurationSection;

public record ChatSettings(boolean spyEnabled) {

    public static ChatSettings read(ConfigurationSection chat) {
        return new ChatSettings(chat.getBoolean("spy-enabled", true));
    }
}
