package dev.chorus.core.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * A screen for laying items out without ever moving one.
 *
 * <p>Clicking something in your own bags puts a copy on the screen; clicking something on the
 * screen takes the copy off again. Nothing is ever picked up, so nothing can be lost and
 * nothing can be gained: the screen can be opened, filled, emptied and closed all day and the
 * player leaves with exactly what they walked in with.
 *
 * <p>That is not a nicety. The obvious way to build this — a real inventory, filled with the
 * kit, handing the contents back on close — gives the player a free copy of the kit every
 * time they open it and look. An editor for a kit full of diamond is not allowed to be a
 * machine for making diamond.
 */
public final class PaletteMenu implements InventoryHolder {

    private final Inventory inventory;
    private final BiConsumer<Player, ItemStack[]> onClose;

    private boolean handled;

    public PaletteMenu(Server server, Component title, int rows,
                       BiConsumer<Player, ItemStack[]> onClose) {
        this.inventory = server.createInventory(this, rows * 9, title);
        this.onClose = onClose;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public int size() {
        return inventory.getSize();
    }

    public void fill(ItemStack[] contents) {
        for (int slot = 0; slot < Math.min(contents.length, inventory.getSize()); slot++) {
            inventory.setItem(slot, contents[slot] == null ? null : contents[slot].clone());
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** A slot on the screen: the copy comes off. */
    void takeOff(int slot) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, null);
        }
    }

    /** A slot in the player's own bags: a copy goes on, if there is room and something to copy. */
    void putOn(ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir() || inventory.firstEmpty() < 0) {
            return;
        }
        inventory.addItem(clicked.clone());
    }

    /**
     * Runs the callback exactly once, whatever the server does with the close event.
     *
     * <p>Opening an inventory while a close is being handled makes the server close the one
     * still on screen, which is this one, and the close comes straight back round. Without
     * this flag that is an endless loop.
     */
    void closed(Player player) {
        if (handled) {
            return;
        }
        handled = true;
        onClose.accept(player, inventory.getContents());
    }
}
