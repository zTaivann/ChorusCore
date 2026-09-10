package dev.chorus.core.home.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.home.Home;
import dev.chorus.core.home.HomeService;
import dev.chorus.core.location.Names;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** {@code /renamehome <old> <new>}, keeping the position and the day it was made. */
public final class RenameHomeCommand extends PlayerCommand {

    private final HomeService homes;
    private final Logger logger;

    public RenameHomeCommand(CommandSupport support, HomeService homes, Logger logger) {
        super(support, "renamehome", "chorus.home.rename");
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
        if (args.length < 2) {
            messages.send(player, "home.rename-usage");
            return;
        }

        String from = Names.normalise(args[0]);
        String to = Names.normalise(args[1]);
        Optional<Home> existing = homes.find(playerId, from);
        if (existing.isEmpty()) {
            messages.send(player, "home.unknown", "home", from);
            return;
        }
        if (!homes.isValidName(to)) {
            messages.send(player, "home.invalid-name",
                    "max", String.valueOf(homes.settings().maxNameLength()));
            return;
        }
        if (from.equals(to)) {
            messages.send(player, "home.rename-same");
            return;
        }
        if (homes.find(playerId, to).isPresent()) {
            messages.send(player, "home.rename-taken", "home", to);
            return;
        }
        if (!ready(player)) {
            return;
        }

        // Saved under the new name before the old one goes: an outage between the two leaves
        // the player with a duplicate rather than with nothing.
        homes.save(existing.get().renamedTo(to))
                .thenCompose(ignored -> homes.delete(playerId, from))
                .whenComplete((removed, failure) -> {
                    if (failure != null) {
                        logger.log(Level.SEVERE,
                                "Could not rename the home " + from + " of " + player.getName(), failure);
                        messages.send(player, "error.storage");
                        return;
                    }
                    settle(player);
                    messages.send(player, "home.renamed", "home", from, "name", to);
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
