package dev.chorus.core.command;

import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A number per permission, as discounts and sell multipliers write it. */
class PermissionValueTest {

    private final List<String> unreadable = new ArrayList<>();

    @Test
    void eachLineGivesAPermissionAndANumber() {
        assertEquals(List.of(new PermissionValue("group.vip", 1.25),
                        new PermissionValue("group.mvp", 50)),
                PermissionValue.read(List.of("group.vip: 1.25", " group.mvp : 50% "), unreadable::add));
        assertEquals(List.of(), unreadable);
    }

    @Test
    void aLineWithoutBothIsHandedBack() {
        assertEquals(List.of(), PermissionValue.read(List.of("group.vip", ": 2", "group.vip: x"),
                unreadable::add));
        assertEquals(List.of("group.vip", ": 2", "group.vip: x"), unreadable);
    }

    @Test
    void theBiggestOneHeldCountsAndNoneGivesTheFallback() {
        List<PermissionValue> values = List.of(new PermissionValue("group.vip", 1.25),
                new PermissionValue("group.mvp", 1.5), new PermissionValue("group.slow", 0.5));

        assertEquals(1.5, PermissionValue.best(holding("group.vip", "group.mvp"), values, 1));
        assertEquals(0.5, PermissionValue.best(holding("group.slow"), values, 1));
        assertEquals(1, PermissionValue.best(holding(), values, 1));
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
