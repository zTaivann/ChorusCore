package dev.chorus.core.shops.chest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * What a server allows its chest shops to do.
 *
 * @param enabled        false leaves a {@code [Shop]} sign as an ordinary sign
 * @param defaultLimit   shops per player without a limit permission
 * @param creationCost   what making one costs
 * @param protectHoppers stops a hopper draining a shop's container
 * @param maxPrice       the most one item may cost, to blunt a mistyped price
 * @param reach          how far from a shop a trade may still be finished, in blocks
 */
public record ChestShopSettings(boolean enabled, int defaultLimit, double creationCost,
                                boolean protectHoppers, double maxPrice, int reach) {

    private static final String LIMIT_PREFIX = "chorus.shops.chest.limit.";

    public static ChestShopSettings read(ConfigurationSection shops) {
        ConfigurationSection chest = shops.getConfigurationSection("chest");
        if (chest == null) {
            return new ChestShopSettings(true, 5, 0, true, 1_000_000, 8);
        }
        return new ChestShopSettings(
                chest.getBoolean("enabled", true),
                Math.max(0, chest.getInt("default-limit", 5)),
                Math.max(0, chest.getDouble("creation-cost", 0)),
                chest.getBoolean("protect-hoppers", true),
                Math.max(0.01, chest.getDouble("max-price", 1_000_000)),
                Math.max(2, Math.min(32, chest.getInt("reach", 8))));
    }

    /** How many shops this player may have. */
    public int limitFor(Player player) {
        int limit = defaultLimit;
        for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
            String node = permission.getPermission();
            if (!permission.getValue() || !node.startsWith(LIMIT_PREFIX)) {
                continue;
            }
            try {
                limit = Math.max(limit, Integer.parseInt(node.substring(LIMIT_PREFIX.length())));
            } catch (NumberFormatException notALimit) {
                // A wildcard carries no number and cannot raise the cap.
            }
        }
        return limit;
    }
}
