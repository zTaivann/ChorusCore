package dev.chorus.core.shops.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.shops.ShopService;
import dev.chorus.core.shops.WorthTable;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /worth [hand|item] [amount]}: what the server pays for something. */
public final class WorthCommand extends PlayerCommand {

    private final WorthTable worth;
    private final Economy economy;

    public WorthCommand(CommandSupport support, WorthTable worth, Economy economy) {
        super(support, "worth", "chorus.shops.worth");
        this.worth = worth;
        this.economy = economy;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!economy.enabled()) {
            messages.send(player, "economy.unavailable");
            return;
        }

        String what = args.length == 0 ? "hand" : args[0].toLowerCase(Locale.ROOT);
        Material material = what.equals("hand")
                ? player.getInventory().getItemInMainHand().getType()
                : Material.matchMaterial(what);
        if (material == null || material.isAir()) {
            messages.send(player, "shops.sell-unknown", "item", what);
            return;
        }

        String item = material.name().toLowerCase(Locale.ROOT);
        double each = worth.of(material);
        if (each <= 0) {
            messages.send(player, "shops.no-worth", "item", item);
            return;
        }
        if (!ready(player)) {
            return;
        }

        int amount = args.length > 1 ? amount(args[1]) : carried(player, material);
        settle(player);
        messages.send(player, "shops.worth",
                "item", item,
                "each", economy.format(each),
                "amount", String.valueOf(amount),
                "total", economy.format(each * amount));
    }

    /** What they are holding, or one, so /worth always answers something useful. */
    private static int carried(Player player, Material material) {
        int carrying = ShopService.count(player, material);
        return carrying > 0 ? carrying : 1;
    }

    private static int amount(String raw) {
        return Math.max(1, Numbers.integer(raw, 1));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("hand"));
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && worth.sellable(stack.getType())) {
                String name = stack.getType().name().toLowerCase(Locale.ROOT);
                if (!options.contains(name)) {
                    options.add(name);
                }
            }
        }
        return startingWith(args[0], options);
    }
}
