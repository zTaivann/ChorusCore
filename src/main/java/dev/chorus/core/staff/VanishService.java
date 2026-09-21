package dev.chorus.core.staff;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Who is hidden from whom. */
public final class VanishService implements Listener {

    private static final String SEE_PERMISSION = "chorus.staff.vanish.see";

    private final Plugin plugin;
    private final Set<UUID> vanished = new HashSet<>();

    VanishService(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(UUID playerId) {
        return vanished.contains(playerId);
    }

    /** @return the state the player is now in. */
    public boolean toggle(Player player) {
        if (vanished.remove(player.getUniqueId())) {
            forEachOther(player, viewer -> viewer.showPlayer(plugin, player));
            return false;
        }
        vanished.add(player.getUniqueId());
        hideFromEveryone(player);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (vanished.contains(joining.getUniqueId())) {
            hideFromEveryone(joining);
        }
        if (joining.hasPermission(SEE_PERMISSION)) {
            return;
        }
        // Anyone already vanished has to be hidden from the player who just arrived.
        for (UUID hiddenId : vanished) {
            Player hidden = plugin.getServer().getPlayer(hiddenId);
            if (hidden != null && !hidden.equals(joining)) {
                joining.hidePlayer(plugin, hidden);
            }
        }
    }

    void shutdown() {
        vanished.clear();
    }

    private void hideFromEveryone(Player player) {
        forEachOther(player, viewer -> {
            if (!viewer.hasPermission(SEE_PERMISSION)) {
                viewer.hidePlayer(plugin, player);
            }
        });
    }

    private void forEachOther(Player player, Consumer<Player> action) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                action.accept(viewer);
            }
        }
    }
}
