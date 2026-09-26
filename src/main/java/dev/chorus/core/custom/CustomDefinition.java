package dev.chorus.core.custom;

import dev.chorus.core.feedback.SoundCue;
import dev.chorus.core.rules.Action;
import dev.chorus.core.rules.Requirement;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** One command an admin invented in the config. */
public record CustomDefinition(String name, String description, String permission,
                               String permissionMessage, List<String> aliases,
                               int cooldownSeconds, String cooldownGroup, List<String> messages,
                               List<String> playerCommands, List<String> consoleCommands,
                               SoundCue sound, List<Requirement> requires,
                               List<Action> onSuccess, List<Action> onFail, boolean log) {

    public static CustomDefinition read(ConfigurationSection block, String name,
                                        Consumer<String> onProblem) {
        List<String> aliases = new ArrayList<>();
        for (String alias : block.getStringList("aliases")) {
            String cleaned = alias.trim().toLowerCase(Locale.ROOT);
            if (!cleaned.isEmpty() && !cleaned.equals(name) && !aliases.contains(cleaned)) {
                aliases.add(cleaned);
            }
        }

        String owner = "/" + name;
        return new CustomDefinition(
                name,
                block.getString("description", "A command from ChorusCore."),
                block.getString("permission", "").trim(),
                block.getString("permission-message", ""),
                aliases,
                Math.max(0, block.getInt("cooldown-seconds", 0)),
                block.getString("cooldown-group", "").trim().toLowerCase(Locale.ROOT),
                List.copyOf(block.getStringList("messages")),
                List.copyOf(block.getStringList("run-as-player")),
                List.copyOf(block.getStringList("run-as-console")),
                SoundCue.read(block, SoundCue.NONE),
                Requirement.read(block.getList("requires", List.of()), owner, onProblem),
                Action.read(block.getStringList("on-success"), owner, onProblem),
                Action.read(block.getStringList("on-fail"), owner, onProblem),
                block.getBoolean("log", false));
    }

    public boolean doesNothing() {
        return messages.isEmpty() && playerCommands.isEmpty() && consoleCommands.isEmpty()
                && onSuccess.isEmpty();
    }
}
