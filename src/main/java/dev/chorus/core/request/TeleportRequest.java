package dev.chorus.core.request;

import java.util.UUID;

public record TeleportRequest(UUID sender, UUID target, Direction direction, long expiresAt) {

    public enum Direction {
        /** /tpa: the sender travels to the target. */
        TO_TARGET,
        /** /tpahere: the target travels to the sender. */
        TO_SENDER
    }

    public boolean hasExpired(long now) {
        return now >= expiresAt;
    }

    /** The player who actually moves once the request is accepted. */
    public UUID traveller() {
        return direction == Direction.TO_TARGET ? sender : target;
    }

    /** The player who stays put and provides the destination. */
    public UUID anchor() {
        return direction == Direction.TO_TARGET ? target : sender;
    }
}
