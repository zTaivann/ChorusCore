package dev.chorus.core.shops.command;

import dev.chorus.core.command.CommandSupport;
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

/**
 * {@code /sell <hand|all|item> [amount]}: sells to the server at the worth table's prices.
 *
 * <p>{@code all} sells every plain item that has a price, which is what a player coming back
 * from a mine actually wants. Anything renamed, enchanted or damaged is left alone.
 */
public final class SellCommand extends PlayerCommand {

    private final WorthTable worth;
    private final Economy economy;

    public SellCommand(CommandSupport support, WorthTable worth, Economy economy) {
        super(support, "sell", "chorus.shops.sell");
        this.worth = worth;
        this.economy = economy;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!economy.enabled()) {
            messages.send(player, "economy.unavailable");
            return;
        }
        if (args.length == 0) {
            messages.send(player, "shops.sell-usage");
            return;
        }

        String what = args[0].toLowerCase(Locale.ROOT);
        if (what.equals("all") || what.equals("*")) {
            sellEverything(player);
            return;
        }

        Material material = what.equals("hand") ? inHand(player) : Material.matchMaterial(what);
        if (material == null || material.isAir()) {
            messages.send(player, "shops.sell-unknown", "item", args[0]);
            return;
        }

        String item = material.name().toLowerCase(Locale.ROOT);
        double each = worth.of(material);
        if (each <= 0) {
            messages.send(player, "shops.no-worth", "item", item);
            return;
        }

        int carrying = ShopService.count(player, material);
        if (carrying <= 0) {
            messages.send(player, "shops.none-carried", "item", item);
            return;
        }
        int wanted = args.length > 1 ? amount(args[1], carrying) : carrying;
        if (wanted <= 0) {
            messages.send(player, "shops.sell-usage");
            return;
        }
        if (!ready(player)) {
            return;
        }

        int sold = ShopService.take(player, material, Math.min(wanted, carrying));
        if (sold <= 0) {
            messages.send(player, "shops.none-carried", "item", item);
            return;
        }

        double paid = each * sold;
        if (!economy.deposit(player, paid)) {
            ShopService.give(player, material, sold);
            messages.send(player, "economy.transfer-failed");
            return;
        }

        settle(player);
        messages.send(player, "shops.sold",
                "amount", String.valueOf(sold), "item", item, "price", economy.format(paid));
    }

    private void sellEverything(Player player) {
        List<Material> kinds = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && !stack.hasItemMeta() && worth.sellable(stack.getType())
                    && !kinds.contains(stack.getType())) {
                kinds.add(stack.getType());
            }
        }
        if (kinds.isEmpty()) {
            messages.send(player, "shops.nothing-to-sell");
            return;
        }
        if (!ready(player)) {
            return;
        }

        double paid = 0;
        int sold = 0;
        for (Material material : kinds) {
            int carrying = ShopService.count(player, material);
            int taken = ShopService.take(player, material, carrying);
            paid += worth.of(material) * taken;
            sold += taken;
        }
        if (sold == 0 || !economy.deposit(player, paid)) {
            messages.send(player, "shops.nothing-to-sell");
            return;
        }

        settle(player);
        messages.send(player, "shops.sold-all",
                "amount", String.valueOf(sold),
                "kinds", String.valueOf(kinds.size()),
                "price", economy.format(paid));
    }

    private static Material inHand(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return held.getType().isAir() ? null : held.getType();
    }

    private static int amount(String raw, int carrying) {
        try {
            int value = Integer.parseInt(raw);
            return value <= 0 ? -1 : Math.min(value, carrying);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("hand", "all"));
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
