package dev.chorus.core.kits.menu;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

/** Which block each button in the kit editor is made of. */
public record KitMenuSettings(int itemRows, @Nullable Material filler, Material icon,
                              Material lore, Material items,
                              Material preview, Material cooldown, Material maxClaims,
                              Material price, Material permission, Material enabled,
                              Material disabled, Material requirements, Material claimActions,
                              Material failActions, Material add, Material newKit, Material back,
                              Material delete, Material close) {

    public static KitMenuSettings read(ConfigurationSection editor, Consumer<String> onBadMaterial) {
        return new KitMenuSettings(
                Math.min(6, Math.max(1, editor.getInt("item-rows", 4))),
                material(editor, "filler", null, onBadMaterial),
                material(editor, "icon", Material.ITEM_FRAME, onBadMaterial),
                material(editor, "lore", Material.BOOK, onBadMaterial),
                material(editor, "items", Material.CHEST, onBadMaterial),
                material(editor, "preview", Material.ENDER_CHEST, onBadMaterial),
                material(editor, "cooldown", Material.CLOCK, onBadMaterial),
                material(editor, "max-claims", Material.PAPER, onBadMaterial),
                material(editor, "price", Material.GOLD_INGOT, onBadMaterial),
                material(editor, "permission", Material.NAME_TAG, onBadMaterial),
                material(editor, "enabled", Material.LIME_DYE, onBadMaterial),
                material(editor, "disabled", Material.GRAY_DYE, onBadMaterial),
                material(editor, "requirements", Material.COMPARATOR, onBadMaterial),
                material(editor, "claim-actions", Material.EXPERIENCE_BOTTLE, onBadMaterial),
                material(editor, "fail-actions", Material.FIRE_CHARGE, onBadMaterial),
                material(editor, "add", Material.EMERALD, onBadMaterial),
                material(editor, "new-kit", Material.WRITABLE_BOOK, onBadMaterial),
                material(editor, "back", Material.ARROW, onBadMaterial),
                material(editor, "delete", Material.LAVA_BUCKET, onBadMaterial),
                material(editor, "close", Material.BARRIER, onBadMaterial));
    }

    private static @Nullable Material material(ConfigurationSection editor, String key,
                                              @Nullable Material fallback,
                                              Consumer<String> onBadMaterial) {
        String name = editor.getString(key);
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            onBadMaterial.accept(name);
            return fallback;
        }
        return material;
    }
}
