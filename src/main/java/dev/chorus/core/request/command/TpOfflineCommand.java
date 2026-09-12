package dev.chorus.core.request.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.players.PlayerProfile;
import dev.chorus.core.players.PlayerProfiles;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * {@code /tpoffline <player>}: goes to where somebody logged out.
 *
 * <p>Somebody still online is simply where they are, so the command answers for them too
 * rather than refusing on a technicality.
 */
public final class TpOfflineCommand extends ChorusCommand {

    private final PlayerProfiles profiles;
    private final TeleportService teleports;

    public TpOfflineCommand(CommandSupport support, PlayerProfiles profiles,
                            TeleportService teleports) {
        super(support, "tpoffline", "chorus.tpa.offline");
        this.profiles = profiles;
        this.teleports = teleports;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.players-only");
            return;
        }
        if (args.length == 0) {
            messages.send(sender, "request.offline-usage");
            return;
        }

        Player online = player.getServer().getPlayerExact(args[0]);
        if (online != null && player.canSee(online)) {
            go(player, online.getLocation(), online.getName(), 0);
            return;
        }

        profiles.find(args[0]).whenComplete((found, failure) -> {
            if (failure != null) {
                messages.send(player, "error.storage");
                return;
            }
            if (found.isEmpty()) {
                messages.send(player, "error.player-not-found", "player", args[0]);
                return;
            }

            PlayerProfile profile = found.get();
            Optional<Location> where = profile.lastLocation();
            if (where.isEmpty()) {
                messages.send(player, "request.offline-nowhere", "player", profile.name());
                return;
            }
            go(player, where.get(), profile.name(), profile.lastSeen());
        });
    }

    private void go(Player player, Location destination, String name, long lastSeen) {
        if (!ready(player)) {
            return;
        }
        teleports.teleport(player, destination, rules(), name(), () -> {
            settle(player);
            messages.send(player, lastSeen == 0 ? "request.offline-here" : "request.offline-went",
                    "player", name,
                    "ago", lastSeen == 0 ? ""
                            : Durations.format(System.currentTimeMillis() - lastSeen));
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? onlineNames(sender, args[0], false) : List.of();
    }
}
