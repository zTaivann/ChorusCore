package dev.chorus.core.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Where every key in a YAML file is, by line. */
final class ConfigLines {

    private static final Pattern KEY = Pattern.compile("^(\\s*)([A-Za-z0-9_.-]+):(.*)$");

    private final Map<String, Integer> lines;

    private ConfigLines(Map<String, Integer> lines) {
        this.lines = lines;
    }

    static ConfigLines of(List<String> text) {
        Map<String, Integer> found = new HashMap<>();
        String[] path = new String[64];
        int[] indents = new int[64];
        int depth = 0;

        for (int at = 0; at < text.size(); at++) {
            String line = text.get(at);
            if (line.isBlank() || line.stripLeading().startsWith("#")
                    || line.stripLeading().startsWith("-")) {
                continue;
            }
            Matcher matcher = KEY.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            int indent = matcher.group(1).length();
            while (depth > 0 && indents[depth - 1] >= indent) {
                depth--;
            }
            if (depth >= path.length) {
                continue;
            }
            path[depth] = matcher.group(2);
            indents[depth] = indent;
            depth++;

            found.putIfAbsent(String.join(".", java.util.Arrays.copyOf(path, depth)), at + 1);
        }
        return new ConfigLines(found);
    }

    /** The line the key is on, or 0. */
    int of(String key) {
        return lines.getOrDefault(key, 0);
    }
}
