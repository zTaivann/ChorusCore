package dev.chorus.core.teleport;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/** Reading the three numbers somebody typed. */
public final class Coordinates {

    private static final char RELATIVE = '~';

    private Coordinates() {
    }

    /**
     * Three coordinates starting at {@code from}, or null when any of them is not a number.
     *
     * @param origin what {@code ~} is measured against, and the direction to keep
     */
    public static @Nullable Location read(String[] args, int from, World world, Location origin) {
        if (args.length < from + 3) {
            return null;
        }

        Double x = one(args[from], origin.getX());
        Double y = one(args[from + 1], origin.getY());
        Double z = one(args[from + 2], origin.getZ());
        if (x == null || y == null || z == null) {
            return null;
        }
        // Keep the direction they were already facing rather than snapping them north.
        return new Location(world, x, y, z, origin.getYaw(), origin.getPitch());
    }

    /** Whether these three would read as coordinates, for telling a name from a number. */
    public static boolean areNumbers(String[] args, int from) {
        return args.length >= from + 3
                && one(args[from], 0) != null
                && one(args[from + 1], 0) != null
                && one(args[from + 2], 0) != null;
    }

    private static @Nullable Double one(String raw, double relativeTo) {
        String text = raw.trim().replace(',', '.');
        if (text.isEmpty()) {
            return null;
        }
        if (text.charAt(0) != RELATIVE) {
            return number(text);
        }
        if (text.length() == 1) {
            return relativeTo;
        }
        Double offset = number(text.substring(1));
        return offset == null ? null : relativeTo + offset;
    }

    private static @Nullable Double number(String text) {
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
