package dev.chorus.core.staff;

import dev.chorus.core.locale.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Closes the door to everyone but staff, for the ten minutes after something has gone wrong.
 *
 * <p>The check is on login rather than pre-login: permissions are only attached once the
 * player object exists, and a lockdown that could not tell staff from anyone else would lock
 * out the very people who need to get in.
 *
 * <p>Paper has marked {@code PlayerLoginEvent} for removal, and the compiler says so on every
 * recent build. It is kept anyway because the event it will be replaced by does not exist on
 * 1.18, and one jar cannot listen for a class half the versions it runs on have never had.
 * The day it goes, this is the only place that has to change.
 *
 * <p>Not remembered across a restart, on purpose. Coming back up already closed, with nobody
 * having said so, is how a server stays empty all evening by accident.
 */
public final class LockdownService implements Listener {

    private static final String BYPASS_PERMISSION = "chorus.staff.lockdown.bypass";

    private final Messages messages;

    private volatile boolean active;
    private volatile String reason = "";

    LockdownService(Messages messages) {
        this.messages = messages;
    }

    public boolean isActive() {
        return active;
    }

    public String reason() {
        return reason;
    }

    public void open() {
        this.active = false;
        this.reason = "";
    }

    public void close(@Nullable String why) {
        this.reason = why == null ? "" : why;
        this.active = true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (!active || event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        if (event.getPlayer().hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, reason.isEmpty()
                ? messages.render("staff.lockdown-kick")
                : messages.render("staff.lockdown-kick-reason", "reason", reason));
    }
}
