package dev.chorus.core.command;

import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.Schedulers;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Base for every command in the plugin. */
public abstract class ChorusCommand implements CommandExecutor, TabCompleter {

    private static final String WORLD_BYPASS = "chorus.bypass.worlds";

    /**
     * This command's own view of the messages, so that a { messages} block in its
     * config can replace a line for this command without touching it anywhere else.
     */
    protected final Messages messages;
    protected final ActionGuard guard;
    protected final Schedulers schedulers;

    private final String name;
    private final String permission;

    private volatile CommandRules rules = CommandRules.FREE;

    protected ChorusCommand(CommandSupport support, String name, @Nullable String permission) {
        this.messages = support.messages().forCommand();
        this.guard = support.guard();
        this.schedulers = support.schedulers();
        this.name = name;
        this.permission = permission;
    }

    /** Runs work that touches a player on the thread that owns them, at once when this is it. */
    protected final void onPlayer(Entity who, Runnable action) {
        schedulers.withEntity(who, action);
    }

    /** The same, for work that touches blocks or spawns something at a place. */
    protected final void atPlace(Location where, Runnable action) {
        schedulers.region(where, action);
    }

    /** Matches the entry in plugin.yml, in aliases.yml and in the module's config file. */
    public final String name() {
        return name;
    }

    public final void apply(CommandRules updated) {
        this.rules = updated;
        messages.override(updated.messages());
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
            schedulers.withEntity(player, () -> against.feedback().play(player));
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
        if (sender instanceof Player player && !player.hasPermission(WORLD_BYPASS)
                && !rules.worlds().allows(player.getWorld().getName())) {
            messages.send(sender, "error.command-world");
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

    /** Names for tab completion, filtered while iterating rather than after. */
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
