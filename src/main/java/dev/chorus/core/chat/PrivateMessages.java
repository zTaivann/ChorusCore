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
 * <p>Commands and the quit listener all run on the server thread, so plain collections are
 * enough and cheaper than concurrent ones.
 */
public final class PrivateMessages {

    private final Map<UUID, UUID> lastPartner = new HashMap<>();
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

    public @Nullable UUID lastPartner(UUID player) {
        return lastPartner.get(player);
    }

    /** Both sides point at each other, so /reply works from either end. */
    public void remember(UUID from, UUID to) {
        lastPartner.put(from, to);
        lastPartner.put(to, from);
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
        UUID partner = lastPartner.remove(player);
        // Their partner's arrow points back at a player who is gone, so drop that too.
        if (partner != null && player.equals(lastPartner.get(partner))) {
            lastPartner.remove(partner);
        }
    }

    void clear() {
        lastPartner.clear();
        spies.clear();
    }
}
