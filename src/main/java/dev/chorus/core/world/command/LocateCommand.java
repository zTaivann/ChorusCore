package dev.chorus.core.world.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.StructureType;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * {@code /locate <structure|biome> <name>}: the nearest one, and how far away it is.
 *
 * <p>The names come from the server rather than a list written here, so a version that adds
 * one offers it. Naming the kind is optional: a bare name is looked for as a structure and
 * then as a biome, which is what most people type.
 *
 * <p>Searching costs real work, and asking for an unexplored structure costs a great deal
 * more, so both the radius and whether unexplored ones count are settings rather than
 * something the player chooses.
 *
 * <p>Biomes are reached through the registry rather than through {@code Biome.values()}.
 * Biome was an enum when this was built against 1.18.2 and is an interface on current
 * versions, so a call compiled against the enum would not link on a modern server.
 */
public final class LocateCommand extends PlayerCommand {

    private static final String STRUCTURE = "structure";
    private static final String BIOME = "biome";
    private static final String TELEPORT_PERMISSION = "chorus.staff.tppos";

    /**
     * How many samples across a biome search is allowed to take.
     *
     * <p>A biome search is noise sampled on the server thread, and there is no background
     * version of it in the API. Left to itself it scales with the square of the radius: a
     * 1600 block search took forty-five seconds here and the watchdog stopped the server.
     *
     * <p>So the interval between samples is worked out from the radius rather than fixed,
     * which makes every search cost the same however far it reaches. A wider search steps
     * over more ground, so a small patch of a biome can be stepped past — that is the trade,
     * and it is the right way round.
     */
    private static final int SAMPLES_ACROSS = 16;

    /** Sampling closer together than this buys nothing: biomes are laid out in fours. */
    private static final int MIN_STEP = 16;

    /** Past this the steps are so coarse that the answer stops meaning anything. */
    private static final int MAX_BIOME_RADIUS = 3200;

    private final IntSupplier radius;
    private final IntSupplier biomeRadius;
    private final BooleanSupplier unexplored;

    public LocateCommand(CommandSupport support, IntSupplier radius, IntSupplier biomeRadius,
                         BooleanSupplier unexplored) {
        super(support, "locate", "chorus.world.locate");
        this.radius = radius;
        this.biomeRadius = biomeRadius;
        this.unexplored = unexplored;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            usage(player);
            return;
        }

        String kind = args[0].toLowerCase(Locale.ROOT);
        boolean named = kind.equals(STRUCTURE) || kind.equals(BIOME);
        if (named && args.length < 2) {
            usage(player);
            return;
        }

        String wanted = named ? args[1] : args[0];
        if (!ready(player)) {
            return;
        }

        Found found;
        try {
            found = named && kind.equals(BIOME)
                    ? biome(player, wanted)
                    : structureOr(player, wanted, !named);
        } catch (RuntimeException refused) {
            // A search reaches well past the region the player is standing in, which Folia
            // is within its rights to refuse. Better said plainly than as a stack trace.
            messages.send(player, "world.locate-refused");
            return;
        }
        if (found == null) {
            messages.send(player, "world.locate-unknown", "kind", wanted);
            return;
        }
        if (found.where() == null) {
            messages.send(player, "world.locate-none", "kind", found.name());
            return;
        }

