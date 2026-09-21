package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class HatCommand extends HeldItemCommand {

    public HatCommand(CommandSupport support) {
        super(support, "hat", "chorus.items.hat");
    }

    @Override
    protected void execute(Player player, String[] args) {
        ItemStack wanted = held(player);
        if (wanted == null) {
            return;
        }
        if (!ready(player)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack previous = inventory.getHelmet();
        inventory.setHelmet(wanted);
        inventory.setItemInMainHand(previous);

        settle(player);
        messages.send(player, "items.hat-worn");
    }
}
