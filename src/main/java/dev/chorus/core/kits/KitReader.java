package dev.chorus.core.kits;

import dev.chorus.core.locale.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Turns the kits section of the config into ready-made items. */
public final class KitReader {

    private KitReader() {
    }

    public static Map<String, Kit> read(ConfigurationSection definitions, Messages messages,
                                        Consumer<String> onProblem) {
        Map<String, Kit> kits = new LinkedHashMap<>();
        for (String name : definitions.getKeys(false)) {
            ConfigurationSection block = definitions.getConfigurationSection(name);
            if (block == null) {
                onProblem.accept("kit '" + name + "' has nothing in it");
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            kits.put(key, new Kit(
                    key,
                    messages.parse(block.getString("display", name)),
                    lore(block.getStringList("lore"), messages),
                    material(block.getString("icon", "CHEST"), Material.CHEST, onProblem),
                    Math.max(0, block.getInt("cooldown-seconds", 0)),
                    block.getBoolean("one-time", false),
                    Math.max(0, block.getDouble("price", 0)),
                    block.getString("permission", "chorus.kits.use." + key),
                    items(block.getMapList("items"), messages, key, onProblem)));
        }
        return Map.copyOf(kits);
    }

    private static List<Component> lore(List<String> lines, Messages messages) {
        return lines.stream().map(line -> plain(messages.parse(line))).toList();
    }

    private static List<ItemStack> items(List<Map<?, ?>> entries, Messages messages,
                                         String kit, Consumer<String> onProblem) {
        List<ItemStack> items = new ArrayList<>(entries.size());
        for (Map<?, ?> entry : entries) {
            Material material = material(text(entry.get("material")), null, onProblem);
            if (material == null) {
                onProblem.accept("kit '" + kit + "' lists an item with no usable material");
                continue;
            }

            ItemStack item = new ItemStack(material, amount(entry.get("amount")));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String name = text(entry.get("name"));
                if (name != null) {
                    meta.displayName(plain(messages.parse(name)));
                }
                if (entry.get("lore") instanceof List<?> lines) {
                    meta.lore(lines.stream().map(line -> plain(messages.parse(String.valueOf(line)))).toList());
                }
                if (entry.get("enchantments") instanceof Map<?, ?> enchantments) {
                    enchant(meta, enchantments, kit, onProblem);
                }
                item.setItemMeta(meta);
            }
            items.add(item);
        }
        return List.copyOf(items);
    }

    /**
     * Looked up by namespaced key rather than by field name: the constants were renamed
     * between versions, but {@code minecraft:protection} means the same thing on all of them.
     */
    private static void enchant(ItemMeta meta, Map<?, ?> entries, String kit,
                                Consumer<String> onProblem) {
        entries.forEach((name, level) -> {
            NamespacedKey key = NamespacedKey.minecraft(String.valueOf(name).toLowerCase(Locale.ROOT));
            Enchantment enchantment = Enchantment.getByKey(key);
            if (enchantment == null) {
                onProblem.accept("kit '" + kit + "' asks for an enchantment called '" + name
                        + "', which this version does not have");
                return;
            }
            meta.addEnchant(enchantment, amount(level), true);
        });
    }

    /** Item names and lore are rendered in italics by default, which nobody ever wants. */
    private static Component plain(Component text) {
        return Component.text().decoration(TextDecoration.ITALIC, false).append(text).build();
    }

    private static @Nullable Material material(@Nullable String name, @Nullable Material fallback,
                                               Consumer<String> onProblem) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            onProblem.accept("this server has no material called '" + name + "'");
            return fallback;
        }
        return material;
    }

    private static @Nullable String text(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int amount(@Nullable Object value) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return 1;
    }
}
