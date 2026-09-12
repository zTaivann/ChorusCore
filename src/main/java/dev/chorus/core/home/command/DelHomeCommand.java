package dev.chorus.core.home.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Confirmations;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.home.Home;
import dev.chorus.core.home.HomeService;
import dev.chorus.core.location.Names;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DelHomeCommand extends PlayerCommand {

    private final HomeService homes;
    private final Confirmations confirmations;
    private final Logger logger;

    public DelHomeCommand(CommandSupport support, HomeService homes, Confirmations confirmations,
                          Logger logger) {
        super(support, "delhome", "chorus.home.delete");
        this.homes = homes;
        this.confirmations = confirmations;
        this.logger = logger;
    }

    @Override
    protected void execute(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        if (!homes.isLoaded(playerId)) {
            messages.send(player, "error.loading");
            return;
        }
        if (homes.count(playerId) == 0) {
            messages.send(player, "home.none");
            return;
        }
        // Deliberately no default name here: deleting is not something to do by accident.
        if (args.length == 0) {
            messages.send(player, "home.delete-usage");
            return;
        }

        String key = Names.normalise(args[0]);
        if (homes.find(playerId, key).isEmpty()) {
            messages.send(player, "home.unknown", "home", key);
            return;
        }
        if (!confirmations.confirmed(player, "delhome:" + key, "home.delete-confirm",
                "home", key)) {
            return;
        }
        if (!ready(player)) {
            return;
        }

        homes.delete(playerId, key).whenComplete((removed, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not delete the home " + key + " of " + player.getName(), failure);
                messages.send(player, "error.storage");
                return;
            }
            if (!removed) {
                messages.send(player, "home.unknown", "home", key);
                return;
            }
            settle(player);
            messages.send(player, "home.deleted", "home", key);
        });
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
