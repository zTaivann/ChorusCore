package dev.chorus.core.preference;

/** The preferences a player can set for themselves, kept between sessions. */
public enum PlayerPreference {

    /** Turns down every incoming teleport request without the sender having to be told twice. */
    TELEPORTS_BLOCKED("teleports-blocked"),

    /** Turns down every private message, from anybody. */
    MESSAGES_BLOCKED("messages-blocked"),

    /** Makes /reply answer whoever last spoke to them rather than whoever they last wrote to. */
    REPLY_TO_SENDER("reply-to-sender"),

    /** Refuses every payment. */
    PAYMENTS_BLOCKED("payments-blocked"),

    /** Accepts every teleport request without being asked. */
    TELEPORTS_AUTOMATIC("teleports-automatic"),

    /** Leaves every powertool in place but stops any of them running. */
    POWERTOOLS_OFF("powertools-off");

    private final String stored;

    PlayerPreference(String stored) {
        this.stored = stored;
    }

    public String stored() {
        return stored;
    }
}
