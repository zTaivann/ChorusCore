package dev.chorus.core.players.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.players.AfkService;
import dev.chorus.core.players.PlayerProfile;
import dev.chorus.core.players.PlayerProfiles;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * {@code /seen <player>}: when somebody was last here, and who else uses their connection.
 *
 * <p>The address and the accounts sharing it are only shown to whoever holds the extra
 * permission for them. That half is the useful half when somebody comes back on a second
 * account, and it is nobody else's business.
 */
public final class SeenCommand extends ChorusCommand {

    private static final String ADDRESS_PERMISSION = "chorus.players.seen.address";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final AfkService afk;
    private final PlayerProfiles profiles;

    public SeenCommand(CommandSupport support, AfkService afk, PlayerProfiles profiles) {
        super(support, "seen", "chorus.players.seen");
        this.afk = afk;
        this.profiles = profiles;
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
            details(sender, online.getUniqueId());
            return;
        }

        profiles.find(args[0]).whenComplete((found, failure) -> {
            if (failure != null) {
                messages.send(sender, "error.storage");
                return;
            }
            if (found.isEmpty()) {
                messages.send(sender, "error.player-not-found", "player", args[0]);
                return;
            }

            PlayerProfile profile = found.get();
            settle(sender);
            messages.send(sender, "players.seen-offline",
                    "player", profile.name(),
                    "ago", Durations.format(
                            Math.max(0, System.currentTimeMillis() - profile.lastSeen())),
                    "first", DATE.format(Instant.ofEpochMilli(profile.firstSeen())));
            if (!profile.world().isEmpty()) {
                messages.send(sender, "players.seen-where",
                        "world", profile.world(),
                        "x", String.valueOf(Math.round(profile.x())),
                        "y", String.valueOf(Math.round(profile.y())),
                        "z", String.valueOf(Math.round(profile.z())));
            }
            addresses(sender, profile);
        });
    }

    /** For somebody online, the profile has to be read before their address is any use. */
    private void details(CommandSender sender, UUID player) {
        if (!sender.hasPermission(ADDRESS_PERMISSION)) {
            return;
        }
        profiles.find(player).whenComplete((found, failure) -> {
            if (failure == null && found.isPresent()) {
                addresses(sender, found.get());
            }
        });
    }

    private void addresses(CommandSender sender, PlayerProfile profile) {
        if (!sender.hasPermission(ADDRESS_PERMISSION) || profile.address().isEmpty()) {
            return;
        }
        messages.send(sender, "players.seen-address", "address", profile.address());
        profiles.alts(profile).whenComplete((names, failure) -> {
            if (failure != null || names == null || names.isEmpty()) {
                return;
            }
            messages.send(sender, "players.seen-alts",
                    "count", String.valueOf(names.size()),
                    "players", String.join(", ", names));
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? onlineNames(sender, args[0], true) : List.of();
    }
}
