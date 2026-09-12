package dev.chorus.core.world.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /time [when] [world|all]}: reads or sets the time of day.
 *
 * <p>Setting it always moves forwards to the next time it will be, so asking for morning in
 * the afternoon does not wind the day back and reset everything that counts from it.
 */
public final class TimeCommand extends ChorusCommand {

    private static final long DAY = 24000L;

    private static final Map<String, Long> NAMED = Map.of(
            "day", 1000L, "noon", 6000L, "dusk", 12610L, "night", 13000L,
            "midnight", 18000L, "dawn", 23000L, "sunrise", 23000L, "sunset", 12610L);

    public TimeCommand(CommandSupport support) {
        super(support, "time", "chorus.world.time");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            show(sender);
            return;
        }

        long time = parse(args[0]);
        if (time < 0) {
            messages.send(sender, "world.time-usage");
            return;
        }

        List<World> worlds = worlds(sender, args.length > 1 ? args[1] : null);
        if (worlds.isEmpty()) {
            messages.send(sender, "world.not-found", "world", args.length > 1 ? args[1] : "");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        worlds.forEach(world -> world.setFullTime(next(world.getFullTime(), time)));
        settle(sender);
        messages.send(sender, "world.time-set",
                "time", args[0].toLowerCase(Locale.ROOT),
                "world", worlds.size() == 1 ? worlds.get(0).getName()
                        : messages.plain("world.every-world"));
    }

    private void show(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "world.time-usage");
            return;
        }
        long time = player.getWorld().getTime();
        messages.send(sender, "world.time-now",
                "world", player.getWorld().getName(),
                "ticks", String.valueOf(time),
                "clock", clock(time));
    }

    /** The same time of day on the next day if it has already gone past today. */
    private static long next(long current, long wanted) {
        long day = current - Math.floorMod(current, DAY);
        long target = day + wanted;
        return target <= current ? target + DAY : target;
    }

    /** Minecraft counts from six in the morning, which is why nothing here lines up. */
    private static String clock(long ticks) {
        long total = Math.floorMod(ticks + 6000L, DAY);
        long hours = total / 1000;
        long minutes = (total % 1000) * 60 / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    /** Negative for anything that is neither a name nor a number of ticks. */
    private static long parse(String raw) {
        Long named = NAMED.get(raw.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        try {
            long ticks = Long.parseLong(raw.toLowerCase(Locale.ROOT).replace("ticks", ""));
            return ticks < 0 ? -1 : Math.floorMod(ticks, DAY);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private static List<World> worlds(CommandSender sender, String name) {
        if (name == null) {
            return sender instanceof Player player
                    ? List.of(player.getWorld())
                    : List.copyOf(sender.getServer().getWorlds());
        }
        if (name.equalsIgnoreCase("all") || name.equals("*")) {
            return List.copyOf(sender.getServer().getWorlds());
        }
        World world = sender.getServer().getWorld(name);
        return world == null ? List.of() : List.of(world);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], NAMED.keySet());
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            names.add("all");
            sender.getServer().getWorlds().forEach(world -> names.add(world.getName()));
            return startingWith(args[1], names);
        }
        return List.of();
    }
}
