package dev.chorus.core.command;

import dev.chorus.core.feedback.CommandFeedback;
import dev.chorus.core.rules.Action;
import dev.chorus.core.rules.Requirement;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Everything one command's config block controls.
 *
 * @param cooldownGroup the name of the cooldown it shares with other commands, or {@code ""}
 * @param cooldownScope who the cooldown holds back once it starts
 * @param permission    the node it takes instead of its own, {@code ""} for everyone, or null
 * @param log           whether each use goes into the staff log
 */
public record CommandRules(boolean enabled, int warmupSeconds, int cooldownSeconds,
                           String cooldownGroup, CooldownScope cooldownScope, double price,
                           WorldRule worlds, CommandFeedback feedback, Map<String, String> messages,
                           @Nullable String permission, List<Requirement> requires,
                           List<Action> onSuccess, List<Action> onFail, boolean log) {

    public static final CommandRules FREE = new CommandRules(true, 0, 0, "",
            CooldownScope.PLAYER, 0, WorldRule.EVERYWHERE, CommandFeedback.NONE, Map.of(), null,
            List.of(), List.of(), List.of(), false);

    /** Every option a command block takes. */
    public static final Set<String> OPTIONS = Set.of("enabled", "warmup-seconds",
            "cooldown-seconds", "cooldown-group", "cooldown-scope", "price", "worlds", "sound",
            "particle",
            "messages", "permission", "permission-message", "requires", "on-success", "on-fail",
            "log");

    /** The options that belong to one command and are ignored in the defaults block. */
    public static final Set<String> PER_COMMAND = Set.of("permission");

    private static final String DEFAULTS = "defaults";
    private static final String NO_PERMISSION = "error.no-permission";

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

    /** The same rules with another price and cooldown, for a home or a warp that sets its own. */
    public CommandRules costing(double otherPrice, int otherCooldownSeconds) {
        return new CommandRules(enabled, warmupSeconds, otherCooldownSeconds, cooldownGroup,
                cooldownScope, otherPrice, worlds, feedback, messages, permission, requires,
                onSuccess, onFail, log);
    }

    /** The same rules with another wait before a teleport. */
    public CommandRules waiting(int otherWarmupSeconds) {
        return new CommandRules(enabled, otherWarmupSeconds, cooldownSeconds, cooldownGroup,
                cooldownScope, price, worlds, feedback, messages, permission, requires,
                onSuccess, onFail, log);
    }

    private static CommandRules merge(@Nullable ConfigurationSection block, CommandRules base,
                                      String command, Logger logger) {
        if (block == null) {
            return base;
        }
        boolean defaults = command.equals(DEFAULTS);
        String owner = defaults ? "the defaults block" : "/" + command;
        Consumer<String> problem = logger::warning;

        return new CommandRules(
                block.getBoolean("enabled", base.enabled()),
                Math.max(0, block.getInt("warmup-seconds", base.warmupSeconds())),
                Math.max(0, block.getInt("cooldown-seconds", base.cooldownSeconds())),
                group(block, base.cooldownGroup()),
                scope(block, base.cooldownScope(), owner, logger),
                Math.max(0, block.getDouble("price", base.price())),
                WorldRule.read(block, base.worlds()),
                CommandFeedback.read(block, base.feedback(), name -> logger.warning(
                        "This server has no particle called '" + name + "', configured for /" + command)),
                lines(block, base.messages()),
                permission(block, base.permission(), defaults, logger),
                block.contains("requires")
                        ? Requirement.read(block.getList("requires", List.of()), owner, problem)
                        : base.requires(),
                actions(block, "on-success", base.onSuccess(), owner, problem),
                actions(block, "on-fail", base.onFail(), owner, problem),
                block.getBoolean("log", base.log()));
    }

    private static String group(ConfigurationSection block, String base) {
        return block.getString("cooldown-group", base).trim().toLowerCase(Locale.ROOT);
    }

    private static CooldownScope scope(ConfigurationSection block, CooldownScope base,
                                       String owner, Logger logger) {
        String written = block.getString("cooldown-scope");
        if (written == null) {
            return base;
        }
        CooldownScope scope = CooldownScope.of(written);
        if (scope == null) {
            logger.warning(owner + " has a cooldown-scope this plugin does not know: '" + written
                    + "'. It takes player, world or server.");
            return base;
        }
        return scope;
    }

    private static @Nullable String permission(ConfigurationSection block, @Nullable String base,
                                               boolean defaults, Logger logger) {
        if (!block.contains("permission")) {
            return base;
        }
        if (defaults) {
            logger.warning("'permission' in a defaults block is ignored: set it on each command.");
            return base;
        }
        return block.getString("permission", "").trim();
    }

    private static List<Action> actions(ConfigurationSection block, String option,
                                        List<Action> base, String owner, Consumer<String> problem) {
        return block.contains(option)
                ? Action.read(block.getStringList(option), owner, problem)
                : base;
    }

    /** The lines this command says instead of the ones in the messages folder. */
    private static Map<String, String> lines(ConfigurationSection block, Map<String, String> base) {
        ConfigurationSection written = block.getConfigurationSection("messages");
        String denied = block.getString("permission-message", "");
        if (written == null && denied.isEmpty()) {
            return base;
        }
        Map<String, String> merged = new LinkedHashMap<>(base);
        if (written != null) {
            for (String key : written.getKeys(true)) {
                if (!written.isConfigurationSection(key)) {
                    merged.put(key, written.getString(key, ""));
                }
            }
        }
        if (!denied.isEmpty()) {
            merged.put(NO_PERMISSION, denied);
        }
        return Map.copyOf(merged);
    }
}
