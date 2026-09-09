package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class BackCommand extends PlayerCommand {

    private final TeleportService teleports;

    public BackCommand(CommandSupport support, TeleportService teleports) {
        super(support, "back", "chorus.back.use");
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        Location previous = teleports.previousLocation(player.getUniqueId()).orElse(null);
        if (previous == null) {
            messages.send(player, "back.none");
            return;
        }
        if (!previous.isWorldLoaded()) {
            messages.send(player, "back.world-missing");
            return;
        }
        if (!ready(player)) {
            return;
        }

        // The teleport itself records where the player is standing now, so /back toggles.
        teleports.teleport(player, previous, rules(), name(), () -> {
            settle(player);
            messages.send(player, "back.teleported");
        });
    }
}
