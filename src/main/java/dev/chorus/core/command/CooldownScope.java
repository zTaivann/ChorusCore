package dev.chorus.core.command;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/** Who a cooldown holds back once somebody starts it. */
public enum CooldownScope {

    /** Only the player who used the command. */
    PLAYER("cooldown.wait"),

    /** Everyone in the world it was used in. */
    WORLD("cooldown.wait-world"),

    /** Everyone on the server. */
    SERVER("cooldown.wait-server");

    private static final UUID WHOLE_SERVER = new UUID(0, 0);

    private final String waitMessage;

    CooldownScope(String waitMessage) {
        this.waitMessage = waitMessage;
    }

    /** Whose timer it is: the player's, their world's or the server's. */
    public UUID owner(Player player) {
        return switch (this) {
            case PLAYER -> player.getUniqueId();
            case WORLD -> player.getWorld().getUID();
            case SERVER -> WHOLE_SERVER;
        };
    }

    /** The message key for somebody this scope holds back. */
    public String waitMessage() {
        return waitMessage;
    }

    /** Null when the text names none of them. */
    public static @Nullable CooldownScope of(String text) {
        String wanted = text.trim().toUpperCase(Locale.ROOT);
        for (CooldownScope scope : values()) {
            if (scope.name().equals(wanted)) {
                return scope;
            }
        }
        return null;
    }
}
