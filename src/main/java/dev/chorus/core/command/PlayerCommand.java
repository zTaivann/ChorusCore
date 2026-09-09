package dev.chorus.core.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public abstract class PlayerCommand extends ChorusCommand {

    protected PlayerCommand(CommandSupport support, String name, String permission) {
        super(support, name, permission);
    }

    @Override
    protected final void run(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.players-only");
            return;
        }
        execute(player, args);
    }

    protected abstract void execute(Player player, String[] args);
}
