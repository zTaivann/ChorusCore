package dev.chorus.core.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (holder instanceof Menu menu) {
            // Menus hold no real items. Shift-click and number keys reach the top inventory too.
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < menu.size()) {
                menu.click(player, event.getRawSlot(), event.getClick());
            }
            return;
        }

        if (holder instanceof PaletteMenu palette) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < palette.size()) {
                palette.takeOff(slot);
            } else {
                palette.putOn(event.getCurrentItem());
            }
            // Cancelling makes the client redraw what it had, so the screen is sent again.
            player.updateInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof Menu || holder instanceof PaletteMenu) {
            event.setCancelled(true);
        }
    }

    /** A palette keeps whatever was left on it. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PaletteMenu palette
                && event.getPlayer() instanceof Player player) {
            palette.closed(player);
        }
    }
}
