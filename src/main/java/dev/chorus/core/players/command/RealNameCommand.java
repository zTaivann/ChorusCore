package dev.chorus.core.players.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.players.PlayerProfiles;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/** {@code /realname <nickname>}: the username behind a nickname. */
public final class RealNameCommand extends ChorusCommand {

    private final PlayerProfiles profiles;

    public RealNameCommand(CommandSupport support, PlayerProfiles profiles) {
        super(support, "realname", "chorus.players.realname");
        this.profiles = profiles;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "players.realname-usage");
            return;
        }
        if (!(sender instanceof Player asker)) {
            messages.send(sender, "error.players-only");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        Optional<Player> found = profiles.byNickname(asker, args[0]);
        if (found.isEmpty() || !asker.canSee(found.get())) {
            messages.send(sender, "players.realname-unknown", "nickname", args[0]);
            return;
        }

        settle(sender);
        messages.send(sender, "players.realname",
                "nickname", args[0], "player", found.get().getName());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}
