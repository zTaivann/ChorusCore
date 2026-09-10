package dev.chorus.core.warp;

import dev.chorus.core.command.CommandRules;
import org.jetbrains.annotations.Nullable;

/**
 * What a single warp costs, who may use it and how it looks.
 *
 * <p>Kept in the database rather than in a config file: warps are made in game with
 * {@code /setwarp}, and a server owner should not have to open a file and restart to say
 * that the one they just made costs money.
 *
 * <p>Every field has a "not set" value that means "use whatever the /warp command block
 * says", so a warp nobody has configured behaves exactly as warps did before any of this
 * existed.
 */
public record WarpDetails(String warp, @Nullable String icon, @Nullable String permission,
                          double price, int cooldownSeconds, @Nullable String description,
                          @Nullable String section, long uses) {

    public static final double INHERIT_PRICE = -1;
    public static final int INHERIT_COOLDOWN = -1;

    public static WarpDetails blank(String warp) {
        return new WarpDetails(warp, null, null, INHERIT_PRICE, INHERIT_COOLDOWN, null, null, 0);
    }

    public boolean isBlank() {
        return icon == null && permission == null && description == null && section == null
                && price == INHERIT_PRICE && cooldownSeconds == INHERIT_COOLDOWN;
    }

    /** The command's own rules, with whatever this warp overrides folded in. */
    public CommandRules over(CommandRules base) {
        return new CommandRules(base.enabled(), base.warmupSeconds(),
                cooldownSeconds == INHERIT_COOLDOWN ? base.cooldownSeconds() : cooldownSeconds,
                price == INHERIT_PRICE ? base.price() : price,
                base.feedback());
    }

    public WarpDetails withIcon(@Nullable String value) {
        return new WarpDetails(warp, value, permission, price, cooldownSeconds, description, section, uses);
    }

    public WarpDetails withPermission(@Nullable String value) {
        return new WarpDetails(warp, icon, value, price, cooldownSeconds, description, section, uses);
    }

    public WarpDetails withPrice(double value) {
        return new WarpDetails(warp, icon, permission, value, cooldownSeconds, description, section, uses);
    }

    public WarpDetails withCooldown(int value) {
        return new WarpDetails(warp, icon, permission, price, value, description, section, uses);
    }

    public WarpDetails withDescription(@Nullable String value) {
        return new WarpDetails(warp, icon, permission, price, cooldownSeconds, value, section, uses);
    }

    public WarpDetails withSection(@Nullable String value) {
        return new WarpDetails(warp, icon, permission, price, cooldownSeconds, description, value, uses);
    }

    public WarpDetails used() {
        return new WarpDetails(warp, icon, permission, price, cooldownSeconds, description, section, uses + 1);
    }
}
