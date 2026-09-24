package dev.chorus.core.config;

import org.jetbrains.annotations.Nullable;

/**
 * One thing wrong with a config file.
 *
 * @param file    the path inside the plugin folder.
 * @param line    the line it is on, or 0 when it could not be found.
 * @param message what is wrong, in one sentence.
 * @param hint    what to write instead, or null.
 */
public record ConfigProblem(String file, int line, String message, @Nullable String hint) {

    public ConfigProblem(String file, int line, String message) {
        this(file, line, message, null);
    }

    /** {@code modules/homes.yml:142  'cooldown-second' is not an option. Did you mean ...} */
    public String describe() {
        StringBuilder text = new StringBuilder(file);
        if (line > 0) {
            text.append(':').append(line);
        }
        text.append("  ").append(message);
        if (hint != null) {
            text.append(" Did you mean '").append(hint).append("'?");
        }
        return text.toString();
    }
}
