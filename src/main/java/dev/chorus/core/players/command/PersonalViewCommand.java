package dev.chorus.core.players.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Shared groundwork for the commands shaped {@code /command <value> [player]}, where the
 * value changes only what one client is shown and nothing on the server itself.
 */
abstract class PersonalViewCommand extends ChorusCommand {

    private final String othersPermission;

    protected PersonalViewCommand(CommandSupport support, String name, String permission) {
        super(support, name, permission);
        this.othersPermission = permission + ".others";
    }

    @Override
    protected final void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, usageKey());
            return;
        }

        Player target;
        if (args.length > 1) {
            if (!sender.hasPermission(othersPermission)) {
                messages.send(sender, "error.no-permission");
                return;
            }
            target = online(sender, args[1]);
            if (target == null) {
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            messages.send(sender, "error.players-only");
            return;
        }

        apply(sender, target, args[0].toLowerCase(Locale.ROOT));
    }

    /** The message shown when no value was given. */
    protected abstract String usageKey();

    /** The values tab completion offers, which are also the ones {@link #apply} accepts. */
    protected abstract List<String> values();

    protected abstract void apply(CommandSender sender, Player target, String value);

    /**
     * Says it once, to whoever it applies to, and again to the sender when they differ.
     *
     * <p>Both keys are named in full rather than one being built from the other, so the check
     * that every key in messages.yml is reachable can actually see them.
     */
    protected final void report(CommandSender sender, Player target,
                                String toTarget, String toSender, String value) {
        messages.send(target, toTarget, "value", value);
        if (!target.equals(sender)) {
            messages.send(sender, toSender, "player", target.getName(), "value", value);
        }
    }

    @Override
    public final List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                            @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], values());
        }
        if (args.length == 2 && sender.hasPermission(othersPermission)) {
            return onlineNames(sender, args[1], true);
        }
        return List.of();
    }
}
