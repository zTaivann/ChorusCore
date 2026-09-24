package dev.chorus.core.papi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Holds the list {@code /chorus placeholders} prints to the ones the code answers. */
class PlaceholderNamesTest {

    private static final Path SOURCE = Path.of("src", "main", "java", "dev", "chorus", "core",
            "papi", "PlaceholderValues.java");

    /** A placeholder answered by name, either in the switch or in the check above it. */
    private static final Pattern ANSWERED =
            Pattern.compile("case \"([a-z_]+)\"|id\\.equals\\(\"([a-z_]+)\"\\)");

    @Test
    void theListedNamesAreTheAnsweredOnes() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        Set<String> answered = new TreeSet<>();

        Matcher matcher = ANSWERED.matcher(source);
        while (matcher.find()) {
            answered.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }

        assertEquals(answered, new TreeSet<>(PlaceholderValues.NAMES),
                "the names listed and the names answered have drifted apart");
    }

    /** They are printed in the order they are written down, so that order is the sorted one. */
    @Test
    void theNamesAreInOrder() {
        assertEquals(new TreeSet<>(PlaceholderValues.NAMES).stream().toList(),
                PlaceholderValues.NAMES);
    }
}
