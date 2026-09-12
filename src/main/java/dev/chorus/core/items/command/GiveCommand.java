package dev.chorus.core.items.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /give [player] <item> [amount]}: hands out items by name.
 *
 * <p>Leaving the player off gives them to whoever ran it, which is how it is used nine times
 * out of ten. Anything that does not fit is dropped at their feet rather than lost.
 */
public final class GiveCommand extends ChorusCommand {

    private static final String OTHERS = "chorus.items.give.others";
    private static final int MAX_AMOUNT = 2304;

    public GiveCommand(CommandSupport support) {
        super(support, "give", "chorus.items.give");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "items.give-usage");
            return;
        }

        // Either "<item> [amount]" or "<player> <item> [amount]": whichever way round it is
        // written, the first word that names a material is the item.
        Material first = Material.matchMaterial(args[0]);
        Player target;
        String itemName;
        String amountRaw;

        if (first != null && first.isItem()) {
            if (!(sender instanceof Player self)) {
                messages.send(sender, "items.give-usage");
                return;
            }
            target = self;
            itemName = args[0];
            amountRaw = args.length > 1 ? args[1] : null;
        } else {
            if (args.length < 2) {
                messages.send(sender, "items.give-usage");
                return;
            }
            if (!sender.hasPermission(OTHERS)) {
                messages.send(sender, "error.no-permission");
                return;
            }
            target = online(sender, args[0]);
            if (target == null) {
                return;
            }
            itemName = args[1];
            amountRaw = args.length > 2 ? args[2] : null;
        }

        Material material = Material.matchMaterial(itemName);
        if (material == null || material.isAir() || !material.isItem()) {
            messages.send(sender, "items.give-unknown", "item", itemName);
            return;
        }
        int amount = amountRaw == null ? material.getMaxStackSize() : amount(amountRaw);
        if (amount <= 0) {
            messages.send(sender, "items.give-usage");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        hand(target, material, amount);
        settle(sender);

        String item = material.name().toLowerCase(Locale.ROOT);
        if (target.equals(sender)) {
            messages.send(sender, "items.give-self", "amount", String.valueOf(amount), "item", item);
            return;
        }
        messages.send(sender, "items.give-sent",
                "amount", String.valueOf(amount), "item", item, "player", target.getName());
        messages.send(target, "items.give-received",
                "amount", String.valueOf(amount), "item", item);
    }

    private static void hand(Player target, Material material, int amount) {
        int left = amount;
        int stackSize = material.getMaxStackSize();
        while (left > 0) {
            int batch = Math.min(stackSize, left);
            left -= batch;
            Map<Integer, ItemStack> rejected =
                    target.getInventory().addItem(new ItemStack(material, batch));
            rejected.values().forEach(
                    stack -> target.getWorld().dropItemNaturally(target.getLocation(), stack));
        }
    }

    private static int amount(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value <= 0 ? -1 : Math.min(MAX_AMOUNT, value);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length > 2) {
            return List.of();
        }
        if (args.length == 2 && !sender.hasPermission(OTHERS)) {
            return List.of();
        }

        List<String> options = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission(OTHERS)) {
            options.addAll(onlineNames(sender, args[0], true));
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (typed.length() >= 2) {
            for (Material material : Material.values()) {
                if (material.isItem() && !material.isAir()
                        && material.name().toLowerCase(Locale.ROOT).startsWith(typed)) {
                    options.add(material.name().toLowerCase(Locale.ROOT));
                }
            }
        }
        return options;
    }
}
