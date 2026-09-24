package dev.chorus.core.items;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What each player last copied off a sign. Goes when they log out. */
public final class SignClipboard implements Listener {

    private final Map<UUID, List<Component>> lines = new ConcurrentHashMap<>();

    public void put(Player player, List<Component> copied) {
        lines.put(player.getUniqueId(), List.copyOf(copied));
    }

    public @Nullable List<Component> of(Player player) {
        return lines.get(player.getUniqueId());
    }

    public void clear() {
        lines.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lines.remove(event.getPlayer().getUniqueId());
    }
}
