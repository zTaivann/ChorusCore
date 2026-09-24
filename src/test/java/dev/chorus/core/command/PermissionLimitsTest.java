package dev.chorus.core.command;

import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PermissionLimitsTest {

    private static final String PREFIX = "chorus.home.limit.";

    @Test
    void theHighestNumberHeldWins() {
        Permissible holder = holding(Map.of(PREFIX + "3", true, PREFIX + "10", true, PREFIX + "5", true));
        assertEquals(10, PermissionLimits.highest(holder, PREFIX, 1));
    }

    @Test
    void neverBelowTheFloor() {
        assertEquals(3, PermissionLimits.highest(holding(Map.of(PREFIX + "1", true)), PREFIX, 3));
    }

    @Test
    void aWildcardOrADeniedNodeCountsForNothing() {
        Permissible holder = holding(Map.of(PREFIX + "*", true, PREFIX + "50", false,
                "chorus.other.limit.99", true));
        assertEquals(2, PermissionLimits.highest(holder, PREFIX, 2));
    }

    private static Permissible holding(Map<String, Boolean> nodes) {
        return (Permissible) Proxy.newProxyInstance(Permissible.class.getClassLoader(),
                new Class<?>[]{Permissible.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getEffectivePermissions")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    Set<PermissionAttachmentInfo> held = new HashSet<>();
                    nodes.forEach((node, value) -> held.add(
                            new PermissionAttachmentInfo((Permissible) proxy, node, null, value)));
                    return held;
                });
    }
}
