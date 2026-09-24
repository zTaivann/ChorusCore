package dev.chorus.core.config;

import dev.chorus.core.locale.Messages;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a reload says it did. */
class ConfigSnapshotTest {

    private static final String FILE = "modules/test.yml";

    @TempDir
    File dataFolder;

    private ConfigFiles configs;

    @BeforeEach
    void open() {
        configs = new ConfigFiles(plugin());
    }

    @Test
    void aReloadThatChangedNothingSaysSo() throws IOException {
        write("a: 1\nb: 2\n");
        ConfigSnapshot before = reloaded();

        assertTrue(reloaded().since(before).isEmpty());
    }

    /** The file is edited first and the command typed after, as on a real server. */
    @Test
    void anEditMadeBeforeTheCommandIsSeen() throws IOException {
        write("a: 1\n");
        reloaded();
        write("a: 5\n");

        ConfigSnapshot before = loaded();
        assertEquals(List.of(FILE + " a"), reloaded().since(before).changed());
    }

    @Test
    void aChangedValueIsNamed() throws IOException {
        write("a: 1\nb: 2\n");
        ConfigSnapshot before = reloaded();
        write("a: 5\nb: 2\n");

        ConfigSnapshot.Change change = reloaded().since(before);
        assertEquals(List.of(FILE + " a"), change.changed());
        assertEquals(1, change.total());
    }

    @Test
    void anOptionAddedAndOneTakenAwayAreCountedApart() throws IOException {
        write("a: 1\nb: 2\n");
        ConfigSnapshot before = reloaded();
        write("a: 1\nc: 3\n");

        ConfigSnapshot.Change change = reloaded().since(before);
        assertEquals(List.of(FILE + " c"), change.added());
        assertEquals(List.of(FILE + " b"), change.removed());
    }

    /** A block is not a value, so growing one is only the values inside it. */
    @Test
    void aBlockIsNotCountedAsAChange() throws IOException {
        write("a: 1\n");
        ConfigSnapshot before = reloaded();
        write("a: 1\nblock:\n  inside: 2\n");

        assertEquals(List.of(FILE + " block.inside"), reloaded().since(before).added());
    }

    @Test
    void onlyTheFirstFewAreListed() throws IOException {
        write("a: 1\nb: 1\nc: 1\nd: 1\ne: 1\nf: 1\n");
        ConfigSnapshot before = reloaded();
        write("a: 2\nb: 2\nc: 2\nd: 2\ne: 2\nf: 2\n");

        ConfigSnapshot.Change change = reloaded().since(before);
        assertEquals(6, change.total());
        assertEquals(3, change.first(3).size());
    }

    /** What {@code /chorus reload <module>} does: its own file and the messages, nothing else. */
    @Test
    void onlyTheFilesAskedForAreReadAgain() throws IOException {
        write("a: 1\n");
        ConfigSnapshot before = reloaded();
        write("a: 2\n");

        configs.reload(path -> false);
        assertTrue(loaded().since(before).isEmpty());
        configs.reload(FILE::equals);
        assertEquals(List.of(FILE + " a"), loaded().since(before).changed());
    }

    @Test
    void theMessageFilesAndThePaletteAreTheMessages() {
        assertTrue(Messages.owns("messages/homes.yml"));
        assertTrue(Messages.owns("palette.yml"));
        assertFalse(Messages.owns("modules/homes.yml"));
    }

    private ConfigSnapshot loaded() {
        return ConfigSnapshot.of(configs);
    }

    private ConfigSnapshot reloaded() {
        configs.get(FILE).reload();
        return loaded();
    }

    private void write(String text) throws IOException {
        File file = new File(dataFolder, FILE);
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    private Plugin plugin() {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getDataFolder" -> dataFolder;
            case "getResource" -> null;
            case "toString" -> "plugin stub";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, handler);
    }
}
