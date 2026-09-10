package dev.chorus.core.command;

import dev.chorus.core.locale.Messages;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Base for every command in the plugin.
 *
 * <p>The permission is checked here rather than in plugin.yml. Bukkit refuses a command
 * declared with a permission before the executor ever runs, which would replace the message
 * in messages.yml with its own.
 */
public abstract class ChorusCommand implements CommandExecutor, TabCompleter {

    protected final Messages messages;
    protected final ActionGuard guard;

    private final String name;
    private final String permission;

    private volatile CommandRules rules = CommandRules.FREE;

    protected ChorusCommand(CommandSupport support, String name, @Nullable String permission) {
        this.messages = support.messages();
        this.guard = support.guard();
        this.name = name;
        this.permission = permission;
    }

    /** Matches the entry in plugin.yml, in aliases.yml and in the module's config file. */
    public final String name() {
        return name;
    }

    public final void apply(CommandRules updated) {
        this.rules = updated;
    }

    protected final CommandRules rules() {
        return rules;
    }

    protected final boolean allowed(CommandSender sender) {
        return permission == null || sender.hasPermission(permission);
    }

    /**
     * Cooldown and price check. Call it once the arguments are known good, so a typo never
     * costs anything. The console is never charged and never waits.
     */
    protected final boolean ready(CommandSender sender) {
        return ready(sender, name, rules);
    }

    /**
     * The same check against a rule set worked out at the time and a cooldown of its own.
     * A warp with its own price and its own wait needs both: the command block still holds
     * what /warp costs in general, and this holds what that one warp costs.
     */
    protected final boolean ready(CommandSender sender, String key, CommandRules against) {
        return !(sender instanceof Player player) || guard.allow(player, key, against);
    }

    /**
     * Starts the cooldown, takes the money and plays the sound. Call it only once the action
     * really happened, which for a teleport means on arrival rather than on the command.
     */
    protected final void settle(CommandSender sender) {
        settle(sender, name, rules);
    }

    protected final void settle(CommandSender sender, String key, CommandRules against) {
        if (sender instanceof Player player) {
            guard.charge(player, key, against);
            against.feedback().play(player);
        }
    }

    @Override
    public final boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                   @NotNull String label, @NotNull String[] args) {
        if (!allowed(sender)) {
            messages.send(sender, "error.no-permission");
            return true;
        }
        if (!rules.enabled()) {
            messages.send(sender, "error.command-disabled");
            return true;
        }
        run(sender, args);
        return true;
    }

    protected abstract void run(CommandSender sender, String[] args);

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return List.of();
    }

    protected static List<String> startingWith(String input, Collection<String> candidates) {
        String typed = input.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(candidate -> candidate.startsWith(typed)).sorted().toList();
    }

    /**
     * Looks up a player who may not be online, reporting it and returning null when there is
     * nobody by that name.
     *
     * <p>Someone online is answered straight away: being here is proof enough that they
     * exist, and asking {@code hasPlayedBefore} about them would say no on their very first
     * session, which is how a brand new player becomes invisible to half the plugin.
     *
     * <p>Never a lookup with Mojang. That is a web request, and these commands run on the
     * server thread.
     */
    protected final @Nullable OfflinePlayer known(CommandSender sender, String name) {
        Player online = sender.getServer().getPlayerExact(name);
        if (online != null && !(sender instanceof Player viewer && !viewer.canSee(online))) {
            return online;
        }
        OfflinePlayer offline = sender.getServer().getOfflinePlayerIfCached(name);
        if (offline == null || !offline.hasPlayedBefore()) {
            messages.send(sender, "error.player-not-found", "player", name);
            return null;
        }
        return offline;
    }

    /** Looks up an online player, reporting it and returning null when there is none. */
    protected final @Nullable Player online(CommandSender sender, String name) {
        Player target = sender.getServer().getPlayerExact(name);
        if (target == null || (sender instanceof Player viewer && !viewer.canSee(target))) {
            messages.send(sender, "error.player-not-found", "player", name);
            return null;
        }
        return target;
    }

    /**
     * Names for tab completion, filtered while iterating rather than after. On a busy server
     * this runs on every keystroke and there is no reason to build a list of everyone first.
     */
    protected static List<String> onlineNames(CommandSender sender, String input, boolean includeSelf) {
        String typed = input.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player online : sender.getServer().getOnlinePlayers()) {
            if (sender instanceof Player viewer && (!viewer.canSee(online)
                    || (!includeSelf && viewer.equals(online)))) {
                continue;
            }
            String name = online.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                names.add(name);
            }
        }
        names.sort(null);
        return names;
    }
}
