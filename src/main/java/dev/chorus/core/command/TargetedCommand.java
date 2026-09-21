package dev.chorus.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** A command that acts on the sender, or on someone else when a name is given. */
public abstract class TargetedCommand extends ChorusCommand {

    private final String othersPermission;

    protected TargetedCommand(CommandSupport support, String name, String permission) {
        super(support, name, permission);
        this.othersPermission = permission + ".others";
    }

    @Override
    protected final void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player self) {
                onPlayer(self, () -> execute(sender, self));
            } else {
                messages.send(sender, "error.players-only");
            }
            return;
        }

        if (!sender.hasPermission(othersPermission)) {
            messages.send(sender, "error.no-permission");
            return;
        }

        Player target = sender.getServer().getPlayerExact(args[0]);
        if (target == null || (sender instanceof Player viewer && !viewer.canSee(target))) {
            messages.send(sender, "error.player-not-found", "player", args[0]);
            return;
        }
        onPlayer(target, () -> execute(sender, target));
    }

    protected abstract void execute(CommandSender sender, Player target);

    /**
     * Sends the right one of the three ways this can read: to yourself, about someone else,
     * or to the player it was done to.
     */
    protected final void announce(CommandSender sender, Player target,
                                  String toSelf, String toSender, String toTarget) {
        if (target.equals(sender)) {
            messages.send(sender, toSelf);
            return;
        }
        messages.send(sender, toSender, "player", target.getName());
        messages.send(target, toTarget, "player", sender.getName());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(othersPermission)) {
            return List.of();
        }

        return onlineNames(sender, args[args.length - 1], true);
    }
}
