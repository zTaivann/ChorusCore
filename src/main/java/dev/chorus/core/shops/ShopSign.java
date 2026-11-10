package dev.chorus.core.shops;

import dev.chorus.core.block.Signs;
import dev.chorus.core.command.Numbers;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;


/**
 * A shop sign as written.
 *
 * <pre>
 *   [Buy]          [Sell]
 *   64             32
 *   diamond        iron_ingot
 *   250            40
 * </pre>
 *
 * @param buying true for a sign that sells to the player, false for one that buys from them
 * @param price  total for that amount, negative to use the worth table
 */
public record ShopSign(boolean buying, int amount, Material material, double price) {

    private static final String BUY = "[buy]";
    private static final String SELL = "[sell]";
    private static final int MAX_AMOUNT = 2304;

    /** Null when the first line is not a shop header, or when the rest does not add up. */
    public static @Nullable ShopSign read(Component header, Component second, Component third,
                                          Component fourth) {
        String kind = Signs.plain(header);
        if (!kind.equals(BUY) && !kind.equals(SELL)) {
            return null;
        }

        int amount = amount(Signs.plain(second));
        if (amount <= 0) {
            return null;
        }
        Material material = Material.matchMaterial(Signs.plain(third).replace(' ', '_'));
        if (material == null || material.isAir() || !material.isItem()) {
            return null;
        }

        String priceLine = Signs.plain(fourth);
        double price = priceLine.isEmpty() ? -1 : price(priceLine);
        if (priceLine.isEmpty() && kind.equals(BUY)) {
            // Nothing is given away by accident: a buying sign has to name its price.
            return null;
        }
        if (!priceLine.isEmpty() && price < 0) {
            return null;
        }
        return new ShopSign(kind.equals(BUY), amount, material, price);
    }

    public boolean usesWorthTable() {
        return price < 0;
    }

    private static int amount(String raw) {
        int value = Numbers.integer(raw, -1);
        return value <= 0 ? -1 : Math.min(MAX_AMOUNT, value);
    }

    private static double price(String raw) {
        return Numbers.amount(raw);
    }
}
