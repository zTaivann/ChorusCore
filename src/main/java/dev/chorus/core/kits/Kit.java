package dev.chorus.core.kits;

import dev.chorus.core.rules.Action;
import dev.chorus.core.rules.Requirement;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** One kit as the config describes it. */
public record Kit(String name, Component display, List<Component> lore, ItemStack icon,
                  int cooldownSeconds, boolean oneTime, int maxClaims, double price,
                  String permission, boolean autoArmor, boolean clearInventory,
                  boolean placeholders, List<Requirement> requirements, List<Action> claimActions,
                  List<Action> failActions, List<KitItem> items) {

    public boolean allowed(Player player) {
        return permission.isEmpty() || player.hasPermission(permission);
    }

    /** Fresh copies for one player, with their placeholders filled in. */
    public List<ItemStack> contents(@Nullable Player player) {
        return items.stream().map(item -> item.build(player)).toList();
    }

    /** The same for a screen nobody is taking the kit from, where a placeholder stays a word. */
    public List<ItemStack> contents() {
        return contents(null);
    }
}
