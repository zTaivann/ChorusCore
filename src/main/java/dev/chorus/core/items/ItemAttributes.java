package dev.chorus.core.items;

import dev.chorus.core.locale.TextFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The {@code name:}, {@code lore:} and {@code enchant:} words that can follow an item.
 *
 * <p>One place for all of them, so {@code /give} and anything else that builds an item read
 * the same syntax. Underscores in text become spaces, which is the only way to write a
 * sentence as one argument.
 *
 * <p>A word that means nothing is reported and skipped rather than refusing the whole item:
 * somebody getting a diamond sword without the lore they typed is a better outcome than
 * getting nothing and having to work out which of eight words was wrong.
 */
public final class ItemAttributes {

    private static final String GLOW_MARKER = "lure";
    private static final int MAX_LORE = 20;

    private ItemAttributes() {
    }

    /**
     * Applies every word to the stack.
     *
     * @param allowFormatting whether colour codes in the text are obeyed or shown as typed
     * @param onProblem       told about each word that meant nothing
     */
    public static void apply(ItemStack stack, List<String> words, boolean allowFormatting,
                             Consumer<String> onProblem) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            words.forEach(onProblem);
            return;
        }

        List<String> lore = new ArrayList<>();
        boolean touched = false;

        for (String word : words) {
            int colon = word.indexOf(':');
            String key = (colon < 0 ? word : word.substring(0, colon)).toLowerCase(Locale.ROOT);
            String value = colon < 0 ? "" : word.substring(colon + 1);

            boolean known = switch (key) {
                case "name", "n" -> name(meta, value, allowFormatting);
                case "lore", "l", "desc" -> lore(lore, value);
                case "enchant", "ench", "e" -> enchant(meta, value);
                case "unbreakable", "nobreak" -> unbreakable(meta);
                case "glow", "shine" -> glow(meta);
                case "hide", "hideflags" -> hide(meta);
                case "durability", "damage", "dura" -> durability(meta, stack, value);
                case "colour", "color" -> colour(meta, value);
                case "owner", "head", "skull" -> owner(meta, value);
                case "amount", "size" -> amount(stack, value);
                default -> false;
            };

            if (known) {
                touched = true;
            } else {
                onProblem.accept(word);
            }
        }

        if (!lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> allowFormatting ? TextFormat.forLore(line)
                            : TextFormat.asLore(Component.text(line)))
                    .toList());
        }
        if (touched || !lore.isEmpty()) {
            stack.setItemMeta(meta);
        }
    }

    private static boolean name(ItemMeta meta, String value, boolean allowFormatting) {
        if (value.isEmpty()) {
            return false;
        }
        String text = spaces(value);
        meta.displayName(allowFormatting
                ? TextFormat.forItem(text)
                : TextFormat.upright(Component.text(text)));
        return true;
    }

    /** {@code lore:one|two} is two lines; the same word twice adds to what is there. */
    private static boolean lore(List<String> lore, String value) {
        if (value.isEmpty() || lore.size() >= MAX_LORE) {
            return false;
        }
        for (String line : value.split("\\|")) {
            if (lore.size() < MAX_LORE) {
                lore.add(spaces(line));
            }
        }
        return true;
    }

    private static boolean enchant(ItemMeta meta, String value) {
        int colon = value.lastIndexOf(':');
        String name = colon < 0 ? value : value.substring(0, colon);
        Enchantment enchantment = Enchantments.byName(name);
        if (enchantment == null) {
            return false;
        }

        int level = 1;
        if (colon >= 0) {
            try {
                level = Integer.parseInt(value.substring(colon + 1));
            } catch (NumberFormatException notANumber) {
                return false;
            }
        }
        if (level <= 0) {
            meta.removeEnchant(enchantment);
            return true;
        }
        meta.addEnchant(enchantment, level, true);
        return true;
    }

    private static boolean unbreakable(ItemMeta meta) {
        meta.setUnbreakable(true);
        return true;
    }

    /**
     * The shimmer without an enchantment behind it: a level that does nothing, hidden from
     * the tooltip.
     */
    private static boolean glow(ItemMeta meta) {
        Enchantment marker = Enchantments.byName(GLOW_MARKER);
        if (marker == null) {
            return false;
        }
        meta.addEnchant(marker, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return true;
    }

    private static boolean hide(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.values());
        return true;
    }

    private static boolean durability(ItemMeta meta, ItemStack stack, String value) {
        if (!(meta instanceof Damageable damageable) || stack.getType().getMaxDurability() <= 0) {
            return false;
        }
        try {
            int left = Integer.parseInt(value);
            int max = stack.getType().getMaxDurability();
            damageable.setDamage(Math.max(0, Math.min(max, max - left)));
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    private static boolean colour(ItemMeta meta, String value) {
        if (!(meta instanceof LeatherArmorMeta leather)) {
            return false;
        }
        Color colour = parseColour(value);
        if (colour == null) {
            return false;
        }
        leather.setColor(colour);
        return true;
    }

    private static boolean owner(ItemMeta meta, String value) {
        if (!(meta instanceof SkullMeta skull) || value.isEmpty()) {
            return false;
        }
        // Only a player the server already knows: looking a name up with Mojang is a web
        // request, and this runs on the server thread.
        OfflinePlayer known = Bukkit.getPlayerExact(value);
        if (known == null) {
            known = Bukkit.getOfflinePlayerIfCached(value);
        }
        if (known == null) {
            return false;
        }
        skull.setOwningPlayer(known);
        return true;
    }

    private static boolean amount(ItemStack stack, String value) {
        try {
            int amount = Integer.parseInt(value);
            if (amount <= 0) {
                return false;
            }
            stack.setAmount(Math.min(amount, stack.getType().getMaxStackSize()));
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /** {@code 255,0,0} or {@code #ff0000}. */
    public static @Nullable Color parseColour(String value) {
        String text = value.trim();
        try {
            if (text.startsWith("#")) {
                return Color.fromRGB(Integer.parseInt(text.substring(1), 16));
            }
            String[] parts = text.split(",");
            if (parts.length != 3) {
                return null;
            }
            return Color.fromRGB(
                    clamp(Integer.parseInt(parts[0].trim())),
                    clamp(Integer.parseInt(parts[1].trim())),
                    clamp(Integer.parseInt(parts[2].trim())));
        } catch (IllegalArgumentException unusable) {
            return null;
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static String spaces(String value) {
        return value.replace('_', ' ');
    }
}
