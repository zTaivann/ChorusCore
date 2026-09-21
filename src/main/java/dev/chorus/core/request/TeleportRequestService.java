package dev.chorus.core.request;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The open /tpa requests, kept per target and in the order they arrived so that a bare
 * /tpaccept answers the most recent one.
 */
public final class TeleportRequestService {

    private final Map<UUID, LinkedHashMap<UUID, TeleportRequest>> byTarget = new HashMap<>();

    private int timeoutSeconds;

    TeleportRequestService(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    void apply(int updated) {
        this.timeoutSeconds = updated;
    }

    /** @return false when the sender already has a request waiting for that target. */
    public boolean add(TeleportRequest request) {
        LinkedHashMap<UUID, TeleportRequest> waiting =
                byTarget.computeIfAbsent(request.target(), target -> new LinkedHashMap<>());
        if (waiting.containsKey(request.sender())) {
            return false;
        }
        waiting.put(request.sender(), request);
        return true;
    }

    public List<TeleportRequest> incoming(UUID target) {
        LinkedHashMap<UUID, TeleportRequest> waiting = byTarget.get(target);
        return waiting == null ? List.of() : List.copyOf(waiting.values());
    }

    public @Nullable TeleportRequest removeLatest(UUID target) {
        LinkedHashMap<UUID, TeleportRequest> waiting = byTarget.get(target);
        if (waiting == null || waiting.isEmpty()) {
            return null;
        }
        // Insertion order, so the last key is the request that arrived most recently.
        UUID newest = null;
        for (UUID sender : waiting.keySet()) {
            newest = sender;
        }
        return remove(target, newest);
    }

    public @Nullable TeleportRequest remove(UUID target, UUID sender) {
        LinkedHashMap<UUID, TeleportRequest> waiting = byTarget.get(target);
        if (waiting == null) {
            return null;
        }
        TeleportRequest removed = waiting.remove(sender);
        if (waiting.isEmpty()) {
            byTarget.remove(target);
        }
        return removed;
    }

    public List<TeleportRequest> removeAllSentBy(UUID sender) {
        List<TeleportRequest> removed = new ArrayList<>();
        prune(request -> {
            if (!request.sender().equals(sender)) {
                return false;
            }
            removed.add(request);
            return true;
        });
        return removed;
    }

    /** Everything involving this player, in either direction. Used when they disconnect. */
    public void forget(UUID playerId) {
        byTarget.remove(playerId);
        prune(request -> request.sender().equals(playerId));
    }

    public List<TeleportRequest> removeExpired(long now) {
        List<TeleportRequest> expired = new ArrayList<>();
        prune(request -> {
            if (!request.hasExpired(now)) {
                return false;
            }
            expired.add(request);
            return true;
        });
        return expired;
    }

    public boolean isEmpty() {
        return byTarget.isEmpty();
    }

    void clear() {
        byTarget.clear();
    }

    private void prune(Predicate<TeleportRequest> doomed) {
        Iterator<Map.Entry<UUID, LinkedHashMap<UUID, TeleportRequest>>> targets =
                byTarget.entrySet().iterator();
        while (targets.hasNext()) {
            Map.Entry<UUID, LinkedHashMap<UUID, TeleportRequest>> entry = targets.next();
            entry.getValue().values().removeIf(doomed);
            if (entry.getValue().isEmpty()) {
                targets.remove();
            }
        }
    }
}
