package dev.chorus.core.backup;

import java.util.Locale;

/**
 * Why a copy was taken, which decides both what it is called on screen and whether it is
 * taken at all.
 */
public enum BackupReason {

    /** The one that matters most: everything they had when they died. */
    DEATH("death"),

    /** On the way in, so there is a record of what they arrived with. */
    JOIN("join"),

    /** On the way out, so a rollback has something to go back to. */
    QUIT("quit"),

    /** On the way into another world, which is where a lot of item loss happens. */
    WORLD("world"),

    /** Emptied by /clear. */
    CLEAR("clear"),

    /** Emptied by a kit with clear-inventory on. */
    KIT("kit"),

    /** Replaced by a restore, so the restore itself can be undone. */
    RESTORE("restore"),

    /** Asked for, by a staff member with no particular reason. */
    MANUAL("manual");

    private final String stored;

    BackupReason(String stored) {
        this.stored = stored;
    }

    public String stored() {
        return stored;
    }

    /** The key it is switched on and off by, in the backups section of items.yml. */
    public String setting() {
        return stored;
    }

    public static BackupReason of(String stored) {
        for (BackupReason reason : values()) {
            if (reason.stored.equals(stored.toLowerCase(Locale.ROOT))) {
                return reason;
            }
        }
        return MANUAL;
    }
}
