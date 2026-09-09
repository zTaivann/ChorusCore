package dev.chorus.core.utility;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public record UtilitySettings(Heal heal, Feed feed, Fix fix, Disposal disposal,
                              Speed speed, Mirror invsee, Top top, Near near) {

    public record Heal(boolean extinguish, boolean clearEffects, boolean restoreFood) {
    }

    public record Feed(float saturation) {
    }

    public record Fix(boolean allowAll, Set<Material> blacklist) {
    }

    public record Disposal(int rows) {
    }

    public record Speed(double maximum) {
    }

    public record Mirror(Material filler, int refreshTicks) {
    }

    public record Top(boolean allowInNether) {
    }

    public record Near(int defaultRadius, int maxRadius) {
    }

    /** {@code onBadMaterial} receives any name in the config that is not a material. */
    public static UtilitySettings read(ConfigurationSection utility, Consumer<String> onBadMaterial) {
        ConfigurationSection heal = child(utility, "heal");
        ConfigurationSection feed = child(utility, "feed");
        ConfigurationSection fix = child(utility, "fix");
        ConfigurationSection disposal = child(utility, "disposal");
        ConfigurationSection speed = child(utility, "speed");
        ConfigurationSection invsee = child(utility, "invsee");
        ConfigurationSection top = child(utility, "top");
        ConfigurationSection near = child(utility, "near");

        Set<Material> blacklist = EnumSet.noneOf(Material.class);
        for (String name : fix.getStringList("blacklist")) {
            Material material = material(name, onBadMaterial);
            if (material != null) {
                blacklist.add(material);
            }
        }

        Material filler = material(invsee.getString("filler", "GRAY_STAINED_GLASS_PANE"), onBadMaterial);
        int maxRadius = Math.max(1, near.getInt("max-radius", 500));

        return new UtilitySettings(
                new Heal(
                        heal.getBoolean("extinguish", true),
                        heal.getBoolean("clear-effects", false),
                        heal.getBoolean("restore-food", false)),
                new Feed((float) Math.max(0, feed.getDouble("saturation", 20))),
                new Fix(fix.getBoolean("allow-all", true), blacklist),
                new Disposal(Math.min(6, Math.max(1, disposal.getInt("rows", 4)))),
                new Speed(Math.max(1, speed.getDouble("maximum", 10))),
                new Mirror(
                        filler == null ? Material.GRAY_STAINED_GLASS_PANE : filler,
                        Math.max(0, invsee.getInt("refresh-ticks", 10))),
                new Top(top.getBoolean("allow-in-nether", false)),
                new Near(
                        Math.min(maxRadius, Math.max(1, near.getInt("default-radius", 100))),
                        maxRadius));
    }

    private static Material material(String name, Consumer<String> onBadMaterial) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            onBadMaterial.accept(name);
        }
        return material;
    }

    private static ConfigurationSection child(ConfigurationSection parent, String name) {
        ConfigurationSection section = parent.getConfigurationSection(name);
        return section != null ? section : new MemoryConfiguration();
    }
}
