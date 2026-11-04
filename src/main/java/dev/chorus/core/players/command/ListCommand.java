package dev.chorus.core.players.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.players.AfkService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Who is online. Players hidden from the sender are left out of the names and the count.
 */
public final class ListCommand extends ChorusCommand {

    private final AfkService afk;

    public ListCommand(CommandSupport support, AfkService afk) {
        super(support, "list", "chorus.players.list");
        this.afk = afk;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!ready(sender)) {
            return;
        }

        List<String> names = new ArrayList<>();
        int away = 0;
        for (Player online : sender.getServer().getOnlinePlayers()) {
            if (sender instanceof Player viewer && !viewer.canSee(online)) {
                continue;
            }
            if (afk.isAway(online.getUniqueId())) {
                away++;
            }
            names.add(online.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);

        settle(sender);
        messages.send(sender, "players.list-header",
                "online", String.valueOf(names.size()),
                "max", String.valueOf(sender.getServer().getMaxPlayers()),
                "away", String.valueOf(away));
        if (names.isEmpty()) {
            messages.send(sender, "players.list-empty");
            return;
        }
        messages.send(sender, "players.list-names", "players", String.join(", ", names));
    }
}
