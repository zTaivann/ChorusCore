package dev.chorus.core.home;

import dev.chorus.core.location.Names;
import dev.chorus.core.location.WorldLimits;
import dev.chorus.core.menu.MenuSettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.function.Consumer;

public record HomeSettings(String defaultName, int defaultLimit, int maxNameLength,
                           boolean fallbackToOnlyHome, double pricePerHome,
                           WorldLimits worldLimits, MenuSettings menu) {

    public static HomeSettings read(ConfigurationSection homes, Consumer<String> onBadMaterial) {
        ConfigurationSection menu = homes.getConfigurationSection("menu");
        return new HomeSettings(
                Names.normalise(homes.getString("default-name", "home")),
                Math.max(0, homes.getInt("default-limit", 3)),
                Math.max(1, homes.getInt("max-name-length", 16)),
                homes.getBoolean("fallback-to-only-home", true),
                Math.max(0, homes.getDouble("price-per-home", 0)),
                WorldLimits.read(homes.getConfigurationSection("world-limits")),
                MenuSettings.read(menu != null ? menu : new MemoryConfiguration(),
                        Material.LIME_BED, onBadMaterial));
    }

    /** The price of setting the given home, counting the ones already owned. */
    public double priceFor(double base, int owned, boolean replacing) {
        if (replacing || pricePerHome <= 0) {
            return base;
        }
        return base + pricePerHome * owned;
    }
}
