package dev.chorus.core;

import dev.chorus.core.locale.TextFormat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps messages.yml, menus.yml and the code that sends from them in step.
 *
 * <p>The list of keys is scanned out of the source rather than written down here, so it can
 * never drift: a message the code sends is by definition one this test looks for.
 */
class MessagesTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Every file a line can come from. The key says which: menu. is a screen. */
    private static final List<String> FILES = List.of("messages.yml", "menus.yml");

    /** The top-level sections of both files, which every key begins with. */
    private static final String ROOTS = "error|core|cooldown|economy|chat|home|warp|spawn"
            + "|request|back|teleport|utility|staff|players|items|menu|kits|world|shops";

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

    /** A %name% in a template, which names a value the code passes in. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-z_]+)%");

    @Test
    void everyTemplateParses() {
        YamlConfiguration messages = allLines();
        List<String> broken = messages.getKeys(true).stream()
                .filter(messages::isString)
                .filter(key -> !parses(messages.getString(key, "")))
                .toList();

        assertEquals(List.of(), broken, "these templates are not valid MiniMessage");
    }

    @Test
    void everyKeyTheCodeSendsExists() throws IOException {
        YamlConfiguration messages = allLines();
        List<String> missing = keysUsedInSources().stream()
                .filter(key -> !messages.isString(key))
                .toList();

        assertEquals(List.of(), missing, "no file holds these keys the code sends");
    }

    @Test
    void noKeyIsLeftOver() throws IOException {
        YamlConfiguration messages = allLines();
        Set<String> used = keysUsedInSources();
        List<String> unused = messages.getKeys(true).stream()
                .filter(messages::isString)
                .filter(key -> !key.equals("prefix"))
                .filter(key -> !used.contains(key))
                .toList();

        assertEquals(List.of(), unused, "these lines exist but nothing sends them");
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
        YamlConfiguration messages = allLines();
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

    /**
     * The menus explain how to colour a line, so the examples have to survive being drawn.
     *
     * <p>An unescaped one turns into the colour it is describing and explains nothing, which
     * is a failure nobody notices until somebody opens the screen and finds a blank space
     * where the instruction was.
     */
    @Test
    void theHelpTextShowsTheCodesRatherThanUsingThem() {
        YamlConfiguration messages = allLines();
        String drawn = PLAIN.serialize(MiniMessage.miniMessage()
                .deserialize(TextFormat.toTags(
                        messages.getString("menu.editor.action-add-lore", ""))));

        assertTrue(drawn.contains("&a"), "the example colour code should still be readable");
        assertTrue(drawn.contains("<green>"), "the example tag should still be readable");
    }

    /**
     * A translation may be incomplete, but it may not invent keys.
     *
     * <p>A key with a typo in it would simply never be sent, and the English line would go
     * out instead with nobody the wiser, which is exactly the kind of thing nobody notices
     * until a player asks why one message is in the wrong language.
     */
    @Test
    void everyTranslatedKeyExists() {
        YamlConfiguration base = allLines();

        for (String file : translations()) {
            YamlConfiguration translated = Resources.read(file);
            List<String> unknown = translated.getKeys(true).stream()
                    .filter(translated::isString)
                    .filter(key -> !key.equals("prefix"))
                    .filter(key -> !base.isString(key))
                    .toList();

            assertEquals(List.of(), unknown, file + " holds keys nothing sends");
        }
    }

    @Test
    void everyTranslatedTemplateParses() {
        for (String file : translations()) {
            YamlConfiguration translated = Resources.read(file);
            List<String> broken = translated.getKeys(true).stream()
                    .filter(translated::isString)
                    .filter(key -> !parses(translated.getString(key, "")))
                    .toList();

            assertEquals(List.of(), broken, file + " holds invalid MiniMessage");
        }
    }

    /**
     * The values a line is given are named, so a translated line that renames one leaves a
     * gap where the number should be. Catching it here costs nothing; catching it in chat
     * costs somebody a bug report.
     */
    @Test
    void noTranslationRenamesAPlaceholder() {
        YamlConfiguration base = allLines();
        List<String> wrong = new ArrayList<>();

        for (String file : translations()) {
            YamlConfiguration translated = Resources.read(file);
            for (String key : translated.getKeys(true)) {
                if (!translated.isString(key) || !base.isString(key)) {
                    continue;
                }
                Set<String> extra = new TreeSet<>(slotsIn(translated.getString(key, "")));
                extra.removeAll(slotsIn(base.getString(key, "")));
                extra.forEach(slot -> wrong.add(file + " " + key + " uses %" + slot + "%"));
            }
        }
        assertEquals(List.of(), wrong, "these placeholders are in no English line");
    }

    /** Every other language that ships in the jar. */
    private static List<String> translations() {
        String[] found = Resources.FOLDER.toFile()
                .list((folder, name) -> name.startsWith("messages_") && name.endsWith(".yml"));
        return found == null ? List.of() : List.of(found);
    }

    private static Set<String> slotsIn(String template) {
        Set<String> slots = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            slots.add(matcher.group(1));
        }
        return slots;
    }

    /**
     * Both files as one, which is how the plugin reads them.
     *
     * <p>Which file a line lives in is a question for whoever is editing them, not for the
     * code: a key is a key. Reading them together is also what catches the same key being
     * written into both.
     */
    private static YamlConfiguration allLines() {
        YamlConfiguration everything = new YamlConfiguration();
        for (String file : FILES) {
            YamlConfiguration read = Resources.read(file);
            for (String key : read.getKeys(true)) {
                if (read.isString(key)) {
                    assertTrue(everything.getString(key) == null,
                            "'" + key + "' is in more than one file");
                    everything.set(key, read.getString(key));
                }
            }
        }
        return everything;
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

    /** Through the same two steps the plugin uses, old codes to tags and then to text. */
    private static boolean parses(String template) {
        try {
            MiniMessage.miniMessage().deserialize(TextFormat.toTags(template));
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }
}
