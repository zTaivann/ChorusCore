package dev.chorus.core.world.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /spawner <mob> [delay]}: changes what the spawner you are looking at makes. */
public final class SpawnerCommand extends PlayerCommand {

    private static final int RANGE = 8;
    private static final int MAX_DELAY = 20 * 60 * 10;

    public SpawnerCommand(CommandSupport support) {
        super(support, "spawner", "chorus.world.spawner");
    }

    @Override
    protected void execute(Player player, String[] args) {
        Block target = player.getTargetBlockExact(RANGE);
        if (target == null || !(target.getState() instanceof CreatureSpawner spawner)) {
            messages.send(player, "world.spawner-none");
            return;
        }
        if (args.length == 0) {
            EntityType current = spawner.getSpawnedType();
            messages.send(player, "world.spawner-now",
                    "mob", current == null ? "nothing" : current.name().toLowerCase(Locale.ROOT),
                    "delay", String.valueOf(spawner.getDelay()));
            return;
        }

        EntityType type = type(args[0]);
        if (type == null) {
            messages.send(player, "world.spawnmob-unknown", "mob", args[0]);
            return;
        }
        int delay = args.length > 1 ? delay(args[1]) : -1;
        if (args.length > 1 && delay < 0) {
            messages.send(player, "world.spawner-usage");
            return;
        }
        if (!ready(player)) {
            return;
        }

        spawner.setSpawnedType(type);
        if (delay >= 0) {
            spawner.setDelay(delay);
        }
        spawner.update(true, false);

        settle(player);
        messages.send(player, "world.spawner-set", "mob", type.name().toLowerCase(Locale.ROOT));
    }

    private static EntityType type(String raw) {
        try {
            EntityType type = EntityType.valueOf(raw.toUpperCase(Locale.ROOT).replace(' ', '_'));
            return type.isSpawnable() && type.isAlive() ? type : null;
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static int delay(String raw) {
        int ticks = Numbers.integer(raw, -1);
        return ticks < 0 ? -1 : Math.min(MAX_DELAY, ticks);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (type.isSpawnable() && type.isAlive()) {
                names.add(type.name().toLowerCase(Locale.ROOT));
            }
        }
        return startingWith(args[0], names);
    }
}