        settle(player);
        surfaced(player, found.name(), player.getLocation(), found.where());
    }

    /**
     * Reports the ground rather than the point the search handed back.
     *
     * <p>A biome search answers with a height somewhere in the column the biome occupies,
     * which over an ocean is a dozen blocks above the water. Sending somebody there lands
     * them in mid-air, or refuses because there is nothing to stand on.
     *
     * <p>The chunk is fetched in the background: reading a heightmap out of an unloaded
     * chunk on the server thread is how a command stalls everybody else.
     */
    private void surfaced(Player player, String kind, Location from, Location found) {
        found.getWorld().getChunkAtAsync(found).whenComplete((chunk, missing) ->
                schedulers.region(found, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Location ground = found.clone();
                    if (missing == null) {
                        ground.setY(found.getWorld().getHighestBlockYAt(found) + 1);
                    }
                    report(player, kind, from, ground);
                }));
    }

    /** A structure, and a biome as well when the player did not say which they meant. */
    private @Nullable Found structureOr(Player player, String wanted, boolean thenBiome) {
        StructureType type = structures().get(key(wanted));
        if (type != null) {
            return new Found(key(wanted), search(player, type));
        }
        return thenBiome ? biome(player, wanted) : null;
    }

    private @Nullable Found biome(Player player, String wanted) {
        NamespacedKey id = id(wanted);
        Biome biome = id == null ? null : Registry.BIOME.get(id);
        if (biome == null) {
            return null;
        }

        int reach = Math.max(MIN_STEP, Math.min(MAX_BIOME_RADIUS, biomeRadius.getAsInt()));
        int step = Math.max(MIN_STEP, reach / SAMPLES_ACROSS);

        Location from = player.getLocation();
        return new Found(key(wanted), from.getWorld().locateNearestBiome(from, biome, reach, step));
    }

    private @Nullable Location search(Player player, StructureType type) {
        Location from = player.getLocation();
        return from.getWorld()
                .locateNearestStructure(from, type, radius.getAsInt(), unexplored.getAsBoolean());
    }

    /**
     * The line, with the coordinates on a tooltip and a teleport behind a click.
     *
     * <p>Only for somebody who could have typed the teleport themselves. Offering a click
     * that answers "you are not allowed to do that" is worse than not offering it.
     */
    private void report(Player player, String kind, Location from, Location found) {
        Component line = messages.render(player, "world.locate-found",
                "kind", kind,
                "x", String.valueOf(found.getBlockX()),
                "y", String.valueOf(found.getBlockY()),
                "z", String.valueOf(found.getBlockZ()),
                "distance", String.valueOf(distance(from, found)));

        boolean mayTeleport = player.hasPermission(TELEPORT_PERMISSION);
        Component hover = messages.render(player,
                mayTeleport ? "world.locate-hover-teleport" : "world.locate-hover",
                "x", String.valueOf(found.getBlockX()),
                "y", String.valueOf(found.getBlockY()),
                "z", String.valueOf(found.getBlockZ()),
                "world", found.getWorld().getName());

        Component shown = line.hoverEvent(HoverEvent.showText(hover));
        if (mayTeleport) {
            shown = shown.clickEvent(ClickEvent.runCommand(teleport(found)));
        }
        player.sendMessage(shown);
    }

    private static String teleport(Location found) {
        return "/tppos " + found.getBlockX() + " " + found.getBlockY() + " " + found.getBlockZ()
                + " " + found.getWorld().getName();
    }

    /** Flat distance: the height a search reports is rarely the height the thing is at. */
    private static long distance(Location from, Location to) {
        double x = to.getX() - from.getX();
        double z = to.getZ() - from.getZ();
        return Math.round(Math.sqrt(x * x + z * z));
    }

    private void usage(Player player) {
        messages.send(player, "world.locate-usage",
                "structures", String.join(", ", structureNames()),
                "biomes", String.valueOf(biomeNames().size()));
    }

    private static Map<String, StructureType> structures() {
        return StructureType.getStructureTypes();
    }

    private static List<String> structureNames() {
        List<String> names = new ArrayList<>(structures().keySet());
        names.sort(String::compareTo);
        return names;
    }

    private static List<String> biomeNames() {
        List<String> names = new ArrayList<>();
        for (Biome biome : Registry.BIOME) {
            names.add(biome.getKey().getKey());
        }
        names.sort(String::compareTo);
        return names;
    }

    private static String key(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /** Null for anything a key cannot be made of, which the registry would refuse anyway. */
    private static @Nullable NamespacedKey id(String raw) {
        try {
            return NamespacedKey.minecraft(key(raw));
        } catch (IllegalArgumentException notAKey) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> first = new ArrayList<>(List.of(STRUCTURE, BIOME));
            first.addAll(structureNames());
            return startingWith(args[0], first);
        }
        if (args.length != 2) {
            return List.of();
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case STRUCTURE -> startingWith(args[1], structureNames());
            case BIOME -> startingWith(args[1], biomeNames());
            default -> List.of();
        };
    }

    private record Found(String name, @Nullable Location where) {
    }
}
