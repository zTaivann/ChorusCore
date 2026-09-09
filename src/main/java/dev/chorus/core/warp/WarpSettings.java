package dev.chorus.core.warp;

import dev.chorus.core.menu.MenuSettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.permissions.Permissible;

import java.util.function.Consumer;

public record WarpSettings(boolean perWarpPermission, int maxNameLength, MenuSettings menu) {

    public static WarpSettings read(ConfigurationSection warps, Consumer<String> onBadMaterial) {
        ConfigurationSection menu = warps.getConfigurationSection("menu");
        return new WarpSettings(
                warps.getBoolean("per-warp-permission", false),
                Math.max(1, warps.getInt("max-name-length", 24)),
                MenuSettings.read(menu != null ? menu : new MemoryConfiguration(),
                        Material.ENDER_PEARL, onBadMaterial));
    }

    public boolean canUse(Permissible who, String warp) {
        return !perWarpPermission || who.hasPermission("chorus.warp.use." + warp);
    }
}
