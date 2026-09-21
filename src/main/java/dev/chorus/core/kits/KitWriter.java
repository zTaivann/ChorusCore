package dev.chorus.core.kits;

import dev.chorus.core.locale.TextFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns real items back into the shape {@link KitReader} reads. */
final class KitWriter {

    private KitWriter() {
    }

    /** @return the items in config form, skipping the empty slots. */
    static List<Map<String, Object>> describe(ItemStack[] contents) {
        List<Map<String, Object>> written = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            written.add(describe(item));
        }
        return written;
    }

    /** Whether an item carries more than the written form can express. */
    static boolean losesDetail(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.hasCustomModelData() || !meta.getPersistentDataContainer().isEmpty()
                || !meta.getItemFlags().isEmpty();
    }

    /** One item on its own, which is what an icon is. */
    static Map<String, Object> describe(ItemStack item) {
        // Linked so the file reads in the order a person would write it.
        Map<String, Object> written = new LinkedHashMap<>();
        written.put("material", item.getType().name());
        if (item.getAmount() != 1) {
            written.put("amount", item.getAmount());
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return written;
        }
        if (meta.hasDisplayName()) {
            written.put("name", TextFormat.toText(meta.displayName()));
        }
        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            written.put("lore", lore.stream().map(TextFormat::toText).toList());
        }
        if (meta.isUnbreakable()) {
            written.put("unbreakable", true);
        }
        if (!meta.getEnchants().isEmpty()) {
            Map<String, Object> enchantments = new LinkedHashMap<>();
            meta.getEnchants().forEach((enchantment, level) ->
                    enchantments.put(key(enchantment), level));
            written.put("enchantments", enchantments);
        }
        return written;
    }

    /** Namespaced keys, matching what the reader looks up. */
    private static String key(Enchantment enchantment) {
        return enchantment.getKey().getKey();
    }
}
