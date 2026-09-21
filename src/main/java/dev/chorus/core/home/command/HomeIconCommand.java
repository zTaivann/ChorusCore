package dev.chorus.core.home.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.home.Home;
import dev.chorus.core.home.HomeService;
import dev.chorus.core.location.Names;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** {@code /homeicon <home> [item]}: what one home looks like in the menu. */
public final class HomeIconCommand extends PlayerCommand {

    private final HomeService homes;
    private final Logger logger;

    public HomeIconCommand(CommandSupport support, HomeService homes, Logger logger) {
        super(support, "homeicon", "chorus.home.icon");
        this.homes = homes;
        this.logger = logger;
    }

    @Override
    protected void execute(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        if (!homes.isLoaded(playerId)) {
            messages.send(player, "error.loading");
            return;
        }
        if (args.length == 0) {
            messages.send(player, "home.icon-usage");
            return;
        }

        String key = Names.normalise(args[0]);
        Optional<Home> existing = homes.find(playerId, key);
        if (existing.isEmpty()) {
            messages.send(player, "home.unknown", "home", key);
            return;
        }

        boolean clearing = args.length < 2;
        Material icon = null;
        if (!clearing) {
            icon = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
            if (icon == null || !icon.isItem()) {
                messages.send(player, "home.icon-unknown", "icon", args[1]);
                return;
            }
        }
        if (!ready(player)) {
            return;
        }

        String chosen = clearing ? null : icon.name();
        homes.save(existing.get().withIcon(chosen)).whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE,
                        "Could not set the icon of the home " + key + " of " + player.getName(), failure);
                messages.send(player, "error.storage");
                return;
            }
            settle(player);
            messages.send(player, clearing ? "home.icon-cleared" : "home.icon-set",
                    "home", key, "icon", clearing ? "" : chosen.toLowerCase(Locale.ROOT));
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        return startingWith(args[0],
                homes.list(player.getUniqueId()).stream().map(Home::name).toList());
    }
}
