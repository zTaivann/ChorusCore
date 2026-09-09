package dev.chorus.core.utility;

import dev.chorus.core.locale.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps every open /invsee and /ecsee window in step with the player it is showing.
 *
 * <p>The refresh task only exists while somebody actually has a window open, so a server
 * where nobody uses the command pays nothing for it at all.
 */
public final class MirrorService implements Listener {

    private final Plugin plugin;
    private final Messages messages;
    private final UtilityService utility;
    private final Map<UUID, InventoryMirror> open = new HashMap<>();

    private BukkitTask refresher;

    MirrorService(Plugin plugin, Messages messages, UtilityService utility) {
        this.plugin = plugin;
        this.messages = messages;
        this.utility = utility;
    }

    public void open(Player viewer, Player target, InventoryMirror.Kind kind, boolean editable) {
        String titleKey = kind == InventoryMirror.Kind.ENDER_CHEST
                ? "utility.ecsee-title"
                : "utility.invsee-title";
        Component title = messages.render(titleKey, "player", target.getName());

        InventoryMirror mirror = new InventoryMirror(plugin.getServer(), target, kind, editable,
                title, utility.settings().invsee().filler());
        mirror.refresh(target, infoName(target), infoLore(target));

        open.put(viewer.getUniqueId(), mirror);
        viewer.openInventory(mirror.getInventory());
        startRefreshing();
    }

    void shutdown() {
        stopRefreshing();
        open.clear();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (open.isEmpty()) {
            return;
        }
        InventoryMirror mirror = mirrorOf(event.getView().getTopInventory().getHolder());
        if (mirror == null) {
            return;
        }

        boolean intoMirror = event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (!mirror.editable() || (intoMirror && mirror.isLocked(event.getRawSlot()))) {
            event.setCancelled(true);
            return;
        }
        // Shift-clicking from the player's own bags also lands in the window.
        scheduleWriteBack(mirror);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (open.isEmpty()) {
            return;
        }
        InventoryMirror mirror = mirrorOf(event.getView().getTopInventory().getHolder());
        if (mirror == null) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize && (!mirror.editable() || mirror.isLocked(slot))) {
                event.setCancelled(true);
                return;
            }
        }
        scheduleWriteBack(mirror);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryMirror mirror = open.get(event.getPlayer().getUniqueId());
        if (mirror != null && mirror.getInventory().equals(event.getInventory())) {
            open.remove(event.getPlayer().getUniqueId());
            stopRefreshingIfIdle();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        open.remove(playerId);

        // Anyone watching the player who just left has nothing left to watch.
        List<Player> watchers = new ArrayList<>();
        open.forEach((viewerId, mirror) -> {
            if (mirror.targetId().equals(playerId)) {
                Player viewer = plugin.getServer().getPlayer(viewerId);
                if (viewer != null) {
                    watchers.add(viewer);
                }
            }
        });
        watchers.forEach(Player::closeInventory);
        stopRefreshingIfIdle();
    }

    private void scheduleWriteBack(InventoryMirror mirror) {
        // The click has not been applied yet, so the copy has to wait a tick.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player target = plugin.getServer().getPlayer(mirror.targetId());
            if (target != null) {
                mirror.writeBack(target);
            }
        });
    }

    private void startRefreshing() {
        int period = utility.settings().invsee().refreshTicks();
        if (refresher != null || period <= 0) {
            return;
        }
        refresher = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::refreshAll, period, period);
    }

    private void stopRefreshingIfIdle() {
        if (open.isEmpty()) {
            stopRefreshing();
        }
    }

    private void stopRefreshing() {
        if (refresher != null) {
            refresher.cancel();
            refresher = null;
        }
    }

    private void refreshAll() {
        open.values().forEach(mirror -> {
            Player target = plugin.getServer().getPlayer(mirror.targetId());
            if (target != null) {
                mirror.refresh(target, infoName(target), infoLore(target));
            }
        });
    }

    private InventoryMirror mirrorOf(InventoryHolder holder) {
        return holder instanceof InventoryMirror mirror ? mirror : null;
    }

    private Component infoName(Player target) {
        return messages.render("utility.invsee-info", "player", target.getName());
    }

    private List<Component> infoLore(Player target) {
        return List.of(
                messages.render("utility.invsee-info-health",
                        "health", round(target.getHealth()),
                        "max", round(target.getMaxHealth())),
                messages.render("utility.invsee-info-food",
                        "food", String.valueOf(target.getFoodLevel())),
                messages.render("utility.invsee-info-level",
                        "level", String.valueOf(target.getLevel())),
                messages.render("utility.invsee-info-gamemode",
                        "gamemode", target.getGameMode().name().toLowerCase(Locale.ROOT)),
                messages.render("utility.invsee-info-location",
                        "world", target.getWorld().getName(),
                        "x", String.valueOf(target.getLocation().getBlockX()),
                        "y", String.valueOf(target.getLocation().getBlockY()),
                        "z", String.valueOf(target.getLocation().getBlockZ())));
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
