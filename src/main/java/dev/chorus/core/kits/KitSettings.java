package dev.chorus.core.kits;

import dev.chorus.core.menu.MenuSettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.Locale;
import java.util.function.Consumer;

public record KitSettings(String firstJoinKit, MenuSettings menu) {

    public static KitSettings read(ConfigurationSection kits, Consumer<String> onBadMaterial) {
        ConfigurationSection menu = kits.getConfigurationSection("menu");
        return new KitSettings(
                kits.getString("first-join", "").trim().toLowerCase(Locale.ROOT),
                MenuSettings.read(menu != null ? menu : new MemoryConfiguration(),
                        Material.CHEST, onBadMaterial));
    }
}
