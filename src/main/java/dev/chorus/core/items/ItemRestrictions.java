package dev.chorus.core.items;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
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
    private static final String UNSAFE = "chorus.items.enchant.unsafe";

    /** As high as an enchantment goes here, whatever the permission. */
    public static final int MAX_LEVEL = 255;

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

    /**
     * The highest level this sender may put on, which is the vanilla maximum unless both the
     * server and their permissions allow going past it.
     *
     * <p>One place for it, so {@code /enchant} and the {@code enchant:} word behind
     * {@code /give} cannot drift into allowing different things.
     */
    public int highestLevel(Permissible who, Enchantment enchantment) {
        boolean beyond = unsafeEnchantments
                && (who.hasPermission(BYPASS) || who.hasPermission(UNSAFE));
        return beyond ? MAX_LEVEL : enchantment.getMaxLevel();
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
