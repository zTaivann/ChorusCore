package dev.chorus.core.players.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.players.AfkService;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class SeenCommand extends ChorusCommand {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final AfkService afk;

    public SeenCommand(CommandSupport support, AfkService afk) {
        super(support, "seen", "chorus.players.seen");
        this.afk = afk;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "players.seen-usage");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        Player online = sender.getServer().getPlayerExact(args[0]);
        if (online != null && (!(sender instanceof Player viewer) || viewer.canSee(online))) {
            settle(sender);
            messages.send(sender, afk.isAway(online.getUniqueId())
                            ? "players.seen-online-afk"
                            : "players.seen-online",
                    "player", online.getName(),
                    "world", online.getWorld().getName());
            return;
        }

        // Only the cache, never a lookup: asking Mojang for an unknown name would block the
        // server thread on a web request.
        OfflinePlayer offline = sender.getServer().getOfflinePlayerIfCached(args[0]);
        if (offline == null || !offline.hasPlayedBefore()) {
            messages.send(sender, "error.player-not-found", "player", args[0]);
            return;
        }

        settle(sender);
        long lastSeen = offline.getLastSeen();
        messages.send(sender, "players.seen-offline",
                "player", offline.getName() == null ? args[0] : offline.getName(),
                "ago", Durations.format(Math.max(0, System.currentTimeMillis() - lastSeen)),
                "first", DATE.format(Instant.ofEpochMilli(offline.getFirstPlayed())));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? onlineNames(sender, args[0], true) : List.of();
    }
}
