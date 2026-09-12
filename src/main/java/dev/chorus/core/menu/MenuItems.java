package dev.chorus.core.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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

        meta.displayName(upright(name));
        meta.lore(lore.stream().map(MenuItems::upright).toList());
        // values() rather than named constants: the set of flags has grown over the years
        // and naming one that a version lacks would fail to link.
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public static @Nullable ItemStack filler(@Nullable Material material) {
        return material == null ? null : of(material, Component.empty(), List.of());
    }

    /**
     * The game draws anything written on an item in italics unless told otherwise, which is
     * never what a menu wants and is not something the line asked for.
     *
     * <p>Set on the outside, so a line that does want italics still gets them: a style on a
     * child beats the one it inherits.
     */
    private static Component upright(Component text) {
        return text.style().decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET
                ? text.decoration(TextDecoration.ITALIC, false)
                : text;
    }
}
