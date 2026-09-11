package dev.chorus.core.kits;

import dev.chorus.core.kits.rules.KitAction;
import dev.chorus.core.kits.rules.Requirement;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * One kit as the config describes it.
 *
 * <p>The items are built once when the file is read, not every time somebody takes the kit,
 * and handed out as copies so a kit can never be drained by giving it away.
 *
 * <p>Running commands is not a field of its own: a command is an action like any other, so
 * {@code console:} and {@code player:} live in {@code claim-actions} beside the messages and
 * the sounds. One list to read, one order to reason about.
 */
public record Kit(String name, Component display, List<Component> lore, ItemStack icon,
                  int cooldownSeconds, boolean oneTime, int maxClaims, double price,
                  String permission, boolean autoArmor, boolean clearInventory,
                  List<Requirement> requirements, List<KitAction> claimActions,
                  List<KitAction> failActions, List<ItemStack> items) {

    public boolean allowed(Player player) {
        return permission.isEmpty() || player.hasPermission(permission);
    }

    /** Fresh copies, so handing the kit out never touches what the config loaded. */
    public List<ItemStack> contents() {
        return items.stream().map(ItemStack::clone).toList();
    }
}
