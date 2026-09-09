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

    /** Any literal shaped like a dotted key whose first segment is one of our roots. */
    private static final Pattern MESSAGE_KEY = Pattern.compile(
            "\"((?:error|core|cooldown|economy|chat|home|warp|spawn|request|back|teleport|utility"
                    + "|staff|players|items|menu|kits)(?:\\.[a-z-]+)+)\"");

    @Test
    void everyTemplateParses() {
        YamlConfiguration messages = Resources.read("messages.yml");
        String prefix = messages.getString("prefix", "");

        for (String key : messages.getKeys(true)) {
            String template = messages.getString(key);
            if (template == null || template.isBlank()) {
                continue;
            }
            assertTrue(parses(template.replace("%prefix%", prefix)),
                    "MiniMessage cannot read '" + key + "'");
        }
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
