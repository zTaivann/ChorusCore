package dev.chorus.core.command;

import org.bukkit.permissions.Permissible;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Percent off a price for whoever holds a permission, written {@code 'group.vip: 25'}.
 *
 * @param percent above 0 and at most 100
 */
public record Discount(String permission, double percent) {

    /** The entries of a {@code discounts} list, leaving out and reporting any it cannot read. */
    public static List<Discount> read(List<?> written, String owner, Consumer<String> onProblem) {
        List<Discount> discounts = new ArrayList<>();
        List<PermissionValue> entries = PermissionValue.read(written, line -> onProblem.accept(
                owner + " has a discount it cannot read: '" + line
                        + "'. Write it as 'permission: percent', like 'group.vip: 25'."));
        for (PermissionValue entry : entries) {
            if (entry.value() <= 0 || entry.value() > 100) {
                onProblem.accept(owner + " gives " + entry.permission() + " a discount of "
                        + entry.value() + "%. A discount is above 0 and at most 100.");
                continue;
            }
            discounts.add(new Discount(entry.permission(), entry.value()));
        }
        return List.copyOf(discounts);
    }

    /** The biggest percent off this holder has, or 0. */
    public static double best(Permissible holder, List<Discount> discounts) {
        double best = 0;
        for (Discount discount : discounts) {
            if (discount.percent() > best && holder.hasPermission(discount.permission())) {
                best = discount.percent();
            }
        }
        return best;
    }

    /** The price once the holder's biggest discount comes off. */
    public static double apply(double price, Permissible holder, List<Discount> discounts) {
        double off = best(holder, discounts);
        return off <= 0 ? price : price * (100 - off) / 100;
    }
}
