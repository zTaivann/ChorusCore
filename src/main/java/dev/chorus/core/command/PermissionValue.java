package dev.chorus.core.command;

import org.bukkit.permissions.Permissible;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A number for whoever holds a permission, written {@code 'group.vip: 25'}. */
public record PermissionValue(String permission, double value) {

    /** The entries of such a list, handing each line it cannot read to {@code unreadable}. */
    public static List<PermissionValue> read(List<?> written, Consumer<String> unreadable) {
        List<PermissionValue> values = new ArrayList<>();
        for (Object entry : lines(written)) {
            String line = String.valueOf(entry).trim();
            int colon = line.lastIndexOf(':');
            String permission = colon < 0 ? "" : line.substring(0, colon).trim();
            double value = colon < 0 ? Double.NaN : Numbers.decimal(
                    line.substring(colon + 1).replace("%", "").trim(), Double.NaN);
            if (permission.isEmpty() || Double.isNaN(value)) {
                unreadable.accept(line);
                continue;
            }
            values.add(new PermissionValue(permission, value));
        }
        return values;
    }

    /** The biggest value this holder has the permission for, or {@code none}. */
    public static double best(Permissible holder, List<PermissionValue> values, double none) {
        double best = Double.NEGATIVE_INFINITY;
        for (PermissionValue entry : values) {
            if (entry.value() > best && holder.hasPermission(entry.permission())) {
                best = entry.value();
            }
        }
        return best == Double.NEGATIVE_INFINITY ? none : best;
    }

    /** Unquoted, {@code - group.vip: 25} reaches the plugin as a map; it means the same. */
    private static List<Object> lines(List<?> written) {
        List<Object> lines = new ArrayList<>();
        for (Object entry : written) {
            if (entry instanceof Map<?, ?> map) {
                map.forEach((key, value) -> lines.add(key + ": " + value));
            } else {
                lines.add(entry);
            }
        }
        return lines;
    }
}
