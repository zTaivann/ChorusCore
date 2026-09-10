package dev.chorus.core.home;

import dev.chorus.core.location.Names;
import dev.chorus.core.menu.MenuSettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public record HomeSettings(String defaultName, int defaultLimit, int maxNameLength,
                           boolean fallbackToOnlyHome, double pricePerHome,
                           Map<String, Integer> worldLimits, MenuSettings menu) {

    public static HomeSettings read(ConfigurationSection homes, Consumer<String> onBadMaterial) {
        ConfigurationSection menu = homes.getConfigurationSection("menu");
        return new HomeSettings(
                Names.normalise(homes.getString("default-name", "home")),
                Math.max(0, homes.getInt("default-limit", 3)),
                Math.max(1, homes.getInt("max-name-length", 16)),
                homes.getBoolean("fallback-to-only-home", true),
                Math.max(0, homes.getDouble("price-per-home", 0)),
                worldLimits(homes.getConfigurationSection("world-limits")),
                MenuSettings.read(menu != null ? menu : new MemoryConfiguration(),
                        Material.LIME_BED, onBadMaterial));
    }

    /**
     * What setting the given home costs, counting the ones already owned.
     *
     * <p>The command's own price is the first one; every home after that adds
     * {@code price-per-home}, so the tenth home can cost real money while the first stays
     * free. Moving a home the player already has is never charged the surcharge.
     */
    public double priceFor(double base, int owned, boolean replacing) {
        if (replacing || pricePerHome <= 0) {
            return base;
        }
        return base + pricePerHome * owned;
    }

    /** The cap for one world, or -1 when that world has none of its own. */
    public int limitIn(String world) {
        return worldLimits.getOrDefault(world.toLowerCase(Locale.ROOT), -1);
    }

    private static Map<String, Integer> worldLimits(ConfigurationSection block) {
        if (block == null) {
            return Map.of();
        }
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (String world : block.getKeys(false)) {
            limits.put(world.toLowerCase(Locale.ROOT), Math.max(0, block.getInt(world)));
        }
        return Map.copyOf(limits);
    }
}
