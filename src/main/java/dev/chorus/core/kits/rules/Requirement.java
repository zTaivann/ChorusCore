package dev.chorus.core.kits.rules;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Something that has to be true before a kit may be claimed. */
public record Requirement(Kind kind, String argument, @Nullable String deny) {

    public enum Kind {
        PERMISSION, PLACEHOLDER, MONEY, PLAYTIME, KIT
    }

    public static List<Requirement> read(List<?> entries, String kit, Consumer<String> onProblem) {
        List<Requirement> requirements = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            Requirement requirement = entry instanceof Map<?, ?> block
                    ? of(text(block.get("condition")), text(block.get("deny")))
                    : of(String.valueOf(entry), null);
            if (requirement == null) {
                onProblem.accept("kit '" + kit + "' has a requirement this plugin does not know: '"
                        + entry + "'");
                continue;
            }
            requirements.add(requirement);
        }
        return List.copyOf(requirements);
    }

    /** One line on its own, for a screen that has to say whether it is a line at all. */
    public static @Nullable Requirement of(@Nullable String line, @Nullable String deny) {
        if (line == null || line.isBlank()) {
            return null;
        }
        int colon = line.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String name = line.substring(0, colon).trim().toUpperCase(Locale.ROOT);
        String argument = line.substring(colon + 1).trim();

        for (Kind kind : Kind.values()) {
            if (kind.name().equals(name)) {
                return new Requirement(kind, argument, deny);
            }
        }
        return null;
    }

    /** Whether this player passes. */
    public boolean met(Player player, Context context) {
        return switch (kind) {
            case PERMISSION -> player.hasPermission(argument);
            case PLACEHOLDER -> Comparisons.holds(Placeholders.fill(player, argument));
            case MONEY -> context.balance() >= number(argument);
            case PLAYTIME -> context.playtimeSeconds() >= number(argument);
            case KIT -> context.hasClaimed(argument.toLowerCase(Locale.ROOT));
        };
    }

    /** What the player is told when this one fails, when the kit gave it a line of its own. */
    public @Nullable String denyMessage() {
        return deny;
    }

    /** The numbers a requirement needs, gathered once rather than per requirement. */
    public interface Context {

        double balance();

        long playtimeSeconds();

        boolean hasClaimed(String kit);
    }

    private static double number(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException notANumber) {
            // A requirement nobody can meet is safer than one everybody passes by accident.
            return Double.MAX_VALUE;
        }
    }

    private static @Nullable String text(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
