package dev.chorus.core.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired before any teleport this plugin performs, warmup included. */
public final class ChorusTeleportEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String cause;
    private Location destination;
    private boolean cancelled;

    public ChorusTeleportEvent(Player player, Location destination, String cause) {
        this.player = player;
        this.destination = destination;
        this.cause = cause;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getDestination() {
        return destination;
    }

    /** Sending them somewhere else instead is allowed. */
    public void setDestination(Location destination) {
        this.destination = destination;
    }

    /** The command behind it: "home", "warp", "spawn", "back", "tpaccept", "api" and so on. */
    public String getCause() {
        return cause;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
