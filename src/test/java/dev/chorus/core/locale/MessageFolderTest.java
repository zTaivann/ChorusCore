package dev.chorus.core.locale;

import dev.chorus.core.config.ConfigFiles;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads the messages the way a server does, into an empty folder. */
class MessageFolderTest {

    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    /** Where the files that would be inside the jar are read from. */
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @TempDir
    File dataFolder;

    @Test
    void everyShippedFileIsOneTheJarCarries() {
        for (String file : Messages.FILES) {
            assertTrue(Files.exists(RESOURCES.resolve("messages").resolve(file)),
                    "messages/" + file + " is written out on first start but is not in the jar");
        }
        for (String code : Messages.SHIPPED_LANGUAGES) {
            assertTrue(Files.isDirectory(RESOURCES.resolve("messages").resolve(code)),
                    "messages/" + code + " is written out on first start but is not in the jar");
        }
    }

    /** A file added to the folder and not to the list is never written out on a fresh install. */
    @Test
    void everyFileTheJarCarriesIsOneItWritesOut() throws IOException {
        try (var listing = Files.list(RESOURCES.resolve("messages"))) {
            List<String> onDisk = listing
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();

            assertEquals(onDisk, new TreeSet<>(Messages.FILES).stream().toList(),
                    "the messages folder and the list written out on first start disagree");
        }
    }

    @Test
    void anEmptyFolderIsFilledAndRead() {
        Messages messages = load();

        assertEquals("You are not allowed to do that.",
                withoutPrefix(messages, "error.no-permission"));
        assertTrue(new File(dataFolder, "messages/core.yml").isFile(),
                "the folder should have been written out");
        assertTrue(new File(dataFolder, "palette.yml").isFile(),
                "the palette should have been written out");
    }

    /** The screens are part of the same set of keys, in a file of their own. */
    @Test
    void theScreensAreReadTogetherWithTheMessages() {
        Messages messages = load();

        assertFalse(PLAIN.serialize(messages.render("menu.homes.title")).isBlank());
        assertFalse(PLAIN.serialize(messages.render("menu.buttons.close")).isBlank());
    }

    /** A colour left as a tag means the palette was never applied to that line. */
    @Test
    void noLineArrivesWithAColourNameStillInIt() {
        Messages messages = load();

        for (String key : List.of("menu.homes.title", "menu.buttons.previous",
                "core.status-header", "menu.kits.lore-action")) {
            assertFalse(PLAIN.serialize(messages.render(key)).contains("<c:"),
                    key + " still holds a colour name, so the palette was not applied");
        }
    }

    @Test
    void theLanguagesInTheFolderAreFound() {
        Messages messages = load();

        assertEquals(Messages.SHIPPED_LANGUAGES, messages.languages());
    }

    /** The wording is not pinned here: what is being checked is that the folder won. */
    @Test
    void aLanguageThatWasAskedForIsWhatEverybodyReads() {
        Messages messages = load();
        String english = withoutPrefix(messages, "error.no-permission");

        MemoryConfiguration language = new MemoryConfiguration();
        language.set("default", "es");
        messages.apply(language);
        String spanish = withoutPrefix(messages, "error.no-permission");

        assertFalse(spanish.isBlank(), "the Spanish line came back empty");
        assertNotEquals(english, spanish, "the Spanish folder was not the one read");
    }

    /** A line the command was given wins over the one in the folder, in every language. */
    @Test
    void aCommandSaysItsOwnLineInstead() {
        Messages messages = load();
        Messages command = messages.forCommand();
        command.override(Map.of("error.no-permission", "<red>Buy VIP."));

        assertEquals("Buy VIP.", PLAIN.serialize(command.render("error.no-permission")));
        assertEquals("You are not allowed to do that.",
                withoutPrefix(messages, "error.no-permission"));
    }

    @Test
    void aBlankLineFromACommandSaysNothing() {
        Messages command = load().forCommand();
        command.override(Map.of("cooldown.wait", ""));

        assertEquals("", PLAIN.serialize(command.render("cooldown.wait")));
    }

    private Messages load() {
        Plugin plugin = stub();
        return Messages.load(plugin, new ConfigFiles(plugin));
    }

    /** The prefix is on the front of nearly every line and is not what is being checked. */
    private String withoutPrefix(Messages messages, String key) {
        String line = PLAIN.serialize(messages.render(key));
        int separator = line.indexOf("| ");
        return separator < 0 ? line : line.substring(separator + 2);
    }

    /** A plugin that is only a folder, the files the jar would hold, and somewhere to complain. */
    private Plugin stub() {
        Logger logger = Logger.getLogger("MessageFolderTest");
        logger.setLevel(Level.OFF);

        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getDataFolder" -> dataFolder;
            case "getLogger" -> logger;
            case "getResource" -> resource((String) args[0]);
            case "saveResource" -> {
                write((String) args[0]);
                yield null;
            }
            case "toString" -> "plugin stub";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(
                    "the stub was asked for " + method.getName());
        };
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, handler);
    }

    private static InputStream resource(String name) throws IOException {
        Path file = RESOURCES.resolve(name);
        return Files.isRegularFile(file) ? Files.newInputStream(file) : null;
    }

    private void write(String name) throws IOException {
        Path file = RESOURCES.resolve(name);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("nothing in the jar is called " + name);
        }
        Path target = dataFolder.toPath().resolve(name);
        Files.createDirectories(target.getParent());
        Files.copy(file, target);
    }
}
