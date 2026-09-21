package dev.chorus.core.menu;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * How a list menu looks. The bottom row is always the navigation strip, so a menu of three
 * rows shows eighteen entries per page and nothing ever moves under the cursor.
 */
public record MenuSettings(boolean enabled, int rows, Material icon, @Nullable Material filler,
                           @Nullable Material navigationFiller, Material previousPage,
                           Material nextPage, Material close) {

    public static MenuSettings read(ConfigurationSection menu, Material defaultIcon,
                                    Consumer<String> onBadMaterial) {
        return new MenuSettings(
                menu.getBoolean("enabled", true),
                Math.min(6, Math.max(2, menu.getInt("rows", 3))),
                material(menu.getString("icon"), defaultIcon, onBadMaterial),
                material(menu.getString("filler"), null, onBadMaterial),
                material(menu.getString("navigation-filler"), Material.GRAY_STAINED_GLASS_PANE,
                        onBadMaterial),
                material(menu.getString("previous-page"), Material.ARROW, onBadMaterial),
                material(menu.getString("next-page"), Material.ARROW, onBadMaterial),
                material(menu.getString("close"), Material.BARRIER, onBadMaterial));
    }

    /** Entries that fit on one page, the bottom row being reserved for navigation. */
    public int perPage() {
        return (rows - 1) * 9;
    }

    public int size() {
        return rows * 9;
    }

    public int previousSlot() {
        return size() - 9;
    }

    public int closeSlot() {
        return size() - 5;
    }

    public int nextSlot() {
        return size() - 1;
    }

    /** Only drawn on a screen that was opened from another one. */
    /** The free spot on the navigation row, for a screen with a control of its own. */
    public int controlSlot() {
        return size() - 3;
    }

    public int backSlot() {
        return size() - 7;
    }

    private static @Nullable Material material(@Nullable String name, @Nullable Material fallback,
                                               Consumer<String> onBadMaterial) {
        if (name == null || name.isBlank()) {
            return name == null ? fallback : null;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            onBadMaterial.accept(name);
            return fallback;
        }
        return material;
    }
}
