package dev.chorus.core;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps messages.yml and the code that sends from it in step.
 *
 * <p>The list of keys is scanned out of the source rather than written down here, so it can
 * never drift: a message the code sends is by definition one this test looks for.
 */
class MessagesTest {

    /** The top-level sections of messages.yml, which every key begins with. */
    private static final String ROOTS = "error|core|cooldown|economy|chat|home|warp|spawn"
            + "|request|back|teleport|utility|staff|players|items|menu|kits";

    /**
     * Any literal shaped like a dotted key whose first segment is one of our roots.
     *
     * <p>Broad on purpose: a key reaches send() through a ternary, through a helper of
     * this plugin's own, or from the next line down, and a pattern anchored on the call
     * would miss most of them and call every one of those messages dead.
     *
     * <p>The one thing it has to rule out is a config path, which looks exactly the same:
     * config.section("kits.editor") is not a message and reporting it as a missing one
     * sends whoever reads the failure looking for a line that was never meant to exist.
     */
    private static final Pattern MESSAGE_KEY = Pattern.compile(
            "(?<!section[(])[\"]((?:" + ROOTS + ")(?:[.][a-z-]+)+)[\"]");

    @Test
    void everyTemplateParses() {
        YamlConfiguration messages = Resources.read("messages.yml");
        List<String> broken = messages.getKeys(true).stream()
                .filter(messages::isString)
                .filter(key -> !parses(messages.getString(key, "")))
                .toList();

        assertEquals(List.of(), broken, "these templates are not valid MiniMessage");
    }

    @Test
    void everyKeyTheCodeSendsExists() throws IOException {
        YamlConfiguration messages = Resources.read("messages.yml");
        List<String> missing = keysUsedInSources().stream()
                .filter(key -> !messages.isString(key))
                .toList();

        assertEquals(List.of(), missing, "messages.yml is missing keys the code sends");
    }

    @Test
    void noKeyIsLeftOver() throws IOException {
        YamlConfiguration messages = Resources.read("messages.yml");
        Set<String> used = keysUsedInSources();
        List<String> unused = messages.getKeys(true).stream()
                .filter(messages::isString)
                .filter(key -> !key.equals("prefix"))
                .filter(key -> !used.contains(key))
                .toList();

        assertEquals(List.of(), unused, "messages.yml has keys nothing sends");
    }

    /**
     * A newline inside one line of item lore is not a line break.
     *
     * <p>Lore is a list of lines, so the game draws the newline as the missing character it
     * is: a little box in the middle of the sentence. A template with one in it has to go
     * through renderLines, which splits it into the separate lines it was asking for.
     */
    @Test
    void noLoreLineIsSentAsOnePieceWhenItAsksForSeveral() throws IOException {
        YamlConfiguration messages = Resources.read("messages.yml");
        String sources = allSources();

        List<String> wrong = messages.getKeys(true).stream()
                .filter(messages::isString)
                .filter(MessagesTest::isLore)
                .filter(key -> messages.getString(key, "").contains("<newline>"))
                .filter(key -> sources.contains("render(\"" + key + "\""))
                .toList();

        assertEquals(List.of(), wrong,
                "these lore lines hold a newline but are rendered as one piece; use renderLines");
    }

    /** Lore keys are the ones whose last segment says so, either way round. */
    private static boolean isLore(String key) {
        String last = key.substring(key.lastIndexOf('.') + 1);
        return last.startsWith("lore") || last.endsWith("lore");
    }

    private static String allSources() throws IOException {
        StringBuilder everything = new StringBuilder();
        try (Stream<Path> files = Files.walk(Resources.SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                everything.append(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return everything.toString();
    }

    private static Set<String> keysUsedInSources() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Resources.SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = MESSAGE_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        assertTrue(keys.size() > 100, "the scanner found almost nothing, so it has stopped working");
        return keys;
    }

    private static boolean parses(String template) {
        try {
            MiniMessage.miniMessage().deserialize(template);
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }
}
