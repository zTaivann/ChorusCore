package dev.chorus.core.command;

import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachmentInfo;

/** A number carried in a permission, such as {@code chorus.home.limit.5}. */
public final class PermissionLimits {

    private PermissionLimits() {
    }

    /** The highest number held under the prefix, and never less than {@code floor}. */
    public static int highest(Permissible holder, String prefix, int floor) {
        int limit = floor;
        for (PermissionAttachmentInfo held : holder.getEffectivePermissions()) {
            String node = held.getPermission();
            if (held.getValue() && node.startsWith(prefix)) {
                limit = Math.max(limit, Numbers.integer(node.substring(prefix.length()), floor));
            }
        }
        return limit;
    }
}
