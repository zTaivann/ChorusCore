package dev.chorus.core.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before /pay moves any money.
 *
 * <p>Cancelling it stops the transfer before either side is touched, which is the hook a
 * tax, a log or an anti-fraud addon wants.
 */
public final class ChorusPaymentEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player from;
    private final Player to;
    private final double amount;
    private boolean cancelled;

    public ChorusPaymentEvent(Player from, Player to, double amount) {
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    public Player getFrom() {
        return from;
    }

    public Player getTo() {
        return to;
    }

    public double getAmount() {
        return amount;
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
