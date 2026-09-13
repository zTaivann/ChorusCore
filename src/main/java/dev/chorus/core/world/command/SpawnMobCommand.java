package dev.chorus.core.world.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * {@code /spawnmob <mob> [amount] [player]}: puts mobs where somebody is looking.
 *
 * <p>The amount is capped in the config, since the difference between forty zombies and four
 * thousand is the difference between a test and a restart.
 */
public final class SpawnMobCommand extends ChorusCommand {

    private static final String OTHERS = "chorus.world.spawnmob.others";

    private final IntSupplier limit;

    public SpawnMobCommand(CommandSupport support, IntSupplier limit) {
        super(support, "spawnmob", "chorus.world.spawnmob");
        this.limit = limit;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "world.spawnmob-usage");
            return;
        }

        EntityType type = type(args[0]);
        if (type == null) {
            messages.send(sender, "world.spawnmob-unknown", "mob", args[0]);
            return;
        }

        int wanted = args.length > 1 ? Numbers.integer(args[1], -1) : 1;
        if (wanted <= 0) {
            messages.send(sender, "world.spawnmob-usage");
            return;
        }
        int allowed = Math.max(1, limit.getAsInt());
        if (wanted > allowed) {
            messages.send(sender, "world.spawnmob-limit", "limit", String.valueOf(allowed));
            return;
        }

        Player at = where(sender, args);
        if (at == null) {
            return;
        }
        if (!ready(sender)) {
            return;
        }

        Location target = at.getLocation();
        atPlace(target, () -> {
            for (int spawned = 0; spawned < wanted; spawned++) {
                target.getWorld().spawnEntity(target, type);
            }
        });

        settle(sender);
        messages.send(sender, "world.spawnmob-done",
                "count", String.valueOf(wanted),
                "mob", type.name().toLowerCase(Locale.ROOT),
                "player", at.getName());
    }

    /** Where the mobs go: the sender, or the named player when they are allowed to say. */
    private Player where(CommandSender sender, String[] args) {
        if (args.length > 2) {
            if (!sender.hasPermission(OTHERS)) {
                messages.send(sender, "error.no-permission");
                return null;
            }
            return online(sender, args[2]);
        }
        if (sender instanceof Player self) {
            return self;
        }
        messages.send(sender, "error.players-only");
        return null;
    }

    private static EntityType type(String raw) {
        try {
            EntityType type = EntityType.valueOf(raw.toUpperCase(Locale.ROOT).replace(' ', '_'));
            return type.isSpawnable() && type.isAlive() ? type : null;
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (EntityType type : EntityType.values()) {
                if (type.isSpawnable() && type.isAlive()) {
                    names.add(type.name().toLowerCase(Locale.ROOT));
                }
            }
            return startingWith(args[0], names);
        }
        if (args.length == 3 && sender.hasPermission(OTHERS)) {
            return onlineNames(sender, args[2], true);
        }
        return List.of();
    }
}
