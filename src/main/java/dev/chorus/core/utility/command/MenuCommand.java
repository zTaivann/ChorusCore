package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.utility.PortableMenu;
import org.bukkit.entity.Player;

/** One class behind /craft, /anvil, /smithingtable and the rest of the portable screens. */
public final class MenuCommand extends PlayerCommand {

    private final PortableMenu menu;

    public MenuCommand(CommandSupport support, PortableMenu menu) {
        super(support, menu.command(), "chorus.utility." + menu.command());
        this.menu = menu;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }
        settle(player);
        menu.open(player);
    }
}
