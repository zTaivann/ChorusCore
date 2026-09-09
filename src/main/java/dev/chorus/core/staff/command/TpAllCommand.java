package dev.chorus.core.staff.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.entity.Player;

public final class TpAllCommand extends PlayerCommand {

    private final TeleportService teleports;

    public TpAllCommand(CommandSupport support, TeleportService teleports) {
        super(support, "tpall", "chorus.staff.tpall");
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        int moved = 0;
        for (Player other : player.getServer().getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            teleports.teleport(other, player.getLocation(), rules(), name());
            messages.send(other, "staff.tp-moved", "target", player.getName());
            moved++;
        }

        if (moved == 0) {
            messages.send(player, "staff.tpall-nobody");
            return;
        }
        settle(player);
        messages.send(player, "staff.tpall-done", "count", String.valueOf(moved));
    }
}
