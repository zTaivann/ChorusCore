package dev.chorus.core.kits;

import dev.chorus.core.kits.rules.Placeholders;
import dev.chorus.core.locale.TextFormat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One thing in a kit, built once and handed out as copies.
 *
 * <p>Most items are the same for everybody, so the finished {@link ItemStack} is built when
 * the file is read and never again. An item whose name or lore mentions a placeholder cannot
 * be: {@code %player%} is a different word for every person who takes the kit, so those keep
 * their text and are finished off at the moment they are handed over.
 *
 * <p>The two are kept apart on purpose. A kit of sixty plain items is not made slower by one
 * server somewhere wanting a dated certificate in theirs.
 */
public record KitItem(ItemStack item, @Nullable String name, @Nullable List<String> lore) {

    /** A plain item: nothing about it changes from one player to the next. */
    public static KitItem plain(ItemStack item) {
        return new KitItem(item, null, null);
    }

    /** Whether this one has to be finished off per player. */
    public boolean personal() {
        return name != null || lore != null;
    }

    /**
     * A copy to hand over.
     *
     * @param player whose placeholders to fill, or null for a preview nobody is taking.
     */
    public ItemStack build(@Nullable Player player) {
        ItemStack copy = item.clone();
        if (player == null || !personal()) {
            return copy;
        }

        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        if (name != null) {
            meta.displayName(TextFormat.forItem(Placeholders.fill(player, name)));
        }
        if (lore != null) {
            meta.lore(lore.stream()
                    .map(line -> TextFormat.forLore(Placeholders.fill(player, line)))
                    .toList());
        }
        copy.setItemMeta(meta);
        return copy;
    }
}
