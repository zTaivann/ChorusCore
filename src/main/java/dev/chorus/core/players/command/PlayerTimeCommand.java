package dev.chorus.core.players.command;

import dev.chorus.core.command.CommandSupport;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Changes the sky one player sees and nothing else: the world keeps its own time, and so does
 * everybody standing next to them.
 */
public final class PlayerTimeCommand extends PersonalViewCommand {

    private static final String RESET = "reset";

    private static final Map<String, Long> NAMED = Map.of(
            "day", 1000L,
            "noon", 6000L,
            "sunset", 12000L,
            "night", 14000L,
            "midnight", 18000L,
            "sunrise", 23000L);

    public PlayerTimeCommand(CommandSupport support) {
        super(support, "ptime", "chorus.players.ptime");
    }

    @Override
    protected String usageKey() {
        return "players.ptime-usage";
    }

    @Override
    protected List<String> values() {
        List<String> names = new ArrayList<>(NAMED.keySet());
        names.add(RESET);
        return names;
    }

    @Override
    protected void apply(CommandSender sender, Player target, String value) {
        if (value.equals(RESET)) {
            if (!ready(sender)) {
                return;
            }
            target.resetPlayerTime();
            settle(sender);
            report(sender, target, "players.ptime-reset", "players.ptime-reset-other", RESET);
            return;
        }

        Long ticks = NAMED.get(value);
        if (ticks == null) {
            ticks = parseTicks(value);
        }
        if (ticks == null) {
            messages.send(sender, "players.ptime-unknown", "value", value);
            return;
        }
        if (!ready(sender)) {
            return;
        }

        // Not relative: the sky the player sees stops where it was put rather than carrying
        // on from there, which is the whole point of asking for a time.
        target.setPlayerTime(ticks, false);
        settle(sender);
        report(sender, target, "players.ptime-set", "players.ptime-set-other", value);
    }

    private static Long parseTicks(String value) {
        try {
            long ticks = Long.parseLong(value);
            return ticks >= 0 && ticks < 24000 ? ticks : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
