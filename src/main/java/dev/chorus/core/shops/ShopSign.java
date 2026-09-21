package dev.chorus.core.shops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A shop sign as it is written: what it does, how much of what, and for how much.
 *
 * <pre>
 *   [Buy]          [Sell]
 *   64             32
 *   diamond        iron_ingot
 *   250            40
 * </pre>
 *
 * @param buying   true for a sign that sells to the player, false for one that buys from them
 * @param amount   how many change hands at once
 * @param material what they are
 * @param price    the total for that many, or negative to use the worth table
 */
public record ShopSign(boolean buying, int amount, Material material, double price) {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String BUY = "[buy]";
    private static final String SELL = "[sell]";
    private static final int MAX_AMOUNT = 2304;

    /** Null when the first line is not a shop header, or when the rest does not add up. */
    public static @Nullable ShopSign read(Component header, Component second, Component third,
                                          Component fourth) {
        String kind = plain(header);
        if (!kind.equals(BUY) && !kind.equals(SELL)) {
            return null;
        }

        int amount = amount(plain(second));
        if (amount <= 0) {
            return null;
        }
        Material material = Material.matchMaterial(plain(third).replace(' ', '_'));
        if (material == null || material.isAir() || !material.isItem()) {
            return null;
        }

        String priceLine = plain(fourth);
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
        try {
            int value = Integer.parseInt(raw);
            return value <= 0 ? -1 : Math.min(MAX_AMOUNT, value);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private static double price(String raw) {
        try {
            double value = Double.parseDouble(raw.replace(',', '.').replace("$", ""));
            return Double.isFinite(value) && value >= 0 ? value : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private static String plain(Component line) {
        return PLAIN.serialize(line).trim().toLowerCase(Locale.ROOT);
    }
}
