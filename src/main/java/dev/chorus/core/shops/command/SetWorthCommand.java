package dev.chorus.core.shops.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.shops.WorthTable;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /setworth [item] <price>}: writes a price into shops.yml from in game.
 *
 * <p>A price of zero takes the item off the list, which is how something stops being
 * sellable without editing the file by hand.
 */
public final class SetWorthCommand extends ChorusCommand {

    private final WorthTable worth;
    private final Economy economy;

    public SetWorthCommand(CommandSupport support, WorthTable worth, Economy economy) {
        super(support, "setworth", "chorus.shops.admin");
        this.worth = worth;
        this.economy = economy;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "shops.setworth-usage");
            return;
        }

        Material material;
        String rawPrice;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "shops.setworth-usage");
                return;
            }
            material = player.getInventory().getItemInMainHand().getType();
            rawPrice = args[0];
        } else {
            material = Material.matchMaterial(args[0]);
            rawPrice = args[1];
        }

        if (material == null || material.isAir() || !material.isItem()) {
            messages.send(sender, "shops.sell-unknown", "item", args[0]);
            return;
        }
        double price = price(rawPrice);
        if (price < 0) {
            messages.send(sender, "economy.invalid-amount");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        String item = material.name().toLowerCase(Locale.ROOT);
        if (!worth.set(material, price)) {
            messages.send(sender, "error.storage");
            return;
        }

        settle(sender);
        if (price <= 0) {
            messages.send(sender, "shops.setworth-cleared", "item", item);
        } else {
            messages.send(sender, "shops.setworth-done",
                    "item", item, "price", economy.format(price));
        }
    }

    private static double price(String raw) {
        double value = Numbers.decimal(raw.replace(',', '.'), -1);
        return value >= 0 ? value : -1;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        Material held = player.getInventory().getItemInMainHand().getType();
        return held.isAir() ? List.of()
                : startingWith(args[0], List.of(held.name().toLowerCase(Locale.ROOT)));
    }
}
