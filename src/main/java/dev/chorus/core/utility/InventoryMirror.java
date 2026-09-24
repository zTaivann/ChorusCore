package dev.chorus.core.utility;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** A live window onto another player's inventory or ender chest. */
public final class InventoryMirror implements InventoryHolder {

    public enum Kind {
        INVENTORY(54, 18, 45),
        ENDER_CHEST(36, 9, -1);

        private final int size;
        private final int storageStart;
        private final int hotbarStart;

        Kind(int size, int storageStart, int hotbarStart) {
            this.size = size;
            this.storageStart = storageStart;
            this.hotbarStart = hotbarStart;
        }
    }

    private static final int HELMET = 0;
    private static final int CHESTPLATE = 1;
    private static final int LEGGINGS = 2;
    private static final int BOOTS = 3;
    private static final int OFFHAND = 4;
    private static final int INFO = 8;

    private final UUID targetId;
    private final Kind kind;
    private final boolean editable;
    private final ItemStack filler;
    private final Inventory inventory;

    /** Set by an edit until it has been written back, so a refresh in between cannot undo it. */
    private volatile boolean edited;

    InventoryMirror(Server server, Player target, Kind kind, boolean editable,
                    Component title, Material fillerMaterial) {
        this.targetId = target.getUniqueId();
        this.kind = kind;
        this.editable = editable;
        this.filler = blank(fillerMaterial);
        this.inventory = server.createInventory(this, kind.size, title);
        decorate();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public UUID targetId() {
        return targetId;
    }

    public boolean editable() {
        return editable;
    }

    /** Slots that hold decoration or the info head, and may never be touched. */
    public boolean isLocked(int slot) {
        if (kind == Kind.ENDER_CHEST) {
            return slot < kind.storageStart;
        }
        return slot >= 5 && slot < kind.storageStart;
    }

    /** Copies the player's current contents into the window, leaving untouched slots alone. */
    public void refresh(Player target, Component infoName, List<Component> infoLore) {
        if (edited) {
            return;
        }
        if (kind == Kind.ENDER_CHEST) {
            copyInto(target.getEnderChest().getContents(), kind.storageStart, 27);
        } else {
            PlayerInventory source = target.getInventory();
            set(HELMET, source.getHelmet());
            set(CHESTPLATE, source.getChestplate());
            set(LEGGINGS, source.getLeggings());
            set(BOOTS, source.getBoots());
            set(OFFHAND, source.getItemInOffHand());

            ItemStack[] contents = source.getContents();
            for (int index = 0; index < 27; index++) {
                set(kind.storageStart + index, contents[9 + index]);
            }
            for (int index = 0; index < 9; index++) {
                set(kind.hotbarStart + index, contents[index]);
            }
        }
        set(INFO, head(target, infoName, infoLore));
    }

    public void markEdited() {
        edited = true;
    }

    /** Pushes whatever is in the window back onto the player. */
    public void writeBack(Player target) {
        if (!editable) {
            return;
        }
        edited = false;
        if (kind == Kind.ENDER_CHEST) {
            Inventory ender = target.getEnderChest();
            for (int index = 0; index < 27; index++) {
                ender.setItem(index, inventory.getItem(kind.storageStart + index));
            }
            return;
        }

        PlayerInventory destination = target.getInventory();
        destination.setHelmet(inventory.getItem(HELMET));
        destination.setChestplate(inventory.getItem(CHESTPLATE));
        destination.setLeggings(inventory.getItem(LEGGINGS));
        destination.setBoots(inventory.getItem(BOOTS));
        destination.setItemInOffHand(inventory.getItem(OFFHAND));

        for (int index = 0; index < 27; index++) {
            destination.setItem(9 + index, inventory.getItem(kind.storageStart + index));
        }
        for (int index = 0; index < 9; index++) {
            destination.setItem(index, inventory.getItem(kind.hotbarStart + index));
        }
    }

    private void decorate() {
        for (int slot = 0; slot < kind.storageStart; slot++) {
            if (isLocked(slot) && slot != INFO) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private void copyInto(ItemStack[] source, int firstSlot, int count) {
        for (int index = 0; index < count; index++) {
            set(firstSlot + index, index < source.length ? source[index] : null);
        }
    }

    /** Only writes when the slot really changed, so an open window does not flicker. */
    private void set(int slot, @Nullable ItemStack item) {
        ItemStack current = inventory.getItem(slot);
        ItemStack wanted = item == null || item.getType().isAir() ? null : item;
        if (current == null ? wanted == null : current.equals(wanted)) {
            return;
        }
        inventory.setItem(slot, wanted);
    }

    private static ItemStack head(Player target, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                // Online, so the profile is in memory. It would be a blocking lookup otherwise.
                skull.setOwningPlayer(target);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack blank(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }
}
