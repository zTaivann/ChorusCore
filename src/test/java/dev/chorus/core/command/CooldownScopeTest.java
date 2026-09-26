package dev.chorus.core.command;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who a cooldown holds back: the player, their world, or everyone. */
class CooldownScopeTest {

    private static final long NOW = 1_000_000;

    private final World overworld = world();
    private final World nether = world();
    private final Player steve = player(overworld);
    private final Player alex = player(overworld);
    private final Player notch = player(nether);

    private final Cooldowns cooldowns = new Cooldowns();

    @Test
    void theNamesAreReadWhateverTheirCase() {
        assertEquals(CooldownScope.PLAYER, CooldownScope.of("player"));
        assertEquals(CooldownScope.WORLD, CooldownScope.of(" World "));
        assertEquals(CooldownScope.SERVER, CooldownScope.of("SERVER"));
        assertNull(CooldownScope.of("everyone"));
    }

    @Test
    void aPlayerScopeHoldsBackOnlyThatPlayer() {
        start(CooldownScope.PLAYER, steve);

        assertTrue(left(CooldownScope.PLAYER, steve) > 0);
        assertEquals(0, left(CooldownScope.PLAYER, alex));
    }

    @Test
    void aWorldScopeHoldsBackEveryoneInThatWorldAndNobodyElse() {
        start(CooldownScope.WORLD, steve);

        assertTrue(left(CooldownScope.WORLD, alex) > 0, "alex is in the same world");
        assertEquals(0, left(CooldownScope.WORLD, notch), "notch is in another world");
    }

    @Test
    void aServerScopeHoldsBackEveryone() {
        start(CooldownScope.SERVER, notch);

        assertTrue(left(CooldownScope.SERVER, steve) > 0);
        assertTrue(left(CooldownScope.SERVER, alex) > 0);
    }

    /** The same command under two scopes is two timers, not one. */
    @Test
    void theScopesDoNotShareATimer() {
        assertNotEquals(CooldownScope.PLAYER.owner(steve), CooldownScope.WORLD.owner(steve));
        assertNotEquals(CooldownScope.WORLD.owner(steve), CooldownScope.SERVER.owner(steve));
    }

    @Test
    void eachScopeHasItsOwnLine() {
        assertEquals("cooldown.wait", CooldownScope.PLAYER.waitMessage());
        assertEquals("cooldown.wait-world", CooldownScope.WORLD.waitMessage());
        assertEquals("cooldown.wait-server", CooldownScope.SERVER.waitMessage());
    }

    private void start(CooldownScope scope, Player player) {
        cooldowns.start(scope.owner(player), "broadcast", 30, NOW);
    }

    private long left(CooldownScope scope, Player player) {
        return cooldowns.remaining(scope.owner(player), "broadcast", NOW);
    }

    private static World world() {
        UUID id = UUID.randomUUID();
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Player player(World world) {
        UUID id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getWorld" -> world;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
