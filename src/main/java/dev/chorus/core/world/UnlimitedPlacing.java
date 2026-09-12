package dev.chorus.core.world;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The blocks a player may place without using them up.
 *
 * <p>Per material rather than all or nothing, so a builder can hand out stone for ever while
 * their diamonds stay countable. The list is held in memory and goes when they log out: an
 * unlimited stack that outlives a session is how a creative world leaks into a survival one.
 */
public final class UnlimitedPlacing implements Listener {

    private final Map<UUID, Set<Material>> allowed = new HashMap<>();

    /** @return whether the material is now unlimited for this player. */
    public boolean toggle(Player player, Material material) {
        Set<Material> materials = allowed.computeIfAbsent(player.getUniqueId(),
                key -> EnumSet.noneOf(Material.class));
        if (materials.remove(material)) {
            if (materials.isEmpty()) {
                allowed.remove(player.getUniqueId());
            }
            return false;
        }
        materials.add(material);
        return true;
    }

    public Set<Material> of(Player player) {
        Set<Material> materials = allowed.get(player.getUniqueId());
        return materials == null ? Set.of() : Set.copyOf(materials);
    }

    public boolean clear(Player player) {
        return allowed.remove(player.getUniqueId()) != null;
    }

    public void clearAll() {
        allowed.clear();
    }

    /**
     * Puts the block back in the hand after it was placed.
     *
     * <p>At MONITOR and only for a place that actually went through, so a protection plugin
     * cancelling the placement leaves the stack exactly as it was.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (allowed.isEmpty()) {
            return;
        }
        Set<Material> materials = allowed.get(event.getPlayer().getUniqueId());
        if (materials == null || !materials.contains(event.getBlockPlaced().getType())) {
            return;
        }

        EquipmentSlot slot = event.getHand();
        ItemStack used = event.getItemInHand();
        if (used == null || used.getType().isAir()) {
            return;
        }

        ItemStack refilled = used.clone();
        refilled.setAmount(used.getType().getMaxStackSize());
        if (slot == EquipmentSlot.OFF_HAND) {
            event.getPlayer().getInventory().setItemInOffHand(refilled);
        } else {
            event.getPlayer().getInventory().setItemInMainHand(refilled);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        allowed.remove(event.getPlayer().getUniqueId());
    }
}
