package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.utility.UtilityService;
import org.bukkit.entity.Player;

/**
 * A throwaway inventory. Nothing saves it, so whatever is inside when the screen closes is
 * gone for good; that is the entire point of the command.
 */
public final class DisposalCommand extends PlayerCommand {

    private final UtilityService utility;

    public DisposalCommand(CommandSupport support, UtilityService utility) {
        super(support, "trash", "chorus.utility.trash");
        this.utility = utility;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }
        settle(player);

        int slots = utility.settings().disposal().rows() * 9;
        player.openInventory(player.getServer()
                .createInventory(null, slots, messages.render("menu.titles.disposal")));
    }
}
