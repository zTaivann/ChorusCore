package dev.chorus.core.shops;

import dev.chorus.core.config.ConfigFile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What each material is worth, for {@code /sell} and for a {@code [Sell]} sign with no price
 * written on it.
 */
public final class WorthTable {

    private static final String SECTION = "shops.worth";

    private final ConfigFile file;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    private volatile double multiplier = 1.0;

    public WorthTable(ConfigFile file) {
        this.file = file;
    }

    public void reload(Consumer<String> onUnknownMaterial) {
        prices.clear();
        multiplier = Math.max(0, file.section("shops").getDouble("sell-multiplier", 1.0));

        ConfigurationSection worth = file.data().getConfigurationSection(SECTION);
        if (worth == null) {
            return;
        }
        for (String key : worth.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                onUnknownMaterial.accept(key);
                continue;
            }
            double price = worth.getDouble(key, 0);
            if (price > 0) {
                prices.put(material, price);
            }
        }
    }

    /** Zero when nothing is worth anything, which is how an item is refused a sale. */
    public double of(Material material) {
        Double price = prices.get(material);
        return price == null ? 0 : price * multiplier;
    }

    public boolean sellable(Material material) {
        return prices.containsKey(material);
    }

    public int size() {
        return prices.size();
    }

    /** Writes a new price into the file, so /setworth survives a restart. */
    public boolean set(Material material, double price) {
        String key = material.name().toLowerCase(Locale.ROOT);
        if (price <= 0) {
            prices.remove(material);
            file.data().set(SECTION + "." + key, null);
        } else {
            prices.put(material, price);
            file.data().set(SECTION + "." + key, price);
        }
        return file.save();
    }
}
