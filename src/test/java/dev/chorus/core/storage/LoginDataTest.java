package dev.chorus.core.storage;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whatever a login loads is let go again, however the connection ends. */
class LoginDataTest {

    private static final String NAME = "Someone";

    private final Set<UUID> held = ConcurrentHashMap.newKeySet();
    private LoginData data;

    @BeforeEach
    void open() {
        data = new LoginData(null, Logger.getAnonymousLogger());
        data.add("test data", new LoginData.Part() {
            @Override
            public void load(UUID player) {
                held.add(player);
            }

            @Override
            public void unload(UUID player) {
                held.remove(player);
            }
        }, true);
    }

    @Test
    void itGoesWhenTheConnectionCloses() {
        UUID id = UUID.randomUUID();
        login(id);
        assertTrue(held.contains(id));

        close(id);
        assertFalse(held.contains(id));
    }

    /** A ban or a full server said no after this plugin had already loaded. */
    @Test
    void aLoginRefusedAfterLoadingLetsGo() {
        UUID id = UUID.randomUUID();
        AsyncPlayerPreLoginEvent event = preLogin(id);
        data.onPreLogin(event);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Component.text("banned"));
        data.onDecided(event);

        assertFalse(held.contains(id));
    }

    @Test
    void aLoginAlreadyRefusedLoadsNothing() {
        UUID id = UUID.randomUUID();
        AsyncPlayerPreLoginEvent event = preLogin(id);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, Component.text("no"));
        data.onPreLogin(event);

        assertFalse(held.contains(id));
    }

    /** The same account logging in over itself: the old session closing keeps the new one's. */
    @Test
    void aSecondLoginKeepsItsDataWhenTheFirstCloses() {
        UUID id = UUID.randomUUID();
        login(id);
        login(id);

        close(id);
        assertTrue(held.contains(id));
        close(id);
        assertFalse(held.contains(id));
    }

    private void login(UUID id) {
        AsyncPlayerPreLoginEvent event = preLogin(id);
        data.onPreLogin(event);
        data.onDecided(event);
    }

    private void close(UUID id) {
        data.onClose(new PlayerConnectionCloseEvent(id, NAME, InetAddress.getLoopbackAddress(), false));
    }

    private static AsyncPlayerPreLoginEvent preLogin(UUID id) {
        PlayerProfile profile = (PlayerProfile) Proxy.newProxyInstance(
                PlayerProfile.class.getClassLoader(), new Class<?>[]{PlayerProfile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> NAME;
                    default -> null;
                });
        InetAddress local = InetAddress.getLoopbackAddress();
        return new AsyncPlayerPreLoginEvent(NAME, local, local, id, profile, "localhost");
    }
}
