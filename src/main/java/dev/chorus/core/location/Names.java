package dev.chorus.core.location;

import java.util.Locale;
import java.util.regex.Pattern;

/** Naming rules shared by homes and warps. */
public final class Names {

    private static final Pattern ALLOWED = Pattern.compile("[a-z0-9_-]+");

    private Names() {
    }

    public static String normalise(String raw) {
        return raw.toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String raw, int maxLength) {
        String name = normalise(raw);
        return name.length() <= maxLength && ALLOWED.matcher(name).matches();
    }
}
