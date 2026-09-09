package dev.chorus.core.api.event;

import dev.chorus.core.home.Home;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired before a home is written, whether it is new or being moved. */
public final class ChorusHomeSaveEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Home home;
    private final boolean replacing;
    private boolean cancelled;

    public ChorusHomeSaveEvent(Home home, boolean replacing) {
        this.home = home;
        this.replacing = replacing;
    }

    public Home getHome() {
        return home;
    }

    /** False when this is the player's first home under that name. */
    public boolean isReplacing() {
        return replacing;
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
