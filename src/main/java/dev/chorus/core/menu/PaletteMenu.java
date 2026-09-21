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

/** A screen for laying items out without ever moving one. */
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

    /** A palette that only takes so many items. */
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

    /** A slot in the player's own inventory: a copy goes on. */
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

    /** Runs the callback exactly once, whatever the server does with the close event. */
    void closed(Player player) {
        if (handled) {
            return;
        }
        handled = true;

        // Only the slots that hold items, not the decoration.
        ItemStack[] contents = new ItemStack[capacity];
        for (int slot = 0; slot < capacity; slot++) {
            contents[slot] = inventory.getItem(slot);
        }
        onClose.accept(player, contents);
    }
}
