package dev.chorus.core.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A read-only screen of clickable icons.
 *
 * <p>Every click is cancelled by {@link MenuListener}, so nothing in a menu can ever be
 * taken out or dropped in. A slot does whatever action was attached to it and nothing else.
 */
public final class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();

    public Menu(Server server, Component title, int rows) {
        this.inventory = server.createInventory(this, rows * 9, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public int size() {
        return inventory.getSize();
    }

    public void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    public void set(int slot, ItemStack item, Consumer<Player> action) {
        setPerClick(slot, item, (player, click) -> action.accept(player));
    }

    /**
     * For the screens where a right click means something different from a left one.
     *
     * <p>Named apart from {@link #set} rather than overloading it: two methods taking
     * functional interfaces of different arities cannot be told apart by a lambda that does
     * not spell out its parameter types, and every call site here is one of those.
     */
    public void setPerClick(int slot, ItemStack item, BiConsumer<Player, ClickType> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    /** Puts the filler in every slot nothing else claimed, between the two bounds. */
    public void fill(int from, int to, ItemStack filler) {
        if (filler == null) {
            return;
        }
        for (int slot = Math.max(0, from); slot < Math.min(to, inventory.getSize()); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    void click(Player player, int slot, ClickType type) {
        BiConsumer<Player, ClickType> action = actions.get(slot);
        if (action != null) {
            action.accept(player, type);
        }
    }
}
