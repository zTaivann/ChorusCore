package dev.chorus.core.command;

import dev.chorus.core.feedback.CommandFeedback;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/** Everything one command's config block controls. */
public record CommandRules(boolean enabled, int warmupSeconds, int cooldownSeconds, double price,
                           WorldRule worlds, CommandFeedback feedback,
                           Map<String, String> messages) {

    public static final CommandRules FREE =
            new CommandRules(true, 0, 0, 0, WorldRule.EVERYWHERE, CommandFeedback.NONE, Map.of());

    private static final String DEFAULTS = "defaults";

    public static CommandRules read(ConfigurationSection commands, String command, Logger logger) {
        CommandRules defaults = merge(commands.getConfigurationSection(DEFAULTS), FREE, DEFAULTS, logger);
        return merge(commands.getConfigurationSection(command), defaults, command, logger);
    }

    /** Hands every command in a module the block named after it in that module's config. */
    public static void applyAll(ConfigurationSection commands,
                                Collection<? extends ChorusCommand> targets, Logger logger) {
        CommandRules defaults = merge(commands.getConfigurationSection(DEFAULTS), FREE, DEFAULTS, logger);
        targets.forEach(command -> command.apply(
                merge(commands.getConfigurationSection(command.name()), defaults, command.name(), logger)));
    }

    private static CommandRules merge(@Nullable ConfigurationSection block, CommandRules base,
                                      String command, Logger logger) {
        if (block == null) {
            return base;
        }
        return new CommandRules(
                block.getBoolean("enabled", base.enabled()),
                Math.max(0, block.getInt("warmup-seconds", base.warmupSeconds())),
                Math.max(0, block.getInt("cooldown-seconds", base.cooldownSeconds())),
                Math.max(0, block.getDouble("price", base.price())),
                WorldRule.read(block, base.worlds()),
                CommandFeedback.read(block, base.feedback(), name -> logger.warning(
                        "This server has no particle called '" + name + "', configured for /" + command)),
                lines(block, base.messages()));
    }

    /** The lines this command says instead of the ones in the messages folder. */
    private static Map<String, String> lines(ConfigurationSection block, Map<String, String> base) {
        ConfigurationSection written = block.getConfigurationSection("messages");
        if (written == null) {
            return base;
        }
        Map<String, String> merged = new LinkedHashMap<>(base);
        for (String key : written.getKeys(true)) {
            if (!written.isConfigurationSection(key)) {
                merged.put(key, written.getString(key, ""));
            }
        }
        return Map.copyOf(merged);
    }
}
