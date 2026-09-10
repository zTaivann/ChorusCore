package dev.chorus.core.players.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.players.Playtime;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Reads the figure the server already keeps rather than tracking its own, so it never drifts
 * from what the vanilla statistics screen shows. It is per server, not per network.
 */
public final class PlaytimeCommand extends ChorusCommand {

    private static final String OTHERS_PERMISSION = "chorus.players.playtime.others";

    public PlaytimeCommand(CommandSupport support) {
        super(support, "playtime", "chorus.players.playtime");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                messages.send(sender, "error.players-only");
                return;
            }
            if (!ready(sender)) {
                return;
            }
            settle(sender);
            messages.send(sender, "players.playtime", "time", Durations.format(Playtime.of(self)));
            return;
        }

        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            messages.send(sender, "error.no-permission");
            return;
        }

        OfflinePlayer target = known(sender, args[0]);
        if (target == null) {
            return;
        }
        if (!ready(sender)) {
            return;
        }

        settle(sender);
        messages.send(sender, "players.playtime-other",
                "player", target.getName() == null ? args[0] : target.getName(),
                "time", Durations.format(Playtime.of(target)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(OTHERS_PERMISSION)) {
            return List.of();
        }
        return onlineNames(sender, args[0], true);
    }
}
