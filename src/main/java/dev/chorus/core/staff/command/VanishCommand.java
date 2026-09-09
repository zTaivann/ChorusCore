package dev.chorus.core.staff.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.staff.VanishService;
import org.bukkit.entity.Player;

public final class VanishCommand extends PlayerCommand {

    private final VanishService vanish;

    public VanishCommand(CommandSupport support, VanishService vanish) {
        super(support, "vanish", "chorus.staff.vanish");
        this.vanish = vanish;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }
        boolean hidden = vanish.toggle(player);
        settle(player);
        messages.send(player, hidden ? "staff.vanish-enabled" : "staff.vanish-disabled");
    }
}
