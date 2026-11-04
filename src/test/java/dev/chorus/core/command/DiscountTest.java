package dev.chorus.core.command;

import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Percent off a command's price for some permissions. */
class DiscountTest {

    private final List<String> problems = new ArrayList<>();

    @Test
    void aDiscountIsReadWithOrWithoutAPercentSign() {
        List<Discount> read = read(List.of("group.vip: 25", "group.mvp:50%"));

        assertEquals(List.of(new Discount("group.vip", 25), new Discount("group.mvp", 50)), read);
        assertTrue(problems.isEmpty());
    }

    /** Without quotes YAML hands over a map, which means the same thing. */
    @Test
    void anUnquotedLineReadsTheSame() {
        assertEquals(List.of(new Discount("group.vip", 25)), read(List.of(Map.of("group.vip", 25))));
    }

    @Test
    void aLineItCannotReadIsLeftOutAndReported() {
        List<Discount> read = read(List.of("group.vip", "group.vip: lots", "group.vip: 0",
                "group.vip: 150", ": 20"));

        assertEquals(List.of(), read);
        assertEquals(5, problems.size());
    }

    @Test
    void theBiggestDiscountThePlayerHoldsCounts() {
        List<Discount> discounts = read(List.of("group.vip: 25", "group.mvp: 50", "group.staff: 90"));

        assertEquals(50, Discount.best(holding("group.vip", "group.mvp"), discounts));
        assertEquals(0, Discount.best(holding(), discounts));
    }

    @Test
    void thePriceComesDownByThatPercent() {
        List<Discount> discounts = read(List.of("group.vip: 25"));

        assertEquals(75, Discount.apply(100, holding("group.vip"), discounts));
        assertEquals(100, Discount.apply(100, holding(), discounts));
        assertEquals(0, Discount.apply(100, holding("group.vip"), read(List.of("group.vip: 100"))));
    }

    private List<Discount> read(List<?> lines) {
        return Discount.read(lines, "/home", problems::add);
    }

    private static Permissible holding(String... permissions) {
        Set<String> held = Set.of(permissions);
        return (Permissible) Proxy.newProxyInstance(Permissible.class.getClassLoader(),
                new Class<?>[]{Permissible.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission") && args[0] instanceof String node) {
                        return held.contains(node);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
