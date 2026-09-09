package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.items.ItemService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/** {@code /itemname <text>} or {@code /itemname reset}. */
public final class ItemNameCommand extends HeldItemCommand {

    private final ItemService items;

    public ItemNameCommand(CommandSupport support, ItemService items) {
        super(support, "itemname", "chorus.items.itemname");
        this.items = items;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "items.itemname-usage");
            return;
        }

        ItemStack item = held(player);
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            messages.send(player, "items.not-editable");
            return;
        }
        if (!ready(player)) {
            return;
        }

        boolean clearing = args.length == 1 && args[0].toLowerCase(Locale.ROOT).equals("reset");
        meta.displayName(clearing ? null : items.text(player, String.join(" ", args)));
        item.setItemMeta(meta);

        settle(player);
        messages.send(player, clearing ? "items.itemname-reset" : "items.itemname-set");
    }
}
