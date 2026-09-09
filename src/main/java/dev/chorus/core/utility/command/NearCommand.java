package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.utility.UtilitySettings;
import dev.chorus.core.utility.UtilityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NearCommand extends PlayerCommand {

    private static final String EXEMPT_PERMISSION = "chorus.utility.near.exempt";

    private final UtilityService utility;

    public NearCommand(CommandSupport support, UtilityService utility) {
        super(support, "near", "chorus.utility.near");
        this.utility = utility;
    }

    @Override
    protected void execute(Player player, String[] args) {
        UtilitySettings.Near settings = utility.settings().near();

        int radius = settings.defaultRadius();
        if (args.length > 0) {
            radius = parse(args[0]);
            if (radius <= 0 || radius > settings.maxRadius()) {
                messages.send(player, "utility.near-radius",
                        "max", String.valueOf(settings.maxRadius()));
                return;
            }
        }
        if (!ready(player)) {
            return;
        }

        List<Found> found = search(player, radius);
        if (found.isEmpty()) {
            messages.send(player, "utility.near-none", "radius", String.valueOf(radius));
            return;
        }
        settle(player);

        messages.send(player, "utility.near-header",
                "count", String.valueOf(found.size()),
                "radius", String.valueOf(radius));

        List<Component> entries = new ArrayList<>(found.size());
        for (Found each : found) {
            entries.add(messages.render("utility.near-entry",
                    "player", each.player().getName(),
                    "distance", String.valueOf(each.blocks())));
        }
        player.sendMessage(Component.join(
                JoinConfiguration.separator(messages.render("utility.near-separator")), entries));
    }

    /**
     * Walks the world's player list rather than asking for nearby entities: this only ever
     * cares about players, and getNearbyEntities would sweep every chunk in the box for mobs
     * and dropped items to throw almost all of them away again.
     */
    private List<Found> search(Player player, int radius) {
        Location from = player.getLocation();
        long limit = (long) radius * radius;

        List<Found> found = new ArrayList<>();
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || !player.canSee(other)
                    || other.hasPermission(EXEMPT_PERMISSION)) {
                continue;
            }
            double distanceSquared = other.getLocation().distanceSquared(from);
            if (distanceSquared <= limit) {
                found.add(new Found(other, (int) Math.round(Math.sqrt(distanceSquared))));
            }
        }
        found.sort(Comparator.comparingInt(Found::blocks));
        return found;
    }

    private static int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private record Found(Player player, int blocks) {
    }
}
