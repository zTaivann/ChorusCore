package dev.chorus.core.chat;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Who last spoke to whom, and who is watching. */
public final class PrivateMessages {

    private final Map<UUID, UUID> sentTo = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> heardFrom = new ConcurrentHashMap<>();
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

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
        sentTo.values().removeIf(player::equals);
        heardFrom.values().removeIf(player::equals);
    }

    void clear() {
        sentTo.clear();
        heardFrom.clear();
        spies.clear();
    }
}
