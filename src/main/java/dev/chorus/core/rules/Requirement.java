package dev.chorus.core.rules;

import dev.chorus.core.command.Numbers;
import dev.chorus.core.locale.Messages;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Something that has to be true first, for a kit or for a command. */
public record Requirement(Kind kind, String argument, @Nullable String deny) {

    public enum Kind {
        PERMISSION, PLACEHOLDER, MONEY, PLAYTIME, KIT
    }

    /** @param owner how a problem names what the list belongs to, such as {@code kit 'vip'} */
    public static List<Requirement> read(List<?> entries, String owner, Consumer<String> onProblem) {
        List<Requirement> requirements = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            Requirement requirement = entry instanceof Map<?, ?> block
                    ? of(text(block.get("condition")), text(block.get("deny")))
                    : of(String.valueOf(entry), null);
            if (requirement == null) {
                onProblem.accept(owner + " has a requirement this plugin does not know: '"
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

    /** The first one in the list this player does not meet, or null when they meet them all. */
    public static @Nullable Requirement firstUnmet(List<Requirement> requirements, Player player,
                                                   Context context) {
        for (Requirement requirement : requirements) {
            if (!requirement.met(player, context)) {
                return requirement;
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

    /** What the player is told when this one fails, when it was given a line of its own. */
    public @Nullable String denyMessage() {
        return deny;
    }

    /**
     * Tells the player they were turned away: this requirement's own line when it has one,
     * the message under {@code fallbackKey} when it does not.
     *
     * @param placeholders name and value pairs, filled into either
     */
    public void tell(Player player, Messages messages, String fallbackKey, String... placeholders) {
        if (deny == null) {
            messages.send(player, fallbackKey, placeholders);
            return;
        }
        String line = deny;
        for (int at = 0; at + 1 < placeholders.length; at += 2) {
            line = line.replace("%" + placeholders[at] + "%", placeholders[at + 1]);
        }
        player.sendMessage(messages.parse(Placeholders.fill(player, line)));
    }

    /** The numbers a requirement needs, gathered once rather than per requirement. */
    public interface Context {

        double balance();

        long playtimeSeconds();

        boolean hasClaimed(String kit);
    }

    /** A requirement nobody can meet is safer than one everybody passes by accident. */
    private static double number(String raw) {
        return Numbers.decimal(raw.replace(',', '.'), Double.MAX_VALUE);
    }

    private static @Nullable String text(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
