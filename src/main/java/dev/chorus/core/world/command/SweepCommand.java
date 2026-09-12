package dev.chorus.core.world.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Confirmations;
import dev.chorus.core.world.SweepTarget;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * {@code /sweep [what] [radius]}: clears entities out of the world.
 *
 * <p>The word can be a group such as {@code drops} or {@code monsters}, or the name of one
 * kind of mob. Without a radius it takes the whole world the sender is standing in, which is
 * the version worth asking twice about.
 */
public final class SweepCommand extends ChorusCommand {

    private static final int MAX_RADIUS = 512;

    private final Confirmations confirmations;

    public SweepCommand(CommandSupport support, Confirmations confirmations) {
        super(support, "sweep", "chorus.world.sweep");
        this.confirmations = confirmations;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        World world = worldOf(sender);
        if (world == null) {
            messages.send(sender, "error.players-only");
            return;
        }

        String wanted = args.length > 0 ? args[0] : SweepTarget.DROPS.label();
        Predicate<Entity> matches = resolve(wanted);
        if (matches == null) {
            messages.send(sender, "world.sweep-unknown", "target", wanted);
            return;
        }

        int radius = args.length > 1 ? radius(args[1]) : 0;
        if (radius < 0) {
            messages.send(sender, "world.sweep-radius");
            return;
        }

        List<Entity> found = radius == 0
                ? world.getEntities().stream().filter(matches).toList()
                : nearby(sender, radius, matches);
        if (found.isEmpty()) {
            messages.send(sender, "world.sweep-none", "target", wanted);
            return;
        }

        // Only the whole-world version is worth a second thought; a radius is already a
        // decision about where.
        if (radius == 0 && !confirmations.confirmed(sender, "sweep:" + world.getName() + ':' + wanted,
                "world.sweep-confirm",
                "count", String.valueOf(found.size()), "target", wanted, "world", world.getName())) {
            return;
        }
        if (!ready(sender)) {
            return;
        }

        found.forEach(Entity::remove);
        settle(sender);
        messages.send(sender, "world.sweep-done",
                "count", String.valueOf(found.size()), "target", wanted);
    }

    private static List<Entity> nearby(CommandSender sender, int radius, Predicate<Entity> matches) {
        Location centre = ((Player) sender).getLocation();
        List<Entity> found = new ArrayList<>();
        for (Entity entity : centre.getWorld().getNearbyEntities(centre, radius, radius, radius)) {
            if (matches.test(entity)) {
                found.add(entity);
            }
        }
        return found;
    }

    /** A group first, then the name of one kind of mob. */
    private static @Nullable Predicate<Entity> resolve(String value) {
        SweepTarget target = SweepTarget.of(value);
        if (target != null) {
            return target::covers;
        }
        try {
            return SweepTarget.ofType(EntityType.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static @Nullable World worldOf(CommandSender sender) {
        return sender instanceof Player player ? player.getWorld() : null;
    }

    /** Negative for anything that is not a usable radius. */
    private static int radius(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value <= 0 ? -1 : Math.min(MAX_RADIUS, value);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(SweepTarget.labels());
        for (EntityType type : EntityType.values()) {
            if (type.isAlive()) {
                options.add(type.name().toLowerCase(Locale.ROOT));
            }
        }
        return startingWith(args[0], options);
    }
}
