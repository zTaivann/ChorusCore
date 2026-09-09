package dev.chorus.core.items;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public record ItemSettings(Map<Material, Material> condenseRecipes, int maxLoreLines) {

    public static ItemSettings read(ConfigurationSection items, Consumer<String> onBadMaterial) {
        ConfigurationSection recipes = items.getConfigurationSection("condense");
        Map<Material, Material> condense = new LinkedHashMap<>();
        if (recipes != null) {
            for (String from : recipes.getKeys(false)) {
                Material source = material(from, onBadMaterial);
                Material result = material(recipes.getString(from), onBadMaterial);
                if (source != null && result != null) {
                    condense.put(source, result);
                }
            }
        }
        return new ItemSettings(Map.copyOf(condense), Math.max(1, items.getInt("max-lore-lines", 10)));
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
}
