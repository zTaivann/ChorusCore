package dev.chorus.core.home.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.home.Home;
import dev.chorus.core.home.HomeService;
import dev.chorus.core.location.Names;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code /home [name]}, and {@code /home <player>:<name>} for staff. */
public final class HomeCommand extends PlayerCommand {

    private static final String OTHERS = "chorus.home.others";

    private final HomeService homes;
    private final TeleportService teleports;

    public HomeCommand(CommandSupport support, HomeService homes, TeleportService teleports) {
        super(support, "home", "chorus.home.use");
        this.homes = homes;
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        Player owner = player;
        String[] rest = args;

        int colon = args.length > 0 ? args[0].indexOf(':') : -1;
        if (colon > 0) {
            if (!player.hasPermission(OTHERS)) {
                messages.send(player, "error.no-permission");
                return;
            }
            owner = online(player, args[0].substring(0, colon));
            if (owner == null) {
                return;
            }
            rest = new String[] {args[0].substring(colon + 1)};
        }

        UUID playerId = owner.getUniqueId();
        if (!homes.isLoaded(playerId)) {
            messages.send(player, "error.loading");
            return;
        }
        // Telling someone with no homes at all that "home" does not exist reads like a bug.
        if (homes.count(playerId) == 0) {
            messages.send(player, owner.equals(player) ? "home.none" : "home.none-other",
                    "player", owner.getName());
            return;
        }

        String name = requested(owner, rest);
        Optional<Home> found = homes.find(playerId, name);
        if (found.isEmpty()) {
            messages.send(player, "home.unknown", "home", Names.normalise(name));
            return;
        }

        Home home = found.get();
        Location destination = home.toLocation();
        if (destination == null) {
            messages.send(player, "home.world-missing", "world", home.worldName());
            return;
        }

        if (!ready(player)) {
            return;
        }
        Player whose = owner;
        teleports.teleport(player, destination, rules(), name(), () -> {
            settle(player);
            messages.send(player, whose.equals(player) ? "home.teleported" : "home.teleported-other",
                    "home", home.name(), "player", whose.getName());
        });
    }

    private String requested(Player player, String[] args) {
        if (args.length > 0) {
            return args[0];
        }
        if (homes.settings().fallbackToOnlyHome()) {
            Optional<Home> only = homes.onlyHome(player.getUniqueId());
            if (only.isPresent()) {
                return only.get().name();
            }
        }
        return homes.settings().defaultName();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        return startingWith(args[0], homes.list(player.getUniqueId()).stream().map(Home::name).toList());
    }
}
