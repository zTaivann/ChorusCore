package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.items.Enchantments;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Gives the held item the enchanted shimmer without an enchantment behind it. */
public final class GlowCommand extends HeldItemCommand {

    private static final String MARKER = "lure";

    public GlowCommand(CommandSupport support) {
        super(support, "glow", "chorus.items.glow");
    }

    @Override
    protected void execute(Player player, String[] args) {
        ItemStack item = held(player);
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            messages.send(player, "items.not-editable");
            return;
        }

        Enchantment marker = Enchantments.byName(MARKER);
        if (marker == null) {
            messages.send(player, "items.glow-unavailable");
            return;
        }

        boolean glowing = meta.hasEnchant(marker) && meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS);
        // An enchanted item already shimmers, and the flag would hide its enchantments.
        if (!glowing && !meta.getEnchants().isEmpty()) {
            messages.send(player, "items.glow-already-enchanted");
            return;
        }
        if (!ready(player)) {
            return;
        }

        if (glowing) {
            meta.removeEnchant(marker);
            meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            meta.addEnchant(marker, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);

        settle(player);
        messages.send(player, glowing ? "items.glow-off" : "items.glow-on");
    }
}
