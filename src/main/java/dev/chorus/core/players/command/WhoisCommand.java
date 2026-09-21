package dev.chorus.core.players.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.players.AfkService;
import dev.chorus.core.players.GeoLookup;
import dev.chorus.core.players.Playtime;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything the server already knows about one player, gathered into one card. A line it
 * cannot fill is left out rather than printed empty.
 */
public final class WhoisCommand extends ChorusCommand {

    private static final String IP_PERMISSION = "chorus.players.whois.ip";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final AfkService afk;
    private final GeoLookup geo;

    public WhoisCommand(CommandSupport support, AfkService afk, GeoLookup geo) {
        super(support, "whois", "chorus.players.whois");
        this.afk = afk;
        this.geo = geo;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "players.whois-usage");
            return;
        }

        Player online = sender.getServer().getPlayerExact(args[0]);
        if (online != null && sender instanceof Player viewer && !viewer.canSee(online)) {
            online = null;
        }
        if (online != null) {
            if (!ready(sender)) {
                return;
            }
            settle(sender);
            describeOnline(sender, online);
            return;
        }

        OfflinePlayer offline = sender.getServer().getOfflinePlayerIfCached(args[0]);
        if (offline == null || !offline.hasPlayedBefore()) {
            messages.send(sender, "error.player-not-found", "player", args[0]);
            return;
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);
        describeOffline(sender, offline, args[0]);
    }

    private void describeOnline(CommandSender sender, Player target) {
        messages.send(sender, "players.whois-header", "player", target.getName());
        messages.send(sender, "players.whois-uuid", "uuid", target.getUniqueId().toString());
        messages.send(sender, afk.isAway(target.getUniqueId())
                ? "players.whois-status-away"
                : "players.whois-status-online");
        messages.send(sender, "players.whois-world",
                "world", target.getWorld().getName(),
                "x", String.valueOf(target.getLocation().getBlockX()),
                "y", String.valueOf(target.getLocation().getBlockY()),
                "z", String.valueOf(target.getLocation().getBlockZ()));
        messages.send(sender, "players.whois-health",
                "health", String.format(Locale.ROOT, "%.1f", target.getHealth()),
                "food", String.valueOf(target.getFoodLevel()));
        messages.send(sender, "players.whois-gamemode",
                "gamemode", target.getGameMode().name().toLowerCase(Locale.ROOT));
        messages.send(sender, "players.whois-ping", "ping", String.valueOf(target.getPing()));
        messages.send(sender, "players.whois-playtime",
                "time", Durations.format(Playtime.of(target)));
        messages.send(sender, "players.whois-first",
                "date", DATE.format(Instant.ofEpochMilli(target.getFirstPlayed())));

        String flags = flagsOf(target);
        if (!flags.isEmpty()) {
            messages.send(sender, "players.whois-flags", "flags", flags);
        }
        if (sender.hasPermission(IP_PERMISSION)) {
            InetSocketAddress socket = target.getAddress();
            String address = socket == null ? "" : socket.getAddress().getHostAddress();
            messages.send(sender, "players.whois-address",
                    "address", address.isEmpty() ? "?" : address);
            country(sender, address);
        }
    }

    private void describeOffline(CommandSender sender, OfflinePlayer target, String typed) {
        messages.send(sender, "players.whois-header",
                "player", target.getName() == null ? typed : target.getName());
        messages.send(sender, "players.whois-uuid", "uuid", target.getUniqueId().toString());
        messages.send(sender, "players.whois-status-offline",
                "ago", Durations.format(Math.max(0, System.currentTimeMillis() - target.getLastSeen())));
        messages.send(sender, "players.whois-playtime",
                "time", Durations.format(Playtime.of(target)));
        messages.send(sender, "players.whois-first",
                "date", DATE.format(Instant.ofEpochMilli(target.getFirstPlayed())));
        if (target.isBanned()) {
            messages.send(sender, "players.whois-banned");
        }
    }

    /** The country, when the server has asked for it. */
    private void country(CommandSender sender, String address) {
        if (!geo.enabled() || address.isEmpty()) {
            return;
        }
        geo.countryOf(address).whenComplete((country, failure) -> {
            if (failure == null && country != null && !country.isEmpty()) {
                messages.send(sender, "players.whois-country", "country", country);
            }
        });
    }

    private static String flagsOf(Player target) {
        List<String> flags = new ArrayList<>();
        if (target.isFlying()) {
            flags.add("flying");
        }
        if (target.isInvulnerable()) {
            flags.add("invulnerable");
        }
        if (target.isSleeping()) {
            flags.add("sleeping");
        }
        if (target.isOp()) {
            flags.add("operator");
        }
        return String.join(", ", flags);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? onlineNames(sender, args[0], true) : List.of();
    }
}
