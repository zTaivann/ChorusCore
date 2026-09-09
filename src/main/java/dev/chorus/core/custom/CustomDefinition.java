package dev.chorus.core.custom;

import dev.chorus.core.feedback.SoundCue;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One command an admin invented in the config. */
public record CustomDefinition(String name, String description, String permission,
                               List<String> aliases, int cooldownSeconds, List<String> messages,
                               List<String> playerCommands, List<String> consoleCommands,
                               SoundCue sound) {

    public static CustomDefinition read(ConfigurationSection block, String name) {
        List<String> aliases = new ArrayList<>();
        for (String alias : block.getStringList("aliases")) {
            String cleaned = alias.trim().toLowerCase(Locale.ROOT);
            if (!cleaned.isEmpty() && !cleaned.equals(name) && !aliases.contains(cleaned)) {
                aliases.add(cleaned);
            }
        }

        return new CustomDefinition(
                name,
                block.getString("description", "A command from ChorusCore."),
                block.getString("permission", "").trim(),
                aliases,
                Math.max(0, block.getInt("cooldown-seconds", 0)),
                List.copyOf(block.getStringList("messages")),
                List.copyOf(block.getStringList("run-as-player")),
                List.copyOf(block.getStringList("run-as-console")),
                SoundCue.read(block, SoundCue.NONE));
    }

    public boolean doesNothing() {
        return messages.isEmpty() && playerCommands.isEmpty() && consoleCommands.isEmpty();
    }
}
