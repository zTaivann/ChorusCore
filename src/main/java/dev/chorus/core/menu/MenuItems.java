package dev.chorus.core.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MenuItems {

    private MenuItems() {
    }

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            // values() rather than named constants: the set of flags has grown over the
            // years and naming one that a version lacks would fail to link.
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static @Nullable ItemStack filler(@Nullable Material material) {
        return material == null ? null : of(material, Component.empty(), List.of());
    }
}
