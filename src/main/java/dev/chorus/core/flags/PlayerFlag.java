package dev.chorus.core.flags;

/**
 * The switches a player can leave set for themselves, kept between sessions.
 *
 * <p>The stored name is written down here rather than taken from the constant, so renaming
 * one in Java never quietly loses what every player had chosen.
 */
public enum PlayerFlag {

    /** Turns down every incoming teleport request without the sender having to be told twice. */
    TELEPORTS_BLOCKED("teleports-blocked");

    private final String stored;

    PlayerFlag(String stored) {
        this.stored = stored;
    }

    public String stored() {
        return stored;
    }
}
