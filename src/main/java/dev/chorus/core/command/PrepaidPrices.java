package dev.chorus.core.command;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Prices taken on use and not yet settled, oldest first, by player and command. */
final class PrepaidPrices {

    private final Map<Key, Deque<Payment>> paid = new ConcurrentHashMap<>();

    void add(UUID player, String command, Payment payment) {
        paid.compute(new Key(player, command), (key, waiting) -> {
            Deque<Payment> payments = waiting == null ? new ArrayDeque<>() : waiting;
            payments.addLast(payment);
            return payments;
        });
    }

    /** The oldest payment this player made for this command, or null when there is none. */
    @Nullable Payment take(UUID player, String command) {
        Payment[] taken = new Payment[1];
        paid.computeIfPresent(new Key(player, command), (key, waiting) -> {
            taken[0] = waiting.pollFirst();
            return waiting.isEmpty() ? null : waiting;
        });
        return taken[0];
    }

    /**
     * What was taken for one use.
     *
     * @param money 0 when none was
     * @param items {@link ItemPrice#NONE} when none were
     */
    record Payment(double money, ItemPrice items) {
    }

    private record Key(UUID player, String command) {
    }
}
