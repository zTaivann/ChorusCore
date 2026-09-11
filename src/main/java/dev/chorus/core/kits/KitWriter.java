package dev.chorus.core.kits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns real items back into the shape {@link KitReader} reads.
 *
 * <p>Deliberately not Bukkit's own item serialisation. That would be shorter to write and
 * would carry every last scrap of NBT, but it writes an opaque block with a data version
 * stamped into it: unreadable to whoever opens the file, and not something one jar spanning
 * 1.18 to 26 can promise to read back on a different version.
 *
 * <p>So a kit written from an inventory comes out looking exactly like one typed by hand,
 * and can be edited by hand afterwards. The cost is that anything the format cannot say —
 * custom model data, a plugin's own tags — is not carried over, and the command says so.
 */
final class KitWriter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

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

    /**
     * Whether an item carries more than the written form can express.
     *
     * <p>{@code hasCustomModelData} is deprecated on new servers in favour of a component
     * that 1.18 has never heard of. It is not marked for removal, and it is the only way to
     * ask the question on both, so it stays.
     */
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
            written.put("name", serialise(meta.displayName()));
        }
        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            written.put("lore", lore.stream().map(KitWriter::serialise).toList());
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

    private static String serialise(Component text) {
        return text == null ? "" : MINI_MESSAGE.serialize(text);
    }
}
