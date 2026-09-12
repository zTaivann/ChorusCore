package dev.chorus.core.chat;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who last spoke to whom, and who is watching.
 *
 * <p>The two directions are kept apart. They only differ when a conversation is interrupted:
 * you message Anna, then Ben messages you, and {@code /r} has to decide which of them it
 * means. {@code /rtoggle} lets each player say.
 *
 * <p>Commands and the quit listener all run on the server thread, so plain collections are
 * enough and cheaper than concurrent ones.
 */
public final class PrivateMessages {

    private final Map<UUID, UUID> sentTo = new HashMap<>();
    private final Map<UUID, UUID> heardFrom = new HashMap<>();
    private final Set<UUID> spies = new HashSet<>();

    private volatile ChatSettings settings;

    PrivateMessages(ChatSettings settings) {
        this.settings = settings;
    }

    public ChatSettings settings() {
        return settings;
    }

    void apply(ChatSettings updated) {
        this.settings = updated;
    }

    /**
     * Who {@code /reply} would answer.
     *
     * @param preferSender true to answer whoever last spoke to them, false to carry on the
     *                     conversation they started. Either way the other one stands in when
     *                     there is nothing on the preferred side.
     */
    public @Nullable UUID replyTarget(UUID player, boolean preferSender) {
        UUID first = preferSender ? heardFrom.get(player) : sentTo.get(player);
        return first != null ? first : (preferSender ? sentTo : heardFrom).get(player);
    }

    public void remember(UUID from, UUID to) {
        sentTo.put(from, to);
        heardFrom.put(to, from);
    }

    /** @return the state the player is now in. */
    public boolean toggleSpy(UUID player) {
        if (spies.remove(player)) {
            return false;
        }
        spies.add(player);
        return true;
    }

    public Set<UUID> spies() {
        return Collections.unmodifiableSet(spies);
    }

    public void forget(UUID player) {
        spies.remove(player);
        sentTo.remove(player);
        heardFrom.remove(player);
        // Anybody still pointing at a player who is gone would otherwise keep a dead name.
        sentTo.values().removeIf(player::equals);
        heardFrom.values().removeIf(player::equals);
    }

    void clear() {
        sentTo.clear();
        heardFrom.clear();
        spies.clear();
    }
}
