package dev.chorus.core.world.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /weather <clear|rain|storm> [minutes] [world]}. */
public final class WeatherCommand extends ChorusCommand {

    private static final List<String> KINDS = List.of("clear", "rain", "storm");
    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final int DEFAULT_MINUTES = 5;
    private static final int MAX_MINUTES = 24 * 60;

    public WeatherCommand(CommandSupport support) {
        super(support, "weather", "chorus.world.weather");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            show(sender);
            return;
        }

        String kind = args[0].toLowerCase(Locale.ROOT);
        if (!KINDS.contains(kind)) {
            messages.send(sender, "world.weather-usage");
            return;
        }

        int minutes = args.length > 1 ? minutes(args[1]) : DEFAULT_MINUTES;
        if (minutes <= 0) {
            messages.send(sender, "world.weather-usage");
            return;
        }

        World world = world(sender, args.length > 2 ? args[2] : null);
        if (world == null) {
            messages.send(sender, "world.not-found", "world", args.length > 2 ? args[2] : "");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        int ticks = minutes * TICKS_PER_MINUTE;
        world.setStorm(!kind.equals("clear"));
        world.setThundering(kind.equals("storm"));
        world.setWeatherDuration(ticks);
        world.setThunderDuration(ticks);

        settle(sender);
        messages.send(sender, "world.weather-set",
                "weather", kind, "world", world.getName(), "minutes", String.valueOf(minutes));
    }

    private void show(CommandSender sender) {
        World world = world(sender, null);
        if (world == null) {
            messages.send(sender, "world.weather-usage");
            return;
        }
        String kind = !world.hasStorm() ? "clear" : world.isThundering() ? "storm" : "rain";
        messages.send(sender, "world.weather-now", "weather", kind, "world", world.getName());
    }

    private static World world(CommandSender sender, String name) {
        if (name != null) {
            return sender.getServer().getWorld(name);
        }
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        List<World> worlds = sender.getServer().getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    private static int minutes(String raw) {
        int value = Numbers.integer(raw, -1);
        return value < 0 ? -1 : Math.min(MAX_MINUTES, value);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], KINDS);
        }
        if (args.length == 3) {
            List<String> names = new ArrayList<>();
            sender.getServer().getWorlds().forEach(world -> names.add(world.getName()));
            return startingWith(args[2], names);
        }
        return List.of();
    }
}
