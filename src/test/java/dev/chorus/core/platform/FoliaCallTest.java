package dev.chorus.core.platform;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The one thing no compiler can check for us.
 *
 * <p>The Folia bridge reaches its schedulers by reflection, because the classes are not in
 * the 1.18.2 API the jar is built against. That means a wrong method name or one argument too
 * many is invisible until a server starts — and on Folia the failure does not even land near
 * the mistake, since there is no working scheduler to fall back to.
 *
 * <p>So every signature is resolved here against the newest Paper API, which ships those
 * classes. A signature that no longer exists fails the build.
 */
class FoliaCallTest {

    /** Handed over by the build, since this API cannot go on the test classpath. */
    private static final String PROPERTY = "chorus.modern.api";

    @Test
    void everySignatureExistsOnTheNewestApi() throws Exception {
        try (URLClassLoader api = newestApi()) {
            List<String> missing = new ArrayList<>();
            for (FoliaCall call : FoliaCall.values()) {
                try {
                    call.on(api);
                } catch (ReflectiveOperationException gone) {
                    missing.add(call.toString());
                }
            }

            assertEquals(List.of(), missing,
                    "these are not methods of the Folia API any more");
        }
    }

    @Test
    void theListCoversEveryOneTheBridgeUses() {
        // Ten scheduler methods and the two that hand them over. A bridge that grows a
        // lookup without growing this list is a bridge with a hole in it.
        assertEquals(12, FoliaCall.values().length);
    }

    @Test
    void theMarkerClassIsOnlyOnFolia() throws Exception {
        try (URLClassLoader api = newestApi()) {
            // Paper ships the scheduler classes so plugins can be written for Folia; it does
            // not ship this one, which is exactly why it is what the detection looks for.
            assertTrue(missing(api, FoliaCall.Api.FOLIA_MARKER),
                    "Paper now has the marker class, so Folia is no longer told apart by it");
            assertTrue(!missing(api, FoliaCall.Api.GLOBAL),
                    "the schedulers should be in the Paper API");
        }
    }

    private static boolean missing(ClassLoader api, String name) {
        try {
            Class.forName(name, false, api);
            return false;
        } catch (ClassNotFoundException absent) {
            return true;
        }
    }

    private static URLClassLoader newestApi() throws MalformedURLException {
        String classpath = System.getProperty(PROPERTY, "");
        assumeFalse(classpath.isBlank(), "the build did not hand over the newest Paper API");

        List<URL> jars = new ArrayList<>();
        for (String entry : classpath.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                jars.add(new File(entry).toURI().toURL());
            }
        }
        return new URLClassLoader(jars.toArray(URL[]::new), null);
    }
}
