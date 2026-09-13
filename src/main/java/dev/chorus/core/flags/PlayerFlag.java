package dev.chorus.core.flags;

/**
 * The switches a player can leave set for themselves, kept between sessions.
 *
 * <p>The stored name is written down here rather than taken from the constant, so renaming
 * one in Java never quietly loses what every player had chosen.
 */
public enum PlayerFlag {

    /** Turns down every incoming teleport request without the sender having to be told twice. */
    TELEPORTS_BLOCKED("teleports-blocked"),

    /** Turns down every private message, from anybody. */
    MESSAGES_BLOCKED("messages-blocked"),

    /** Makes /reply answer whoever last spoke to them rather than whoever they last wrote to. */
    REPLY_TO_SENDER("reply-to-sender"),

    /** Turns down every payment, so nobody can push money onto them. */
    PAYMENTS_BLOCKED("payments-blocked"),

    /** Accepts every teleport request without being asked. */
    TELEPORTS_AUTOMATIC("teleports-automatic"),

    /** Leaves every powertool in place but stops any of them running. */
    POWERTOOLS_OFF("powertools-off");

    private final String stored;

    PlayerFlag(String stored) {
        this.stored = stored;
    }

    public String stored() {
        return stored;
    }
}
