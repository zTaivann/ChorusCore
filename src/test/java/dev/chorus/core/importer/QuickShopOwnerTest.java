package dev.chorus.core.importer;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * QuickShop has written the owner column several different ways over the years, and an
 * import that reads it wrong hands somebody else's shop to the wrong player. Every shape it
 * is known to take is pinned here.
 */
class QuickShopOwnerTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void aBareIdIsRead() {
        assertEquals(NOTCH, ownerOf(NOTCH.toString()));
    }

    @Test
    void anIdWithSomethingInFrontIsRead() {
        assertEquals(NOTCH, ownerOf("UUID:" + NOTCH));
        assertEquals(NOTCH, ownerOf("uuid=" + NOTCH));
    }

    @Test
    void anIdInsideJsonIsRead() {
        assertEquals(NOTCH, ownerOf("{\"type\":\"REAL\",\"uuid\":\"" + NOTCH + "\"}"));
    }

    @Test
    void anIdWithoutItsDashesIsRead() {
        assertEquals(NOTCH, ownerOf(NOTCH.toString().replace("-", "")));
    }

    @Test
    void spacesAroundItDoNotMatter() {
        assertEquals(NOTCH, ownerOf("  " + NOTCH + "  "));
    }

    /** A name is not an id, and guessing one would be handing a shop to the wrong player. */
    @Test
    void aPlainNameIsRefused() {
        assertNull(ownerOf("Notch"));
        assertNull(ownerOf("username:Notch"));
    }

    @Test
    void nothingIsRefused() {
        assertNull(ownerOf(null));
        assertNull(ownerOf(""));
        assertNull(ownerOf("   "));
    }

    /** Package-private on the importer, which is where it belongs; reached for the checks. */
    private static UUID ownerOf(String raw) {
        try {
            Method method = QuickShopImport.class.getDeclaredMethod("ownerOf", String.class);
            method.setAccessible(true);
            return (UUID) method.invoke(null, raw);
        } catch (ReflectiveOperationException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
