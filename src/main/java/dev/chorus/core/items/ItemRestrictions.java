package dev.chorus.core.items;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.permissions.Permissible;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What may be handed out and what may be put on an item.
 *
 * <p>Every rule here is off in the file that ships, so a server that has never opened this
 * section behaves exactly as it did before. {@code chorus.bypass.restrictions} is above all
 * of them.
 */
public record ItemRestrictions(Set<Material> blockedItems, boolean permissionPerItem,
                               Set<String> blockedEnchantments, boolean permissionPerEnchantment,
                               boolean unsafeEnchantments) {

    public static final ItemRestrictions NONE =
            new ItemRestrictions(Set.of(), false, Set.of(), false, true);

    private static final String BYPASS = "chorus.bypass.restrictions";
    private static final String ITEM = "chorus.items.give.item.";
    private static final String ENCHANTMENT = "chorus.items.enchant.";

    /** Whether this sender may bring an item into the world with /give or /more. */
    public boolean mayHave(Permissible who, Material material) {
        if (who.hasPermission(BYPASS)) {
            return true;
        }
        if (blockedItems.contains(material)) {
            return false;
        }
        return !permissionPerItem
                || who.hasPermission(ITEM + material.name().toLowerCase(Locale.ROOT));
    }

    /** Whether this sender may put an enchantment on with /enchant. {@code name} is its id. */
    public boolean mayEnchant(Permissible who, String name) {
        if (who.hasPermission(BYPASS)) {
            return true;
        }
        if (blockedEnchantments.contains(name)) {
            return false;
        }
        return !permissionPerEnchantment || who.hasPermission(ENCHANTMENT + name);
    }

    /** Whether a level beyond what the game allows is possible at all on this server. */
    public boolean allowsUnsafeLevels() {
        return unsafeEnchantments;
    }

    public static ItemRestrictions read(ConfigurationSection items, Consumer<String> onBadMaterial) {
        ConfigurationSection rules = items.getConfigurationSection("restrictions");
        if (rules == null) {
            return NONE;
        }

        Set<Material> blocked = EnumSet.noneOf(Material.class);
        for (String name : rules.getStringList("blocked-items")) {
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                onBadMaterial.accept(name);
                continue;
            }
            blocked.add(material);
        }

        Set<String> enchantments = new HashSet<>();
        for (String name : rules.getStringList("blocked-enchantments")) {
            enchantments.add(name.trim().toLowerCase(Locale.ROOT));
        }

        return new ItemRestrictions(
                Set.copyOf(blocked),
                rules.getBoolean("permission-per-item", false),
                Set.copyOf(enchantments),
                rules.getBoolean("permission-per-enchantment", false),
                rules.getBoolean("unsafe-enchantments", true));
    }
}
