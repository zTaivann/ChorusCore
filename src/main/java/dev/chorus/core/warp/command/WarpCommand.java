package dev.chorus.core.warp.command;

import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.location.Names;
import dev.chorus.core.teleport.TeleportService;
import dev.chorus.core.warp.WarpDetailsService;
import dev.chorus.core.warp.WarpService;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/** {@code /warp <name>}, or {@code /warp <name> <player>} to send somebody else. */
public final class WarpCommand extends PlayerCommand {

    private static final String OTHERS_PERMISSION = "chorus.warp.use.others";

    private final WarpService warps;
    private final WarpDetailsService details;
    private final TeleportService teleports;

    public WarpCommand(CommandSupport support, WarpService warps, WarpDetailsService details,
                       TeleportService teleports) {
        super(support, "warp", "chorus.warp.use");
        this.warps = warps;
        this.details = details;
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, warps.visibleTo(player).isEmpty()
                    ? "warp.none"
                    : "warp.use-usage");
            return;
        }

        String key = Names.normalise(args[0]);
        Optional<NamedLocation> found = warps.find(key);
        if (found.isEmpty()) {
            messages.send(player, "warp.unknown", "warp", key);
            return;
        }
        if (!warps.canUse(player, key)) {
            messages.send(player, "warp.locked", "warp", key);
            return;
        }

        Player traveller = player;
        if (args.length > 1) {
            if (!player.hasPermission(OTHERS_PERMISSION)) {
                messages.send(player, "error.no-permission");
                return;
            }
            traveller = online(player, args[1]);
            if (traveller == null) {
                return;
            }
        }

        NamedLocation warp = found.get();
        Location destination = warp.toLocation();
        if (destination == null) {
            messages.send(player, "warp.world-missing", "world", warp.worldName());
            return;
        }

        // Sending somebody else is a staff action, and paying for their trip out of your own
        // pocket, or waiting out their cooldown, is nobody's idea of how that should work.
        if (!traveller.equals(player)) {
            send(player, traveller, warp, destination);
            return;
        }

        String cooldownKey = name() + ":" + key;
        CommandRules against = details.of(key).over(rules());
        if (!ready(player, cooldownKey, against)) {
            return;
        }

        teleports.teleport(player, destination, against, name(), () -> {
            settle(player, cooldownKey, against);
            details.countUse(key);
            messages.send(player, "warp.teleported", "warp", warp.name());
        });
    }

    private void send(Player sender, Player traveller, NamedLocation warp, Location destination) {
        if (!ready(sender)) {
            return;
        }
        teleports.teleport(traveller, destination, CommandRules.FREE, name(), () -> {
            settle(sender);
            details.countUse(warp.name());
            messages.send(sender, "warp.sent",
                    "player", traveller.getName(), "warp", warp.name());
            messages.send(traveller, "warp.sent-received",
                    "player", sender.getName(), "warp", warp.name());
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0],
                    warps.visibleTo(sender).stream().map(NamedLocation::name).toList());
        }
        if (args.length == 2 && sender.hasPermission(OTHERS_PERMISSION)) {
            return onlineNames(sender, args[1], true);
        }
        return List.of();
    }
}
