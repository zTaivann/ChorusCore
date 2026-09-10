package dev.chorus.core.home.command;

import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.home.Home;
import dev.chorus.core.home.HomeService;
import dev.chorus.core.location.Names;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SetHomeCommand extends PlayerCommand {

    private final HomeService homes;
    private final Logger logger;

    public SetHomeCommand(CommandSupport support, HomeService homes, Logger logger) {
        super(support, "sethome", "chorus.home.set");
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

        String name = args.length > 0 ? args[0] : homes.settings().defaultName();
        if (!homes.isValidName(name)) {
            messages.send(player, "home.invalid-name",
                    "max", String.valueOf(homes.settings().maxNameLength()));
            return;
        }

        String key = Names.normalise(name);
        boolean replacing = homes.find(playerId, key).isPresent();
        int limit = homes.limit(player);
        if (!replacing && homes.count(playerId) >= limit) {
            messages.send(player, "home.limit-reached", "limit", String.valueOf(limit));
            return;
        }

        String world = player.getWorld().getName();
        int worldLimit = homes.settings().limitIn(world);
        if (!replacing && worldLimit >= 0 && homes.countIn(playerId, world) >= worldLimit) {
            messages.send(player, "home.world-limit-reached",
                    "world", world, "limit", String.valueOf(worldLimit));
            return;
        }

        // The price grows with the number already owned, so the tenth home can cost real
        // money while the first stays free. Moving one you have is never surcharged.
        CommandRules against = new CommandRules(rules().enabled(), rules().warmupSeconds(),
                rules().cooldownSeconds(),
                homes.settings().priceFor(rules().price(), homes.count(playerId), replacing),
                rules().feedback());
        if (!ready(player, name(), against)) {
            return;
        }

        // Moving a home the player already has keeps its icon and the day it was made.
        Home saved = homes.find(playerId, key)
                .map(existing -> existing.movedTo(player.getLocation()))
                .orElseGet(() -> Home.create(playerId, key, player.getLocation()));

        homes.save(saved)
                .whenComplete((ignored, failure) -> {
                    // A plugin cancelling the event is a decision, not a fault: it has
                    // already told the player whatever it wanted to.
                    if (failure instanceof CancellationException
                            || failure instanceof CompletionException wrapped
                            && wrapped.getCause() instanceof CancellationException) {
                        return;
                    }
                    if (failure != null) {
                        logger.log(Level.SEVERE, "Could not save the home " + key + " of " + player.getName(), failure);
                        messages.send(player, "error.storage");
                        return;
                    }
                    settle(player, name(), against);
                    messages.send(player, replacing ? "home.updated" : "home.created", "home", key);
                });
    }
}
