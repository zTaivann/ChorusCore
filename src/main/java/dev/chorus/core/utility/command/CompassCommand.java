package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /compass}: which way the player is facing. */
public final class CompassCommand extends PlayerCommand {

    /** Clockwise from south, which is where Minecraft starts counting yaw. */
    private static final List<String> POINTS = List.of(
            "south", "south-west", "west", "north-west",
            "north", "north-east", "east", "south-east");

    private static final float ARC = 360f / 8;

    public CompassCommand(CommandSupport support) {
        super(support, "compass", "chorus.utility.compass");
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }
        settle(player);

        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        int point = Math.round(yaw / ARC) % POINTS.size();

        messages.send(player, "utility.compass",
                "direction", POINTS.get(point),
                "degrees", String.valueOf(Math.round(yaw)));
    }
}
