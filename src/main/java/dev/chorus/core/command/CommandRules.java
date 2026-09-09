package dev.chorus.core.command;

import dev.chorus.core.feedback.CommandFeedback;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Everything one command's config block controls.
 *
 * <p>Each module file opens its {@code commands} section with a {@code defaults} block, and
 * a command only writes down what it does differently. That is why /craft is four lines
 * instead of fifteen, and why adding an option later means editing one place rather than
 * every command in the file.
 */
public record CommandRules(boolean enabled, int warmupSeconds, int cooldownSeconds, double price,
                           CommandFeedback feedback) {

    public static final CommandRules FREE =
            new CommandRules(true, 0, 0, 0, CommandFeedback.NONE);

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
                CommandFeedback.read(block, base.feedback(), name -> logger.warning(
                        "This server has no particle called '" + name + "', configured for /" + command)));
    }
}
