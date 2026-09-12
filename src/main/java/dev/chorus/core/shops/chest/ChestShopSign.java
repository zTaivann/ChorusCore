package dev.chorus.core.shops.chest;

import dev.chorus.core.economy.Economy;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.locale.TextFormat;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * What a shop sign says.
 *
 * <p>The lines live in menus.yml like every other piece of text, so a server can translate
 * them or lay them out differently without touching the code.
 */
public final class ChestShopSign {

    /** The header a player writes to make one. */
    public static final String HEADER = "[shop]";

    private static final String UNLIMITED = "∞";

    private ChestShopSign() {
    }

    /** Writes the four lines onto every sign fixed to this shop. */
    public static void refresh(ChestShop shop, Block container, Messages messages,
                               Economy economy) {
        ItemStack template = shop.template();
        if (template == null) {
            return;
        }
        Inventory stock = ShopContainers.inventoryOf(container);
        String amount = amountLine(shop, stock, template);

        for (Block block : signsOn(shop, container)) {
            if (!(block.getState() instanceof Sign sign)) {
                continue;
            }
            sign.line(0, messages.render("menu.shops.sign-owner", "player", shop.ownerName()));
            sign.line(1, messages.render(
                    shop.selling() ? "menu.shops.sign-selling" : "menu.shops.sign-buying",
                    "amount", amount));
            sign.line(2, messages.render("menu.shops.sign-item", "item", itemName(template)));
            sign.line(3, messages.render("menu.shops.sign-price",
                    "price", economy.format(shop.price())));
            sign.update(true, false);
        }
    }

    /** Both halves of a double chest can carry a sign, and both should read the same. */
    private static List<Block> signsOn(ChestShop shop, Block container) {
        List<Block> signs = ShopContainers.signsOn(container);
        Block half = ShopContainers.otherHalf(container);
        if (half != null) {
            signs.addAll(ShopContainers.signsOn(half));
        }
        return signs;
    }

    private static String amountLine(ChestShop shop, Inventory stock, ItemStack template) {
        if (shop.unlimited() || stock == null) {
            return UNLIMITED;
        }
        int count = shop.selling()
                ? ChestShopTrade.count(stock, template)
                : ChestShopTrade.space(stock, template, Integer.MAX_VALUE);
        return String.valueOf(count);
    }

    /** The item's own name when it has been given one, and its plain name when it has not. */
    public static String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta() != null
                && item.getItemMeta().hasDisplayName()) {
            return TextFormat.toText(item.getItemMeta().displayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
