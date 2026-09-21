package dev.chorus.core.shops.chest;

import dev.chorus.core.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** One trade, start to finish, without ever letting go of the thread. */
public final class ChestShopTrade {

    /** How it went. Each one has a line in the messages folder. */
    public enum Result {
        DONE,
        /** The shop has none of the item left. */
        NO_STOCK,
        /** The buyer has nowhere to put it. */
        NO_SPACE,
        /** The buyer cannot afford it. */
        NO_MONEY,
        /** The seller is not carrying any. */
        NOTHING_CARRIED,
        /** The shop's container is full. */
        SHOP_FULL,
        /** The shop's owner cannot pay. */
        SHOP_BROKE,
        /** The money would not move, so nothing did. */
        FAILED
    }

    /**
     * @param amount how many actually changed hands
     * @param money  what was actually paid
     */
    public record Trade(Result result, int amount, double money) {

        static Trade refused(Result result) {
            return new Trade(result, 0, 0);
        }
    }

    private ChestShopTrade() {
    }

    /** The shop hands items to the player and takes their money. */
    public static Trade buy(Player buyer, ChestShop shop, Inventory stock, ItemStack template,
                            int wanted, Economy economy, OfflinePlayer owner) {
        int available = shop.unlimited() ? wanted : count(stock, template);
        if (available <= 0) {
            return Trade.refused(Result.NO_STOCK);
        }
        int room = space(buyer.getInventory(), template, wanted);
        if (room <= 0) {
            return Trade.refused(Result.NO_SPACE);
        }

        int amount = Math.min(wanted, Math.min(available, room));
        if (!economy.has(buyer, money(shop, amount))) {
            return new Trade(Result.NO_MONEY, amount, money(shop, amount));
        }

        // Out of the shop first. Everything below puts them straight back if it cannot finish.
        int taken = shop.unlimited() ? amount : take(stock, template, amount);
        if (taken <= 0) {
            return Trade.refused(Result.NO_STOCK);
        }

        double cost = money(shop, taken);
        if (!economy.withdraw(buyer, cost)) {
            returnToStock(shop, stock, template, taken);
            return new Trade(Result.NO_MONEY, taken, cost);
        }

        int leftover = put(buyer.getInventory(), template, taken);
        if (leftover > 0) {
            returnToStock(shop, stock, template, leftover);
            economy.deposit(buyer, money(shop, leftover));
        }

        int delivered = taken - leftover;
        if (delivered <= 0) {
            return Trade.refused(Result.NO_SPACE);
        }

        double paid = money(shop, delivered);
        if (!shop.unlimited() && !economy.deposit(owner, paid)) {
            // The buyer has their items and has paid; the shop could not take the money.
            return new Trade(Result.FAILED, delivered, paid);
        }
        return new Trade(Result.DONE, delivered, paid);
    }

    /** The player hands items to the shop and takes its money. */
    public static Trade sell(Player seller, ChestShop shop, Inventory stock, ItemStack template,
                             int wanted, Economy economy, OfflinePlayer owner) {
        int carrying = count(seller.getInventory(), template);
        if (carrying <= 0) {
            return Trade.refused(Result.NOTHING_CARRIED);
        }
        int room = shop.unlimited() ? wanted : space(stock, template, wanted);
        if (room <= 0) {
            return Trade.refused(Result.SHOP_FULL);
        }

        int amount = Math.min(wanted, Math.min(carrying, room));
        if (!shop.unlimited() && !economy.has(owner, money(shop, amount))) {
            return new Trade(Result.SHOP_BROKE, amount, money(shop, amount));
        }

        // Out of the seller first, for the same reason.
        int taken = take(seller.getInventory(), template, amount);
        if (taken <= 0) {
            return Trade.refused(Result.NOTHING_CARRIED);
        }

        double payout = money(shop, taken);
        if (!shop.unlimited() && !economy.withdraw(owner, payout)) {
            put(seller.getInventory(), template, taken);
            return new Trade(Result.SHOP_BROKE, taken, payout);
        }

        int leftover = shop.unlimited() ? 0 : put(stock, template, taken);
        if (leftover > 0) {
            put(seller.getInventory(), template, leftover);
            if (!shop.unlimited()) {
                economy.deposit(owner, money(shop, leftover));
            }
        }

        int sold = taken - leftover;
        if (sold <= 0) {
            return Trade.refused(Result.SHOP_FULL);
        }

        double paid = money(shop, sold);
        if (!economy.deposit(seller, paid)) {
            // Nothing has left the shop yet, so all of it goes back.
            if (!shop.unlimited()) {
                take(stock, template, sold);
                economy.deposit(owner, paid);
            }
            put(seller.getInventory(), template, sold);
            return Trade.refused(Result.FAILED);
        }
        return new Trade(Result.DONE, sold, paid);
    }

    /** How much of the item is in a shop right now, for the sign and the info screen. */
    public static int stockOf(ChestShop shop, Inventory stock, ItemStack template) {
        return shop.unlimited() ? Integer.MAX_VALUE : count(stock, template);
    }

    /** How many more a buying shop could still take. */
    public static int roomIn(ChestShop shop, Inventory stock, ItemStack template, int wanted) {
        return shop.unlimited() ? wanted : space(stock, template, wanted);
    }

    private static void returnToStock(ChestShop shop, Inventory stock, ItemStack template,
                                      int amount) {
        if (!shop.unlimited()) {
            put(stock, template, amount);
        }
    }

    private static double money(ChestShop shop, int amount) {
        return Math.round(shop.price() * amount * 100.0) / 100.0;
    }

    static int count(Inventory inventory, ItemStack template) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(template)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Stops counting once there is enough, since the answer is only ever compared. */
    static int space(Inventory inventory, ItemStack template, int wanted) {
        int stackSize = template.getMaxStackSize();
        int room = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                room += stackSize;
            } else if (stack.isSimilar(template)) {
                room += Math.max(0, stackSize - stack.getAmount());
            }
            if (room >= wanted) {
                return wanted;
            }
        }
        return room;
    }

    /** @return how many were actually removed, which may be fewer than asked for. */
    static int take(Inventory inventory, ItemStack template, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        int left = amount;

        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !stack.isSimilar(template)) {
                continue;
            }
            int taken = Math.min(left, stack.getAmount());
            left -= taken;
            if (taken == stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - taken);
            }
        }
        inventory.setStorageContents(contents);
        return amount - left;
    }

    /** @return how many did not fit and were not put anywhere. */
    static int put(Inventory inventory, ItemStack template, int amount) {
        int left = amount;
        int stackSize = template.getMaxStackSize();

        while (left > 0) {
            int batch = Math.min(stackSize, left);
            ItemStack giving = template.clone();
            giving.setAmount(batch);

            Map<Integer, ItemStack> rejected = inventory.addItem(giving);
            if (!rejected.isEmpty()) {
                int refused = 0;
                for (ItemStack stack : rejected.values()) {
                    refused += stack.getAmount();
                }
                return left - batch + refused;
            }
            left -= batch;
        }
        return 0;
    }
}
