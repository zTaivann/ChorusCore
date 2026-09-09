package dev.chorus.core.kits;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * One kit as the config describes it.
 *
 * <p>The items are built once when the file is read, not every time somebody takes the kit,
 * and handed out as copies so a kit can never be drained by giving it away.
 */
public record Kit(String name, Component display, List<Component> lore, Material icon,
                  int cooldownSeconds, boolean oneTime, double price, String permission,
                  List<ItemStack> items) {

    public boolean allowed(Player player) {
        return permission.isEmpty() || player.hasPermission(permission);
    }

    /** Fresh copies, so handing the kit out never touches what the config loaded. */
    public List<ItemStack> contents() {
        return items.stream().map(ItemStack::clone).toList();
    }
}
