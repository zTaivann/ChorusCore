package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.items.ItemService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** Fills the held stack, or sets it to a size given as an argument. */
public final class MoreCommand extends HeldItemCommand {

    private final ItemService items;

    public MoreCommand(CommandSupport support, ItemService items) {
        super(support, "more", "chorus.items.more");
        this.items = items;
    }

    @Override
    protected void execute(Player player, String[] args) {
        ItemStack item = held(player);
        if (item == null) {
            return;
        }

        int most = item.getMaxStackSize();
        int wanted = args.length == 0 ? most : parse(args[0]);
        if (wanted < 1 || wanted > most) {
            messages.send(player, "items.more-range", "max", String.valueOf(most));
            return;
        }
        if (item.getAmount() == wanted) {
            messages.send(player, "items.more-already", "amount", String.valueOf(wanted));
            return;
        }
        // Otherwise one of a blocked item is all it takes to have a stack of them.
        if (wanted > item.getAmount()
                && !items.settings().restrictions().mayHave(player, item.getType())) {
            messages.send(player, "items.give-blocked",
                    "item", item.getType().name().toLowerCase(Locale.ROOT));
            return;
        }
        if (!ready(player)) {
            return;
        }

        item.setAmount(wanted);
        settle(player);
        messages.send(player, "items.more", "amount", String.valueOf(wanted));
    }

    private static int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? startingWith(args[0], List.of("16", "32", "64")) : List.of();
    }
}
