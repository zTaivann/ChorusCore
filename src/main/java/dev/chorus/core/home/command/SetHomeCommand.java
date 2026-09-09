package dev.chorus.core.home.command;

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
        if (!ready(player)) {
            return;
        }

        homes.save(Home.create(playerId, key, player.getLocation()))
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
                    settle(player);
                    messages.send(player, replacing ? "home.updated" : "home.created", "home", key);
                });
    }
}
