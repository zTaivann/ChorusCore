package dev.chorus.core.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * A screen for laying items out without ever moving one.
 *
 * <p>Clicking something in your own inventory puts a copy on the screen; clicking something
 * on the screen takes the copy off again. Nothing is ever picked up, so nothing can be lost
 * and nothing can be gained: the screen can be opened, filled, emptied and closed all day and
 * the player leaves with exactly what they walked in with.
 *
 * <p>That is not a nicety. The obvious way to build this — a real inventory, filled with the
 * kit, handing the contents back on close — gives the player a free copy of the kit every
 * time they open it and look. An editor for a kit full of diamond is not allowed to be a
 * machine for making diamond.
 */
public final class PaletteMenu implements InventoryHolder {

    private final Inventory inventory;
    private final BiConsumer<Player, ItemStack[]> onClose;

    /** How many slots at the front hold items. The rest are decoration and ignore clicks. */
    private final int capacity;

    private boolean handled;

    public PaletteMenu(Server server, Component title, int rows,
                       BiConsumer<Player, ItemStack[]> onClose) {
        this(server, title, rows, rows * 9, onClose);
    }

    /**
     * A palette that only takes so many items.
     *
     * <p>A capacity of one makes it a single slot that replaces rather than fills: choosing
     * a new icon should not mean taking the old one out first.
     */
    public PaletteMenu(Server server, Component title, int rows, int capacity,
                       BiConsumer<Player, ItemStack[]> onClose) {
        this.inventory = server.createInventory(this, rows * 9, title);
        this.capacity = Math.max(1, Math.min(capacity, rows * 9));
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
        for (int slot = 0; slot < Math.min(contents.length, capacity); slot++) {
            inventory.setItem(slot, contents[slot] == null ? null : contents[slot].clone());
        }
    }

    /** Anything past the capacity, so a single slot reads as a single slot. */
    public void surround(@Nullable ItemStack filler) {
        if (filler == null) {
            return;
        }
        for (int slot = capacity; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** A slot on the screen: the copy comes off. Decoration is not a slot. */
    void takeOff(int slot) {
        if (slot >= 0 && slot < capacity) {
            inventory.setItem(slot, null);
        }
    }

    /**
     * A slot in the player's own inventory: a copy goes on.
     *
     * <p>A full palette of one replaces what is there. Everywhere else a full palette is
     * full, and quietly dropping the oldest item to make room would be worse than doing
     * nothing at all.
     */
    void putOn(ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        int free = firstFree();
        if (free >= 0) {
            inventory.setItem(free, clicked.clone());
        } else if (capacity == 1) {
            inventory.setItem(0, clicked.clone());
        }
    }

    private int firstFree() {
        for (int slot = 0; slot < capacity; slot++) {
            ItemStack held = inventory.getItem(slot);
            if (held == null || held.getType().isAir()) {
                return slot;
            }
        }
        return -1;
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

        // Only the slots that hold items. Handing back the decoration as well would put a
        // row of glass panes into whatever was being edited.
        ItemStack[] contents = new ItemStack[capacity];
        for (int slot = 0; slot < capacity; slot++) {
            contents[slot] = inventory.getItem(slot);
        }
        onClose.accept(player, contents);
    }
}
