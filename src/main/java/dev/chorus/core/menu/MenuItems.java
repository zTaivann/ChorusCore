package dev.chorus.core.menu;

import dev.chorus.core.locale.TextFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MenuItems {

    private MenuItems() {
    }

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        return of(new ItemStack(material), name, lore);
    }

    /**
     * The same from a real item rather than a bare material, so an icon somebody chose by
     * dragging their own enchanted sword in still looks like that sword on the button.
     */
    public static ItemStack of(ItemStack template, Component name, List<Component> lore) {
        ItemStack item = template.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(TextFormat.upright(name));
        meta.lore(lore.stream().map(TextFormat::asLore).toList());
        // values() rather than named constants: the set of flags has grown over the years
        // and naming one that a version lacks would fail to link.
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public static @Nullable ItemStack filler(@Nullable Material material) {
        return material == null ? null : of(material, Component.empty(), List.of());
    }
}
