package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Toggles the vanilla unbreakable flag on the held item. */
public final class UnbreakableCommand extends HeldItemCommand {

    public UnbreakableCommand(CommandSupport support) {
        super(support, "unbreakable", "chorus.items.unbreakable");
    }

    @Override
    protected void execute(Player player, String[] args) {
        ItemStack item = held(player);
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            messages.send(player, "items.not-editable");
            return;
        }
        // Something with no durability to lose cannot be made unbreakable, and saying so is
        // friendlier than setting a flag the game will ignore.
        if (item.getType().getMaxDurability() <= 0) {
            messages.send(player, "items.unbreakable-not-a-tool");
            return;
        }
        if (!ready(player)) {
            return;
        }

        boolean unbreakable = !meta.isUnbreakable();
        meta.setUnbreakable(unbreakable);
        item.setItemMeta(meta);

        settle(player);
        messages.send(player, unbreakable ? "items.unbreakable-on" : "items.unbreakable-off");
    }
}
