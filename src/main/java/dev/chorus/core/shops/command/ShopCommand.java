package dev.chorus.core.shops.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Confirmations;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.shops.chest.ChestShop;
import dev.chorus.core.shops.chest.ChestShopDisplays;
import dev.chorus.core.shops.chest.ChestShopSign;
import dev.chorus.core.shops.chest.ChestShops;
import dev.chorus.core.shops.chest.ShopContainers;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** {@code /shop}: managing the chest shop you are looking at. */
public final class ShopCommand extends PlayerCommand {

    private static final String ADMIN_PERMISSION = "chorus.shops.chest.admin";
    private static final String UNLIMITED_PERMISSION = "chorus.shops.chest.unlimited";
    private static final int RANGE = 8;
    private static final List<String> ACTIONS =
            List.of("info", "price", "mode", "remove", "list", "unlimited");

    private final ChestShops shops;
    private final ChestShopDisplays displays;
    private final Economy economy;
    private final Confirmations confirmations;

    public ShopCommand(CommandSupport support, ChestShops shops, ChestShopDisplays displays,
                       Economy economy, Confirmations confirmations) {
        super(support, "shop", "chorus.shops.chest.use");
        this.shops = shops;
        this.displays = displays;
        this.economy = economy;
        this.confirmations = confirmations;
    }

    @Override
    protected void execute(Player player, String[] args) {
        String action = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            list(player);
            return;
        }

        ChestShop shop = looking(player);
        if (shop == null) {
            messages.send(player, "shops.chest-not-looking");
            return;
        }
        if (!mine(player, shop)) {
            messages.send(player, "shops.chest-not-yours", "player", shop.ownerName());
            return;
        }

        switch (action) {
            case "info" -> info(player, shop);
            case "price" -> price(player, shop, args);
            case "mode" -> mode(player, shop);
            case "remove", "delete" -> remove(player, shop);
            case "unlimited", "admin" -> unlimited(player, shop);
            default -> messages.send(player, "shops.chest-usage");
        }
    }

    private void info(Player player, ChestShop shop) {
        ItemStack template = shop.template();
        if (template == null) {
            messages.send(player, "shops.chest-broken");
            return;
        }
        messages.send(player, "shops.chest-info-header");
        messages.send(player, "shops.chest-info-owner", "player", shop.ownerName());
        messages.send(player, "shops.chest-info-item", "item", ChestShopSign.itemName(template));
        messages.send(player, "shops.chest-info-price",
                "price", economy.format(shop.price()),
                "item", ChestShopSign.itemName(template));
        messages.send(player, shop.selling() ? "shops.chest-mode-selling" : "shops.chest-mode-buying");
        if (shop.unlimited()) {
            messages.send(player, "shops.chest-info-unlimited");
        }
    }

    private void price(Player player, ChestShop shop, String[] args) {
        if (args.length < 2) {
            messages.send(player, "shops.chest-usage");
            return;
        }
        double price = price(args[1]);
        if (price < 0) {
            messages.send(player, "economy.invalid-amount");
            return;
        }
        if (!ready(player)) {
            return;
        }

        ChestShop updated = shop.withPrice(price);
        shops.replace(updated);
        refresh(updated);

        settle(player);
        messages.send(player, "shops.chest-price-set", "price", economy.format(price));
    }

    private void mode(Player player, ChestShop shop) {
        if (!ready(player)) {
            return;
        }
        ChestShop updated = shop.withMode(!shop.selling());
        shops.replace(updated);
        refresh(updated);

        settle(player);
        messages.send(player, updated.selling()
                ? "shops.chest-mode-selling"
                : "shops.chest-mode-buying");
    }

    private void unlimited(Player player, ChestShop shop) {
        if (!player.hasPermission(UNLIMITED_PERMISSION)) {
            messages.send(player, "error.no-permission");
            return;
        }
        if (!ready(player)) {
            return;
        }

        ChestShop updated = shop.withUnlimited(!shop.unlimited());
        shops.replace(updated);
        refresh(updated);

        settle(player);
        messages.send(player, updated.unlimited()
                ? "shops.chest-unlimited-on"
                : "shops.chest-unlimited-off");
    }

    private void remove(Player player, ChestShop shop) {
        if (!confirmations.confirmed(player, "shop:" + shop.key(), "shops.chest-remove-confirm")) {
            return;
        }
        if (!ready(player)) {
            return;
        }

        displays.hide(shop.key());
        shops.remove(shop);
        Location where = shop.location();
        if (where != null) {
            for (Block sign : ShopContainers.signsOn(where.getBlock())) {
                sign.breakNaturally();
            }
        }

        settle(player);
        messages.send(player, "shops.chest-removed");
    }

    private void list(Player player) {
        List<ChestShop> owned = shops.allOwnedBy(player.getUniqueId());
        if (owned.isEmpty()) {
            messages.send(player, "shops.chest-list-empty");
            return;
        }
        if (!ready(player)) {
            return;
        }
        settle(player);

        messages.send(player, "shops.chest-list-header", "count", String.valueOf(owned.size()));
        for (ChestShop shop : owned) {
            ItemStack template = shop.template();
            messages.send(player, "shops.chest-list-entry",
                    "item", template == null
                            ? messages.plain("shops.chest-list-broken")
                            : ChestShopSign.itemName(template),
                    "price", economy.format(shop.price()),
                    "world", shop.world(),
                    "x", String.valueOf(shop.x()),
                    "y", String.valueOf(shop.y()),
                    "z", String.valueOf(shop.z()));
        }
    }

    private void refresh(ChestShop shop) {
        Location where = shop.location();
        if (where != null) {
            ChestShopSign.refresh(shop, where.getBlock(), messages, economy);
        }
        displays.show(shop);
    }

    private boolean mine(Player player, ChestShop shop) {
        return shop.isOwner(player.getUniqueId()) || player.hasPermission(ADMIN_PERMISSION);
    }

    private @Nullable ChestShop looking(Player player) {
        Block target = player.getTargetBlockExact(RANGE);
        return target == null ? null : shops.at(target);
    }

    private static double price(String raw) {
        try {
            double value = Double.parseDouble(raw.replace(',', '.'));
            return Double.isFinite(value) && value >= 0
                    ? Math.round(value * 100.0) / 100.0
                    : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? startingWith(args[0], ACTIONS) : List.of();
    }

}
