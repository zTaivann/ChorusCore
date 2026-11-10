package dev.chorus.core.backup;

import dev.chorus.core.locale.Messages;
import org.bukkit.command.CommandSender;

import java.util.Locale;

/** Why a backup was made; decides its menu name and whether it is made. */
public enum BackupReason {

    /** The one that matters most: everything they had when they died. */
    DEATH("death", "menu.backups.reason-death"),

    /** On the way in, so there is a record of what they arrived with. */
    JOIN("join", "menu.backups.reason-join"),

    /** When the player leaves. */
    QUIT("quit", "menu.backups.reason-quit"),

    /** On the way into another world, which is where a lot of item loss happens. */
    WORLD("world", "menu.backups.reason-world"),

    /** Emptied by /clear. */
    CLEAR("clear", "menu.backups.reason-clear"),

    /** Emptied by a kit with clear-inventory on. */
    KIT("kit", "menu.backups.reason-kit"),

    /** What a restore replaced. */
    RESTORE("restore", "menu.backups.reason-restore"),

    /** Asked for, by a staff member with no particular reason. */
    MANUAL("manual", "menu.backups.reason-manual");

    private final String stored;
    private final String messageKey;

    BackupReason(String stored, String messageKey) {
        this.stored = stored;
        this.messageKey = messageKey;
    }

    public String stored() {
        return stored;
    }

    /** The key that enables or disables it, in the backups section of items.yml. */
    public String setting() {
        return stored;
    }

    /** The message key of its name. */
    public String messageKey() {
        return messageKey;
    }

    /** The reason at the start of a stored one, such as {@code kit} in {@code kit vip}. */
    public static BackupReason of(String stored) {
        int space = stored.indexOf(' ');
        String first = (space < 0 ? stored : stored.substring(0, space)).toLowerCase(Locale.ROOT);
        for (BackupReason reason : values()) {
            if (reason.stored.equals(first)) {
                return reason;
            }
        }
        return MANUAL;
    }

    /** What was appended to a stored reason: the kit, or the world they left. */
    public static String detail(String stored) {
        int space = stored.indexOf(' ');
        return space < 0 ? "" : stored.substring(space + 1);
    }

    /** A stored reason as the reader sees it, such as {@code Kit vip}. */
    public static String describe(String stored, Messages messages, CommandSender reader) {
        String name = messages.plain(reader, of(stored).messageKey());
        String detail = detail(stored);
        return detail.isEmpty() ? name : name + " " + detail;
    }
}
