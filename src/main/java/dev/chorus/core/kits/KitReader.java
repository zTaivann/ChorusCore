package dev.chorus.core.kits;

import dev.chorus.core.items.Enchantments;
import dev.chorus.core.kits.rules.KitAction;
import dev.chorus.core.kits.rules.Requirement;
import dev.chorus.core.locale.TextFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
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

    public static Map<String, Kit> read(ConfigurationSection definitions,
                                        Consumer<String> onProblem) {
        Map<String, Kit> kits = new LinkedHashMap<>();
        for (String name : definitions.getKeys(false)) {
            // Only what the file on disk actually holds. The copy bundled in the jar backs
            // this config so new options appear without anyone deleting their file, and
            // without this a kit deleted with /kitedit would come straight back from it.
            if (!definitions.isSet(name)) {
                continue;
            }
            ConfigurationSection block = definitions.getConfigurationSection(name);
            if (block == null) {
                onProblem.accept("kit '" + name + "' has nothing in it");
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            kits.put(key, kit(key, name, block, onProblem));
        }
        return Map.copyOf(kits);
    }

    private static Kit kit(String key, String name, ConfigurationSection block,
                           Consumer<String> onProblem) {
        // Read once and used twice: the items are built differently when it is on, and the
        // editor has to be able to show which way round it is.
        boolean placeholders = block.getBoolean("placeholders", false);

        return new Kit(
                key,
                TextFormat.forItem(block.getString("display", name)),
                lore(block.getStringList("lore")),
                icon(block.get("icon"), key, onProblem),
                Math.max(0, block.getInt("cooldown-seconds", 0)),
                block.getBoolean("one-time", false),
                Math.max(0, block.getInt("max-claims", 0)),
                Math.max(0, block.getDouble("price", 0)),
                block.getString("permission", "chorus.kits.use." + key),
                block.getBoolean("auto-armor", true),
                block.getBoolean("clear-inventory", false),
                placeholders,
                Requirement.read(block.getList("requirements", List.of()), key, onProblem),
                KitAction.read(block.getStringList("claim-actions"), key, onProblem),
                KitAction.read(block.getStringList("fail-actions"), key, onProblem),
                items(block.getMapList("items"), placeholders, key, onProblem));
    }

    private static List<Component> lore(List<String> lines) {
        return lines.stream().map(TextFormat::forItem).toList();
    }

    private static List<KitItem> items(List<Map<?, ?>> entries, boolean placeholders,
                                       String kit, Consumer<String> onProblem) {
        List<KitItem> items = new ArrayList<>(entries.size());
        for (Map<?, ?> entry : entries) {
            ItemStack item = item(entry, kit, onProblem);
            if (item == null) {
                onProblem.accept("kit '" + kit + "' lists an item with no usable material");
                continue;
            }
            items.add(placeholders ? personal(entry, item) : KitItem.plain(item));
        }
        return List.copyOf(items);
    }

    /**
     * Keeps the written text alongside the built item, but only where it would change from
     * one player to the next.
     *
     * <p>An item with no {@code %} in it is the same for everybody however the kit is set up,
     * and there is no sense in rebuilding it sixty times a day to find that out.
     */
    private static KitItem personal(Map<?, ?> entry, ItemStack item) {
        String name = text(entry.get("name"));
        List<String> lore = entry.get("lore") instanceof List<?> lines
                ? lines.stream().map(String::valueOf).toList()
                : null;

        boolean namedByPlayer = name != null && name.indexOf('%') >= 0;
        boolean loredByPlayer = lore != null && lore.stream().anyMatch(line -> line.indexOf('%') >= 0);
        if (!namedByPlayer && !loredByPlayer) {
            return KitItem.plain(item);
        }
        return new KitItem(item, namedByPlayer ? name : null, loredByPlayer ? lore : null);
    }

    /**
     * One item from its written form.
     *
     * <p>Shared with the icon, which is the same shape: a kit shown as a named, enchanted
     * sword reads better than one shown as a plain one, and there was no reason for the icon
     * to understand less than the contents do.
     */
    private static @Nullable ItemStack item(Map<?, ?> entry, String kit,
                                            Consumer<String> onProblem) {
        Material material = material(text(entry.get("material")), null, onProblem);
        if (material == null) {
            return null;
        }

        ItemStack item = new ItemStack(material, amount(entry.get("amount")));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = text(entry.get("name"));
        if (name != null) {
            meta.displayName(TextFormat.forItem(name));
        }
        if (entry.get("lore") instanceof List<?> lines) {
            meta.lore(lines.stream().map(line -> TextFormat.forItem(String.valueOf(line))).toList());
        }
        if (entry.get("enchantments") instanceof Map<?, ?> enchantments) {
            enchant(meta, enchantments, kit, onProblem);
        }
        if (Boolean.TRUE.equals(entry.get("unbreakable"))) {
            meta.setUnbreakable(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The written form of an item, whichever shape the config hands it over in.
     *
     * <p>Setting a value in memory leaves a {@link Map} behind; reading the same file back
     * hands out a {@link ConfigurationSection} instead. Checking for only one of them is why
     * an icon could be saved, reported as saved, and still come back as a chest.
     *
     * @return null when this is not a block at all, such as a bare material name.
     */
    static @Nullable Map<?, ?> asBlock(@Nullable Object written) {
        if (written instanceof ConfigurationSection section) {
            return section.getValues(false);
        }
        return written instanceof Map<?, ?> map ? map : null;
    }

    /** The icon: a bare material name for the simple case, or an item block for the rest. */
    private static ItemStack icon(Object written, String kit,
                                  Consumer<String> onProblem) {
        Map<?, ?> block = asBlock(written);

        if (block != null) {
            ItemStack item = item(block, kit, onProblem);
            if (item != null) {
                return item;
            }
            onProblem.accept("kit '" + kit + "' has an icon with no usable material");
            return new ItemStack(Material.CHEST);
        }

        Material material = material(written == null ? null : String.valueOf(written),
                Material.CHEST, onProblem);
        return new ItemStack(material == null ? Material.CHEST : material);
    }

    /**
     * Looked up by namespaced key rather than by field name, for the reasons set out on
     * {@link Enchantments}.
     */
    private static void enchant(ItemMeta meta, Map<?, ?> entries, String kit,
                                Consumer<String> onProblem) {
        entries.forEach((name, level) -> {
            Enchantment enchantment = Enchantments.byName(String.valueOf(name));
            if (enchantment == null) {
                onProblem.accept("kit '" + kit + "' asks for an enchantment called '" + name
                        + "', which this version does not have");
                return;
            }
            meta.addEnchant(enchantment, amount(level), true);
        });
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
