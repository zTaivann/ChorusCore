package dev.chorus.core.world.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /tree <kind>}: grows a tree where the player is looking. */
public final class TreeCommand extends PlayerCommand {

    private static final int RANGE = 64;

    public TreeCommand(CommandSupport support) {
        super(support, "tree", "chorus.world.tree");
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "world.tree-usage", "kinds", String.join(", ", kinds()));
            return;
        }

        TreeType type = type(args[0]);
        if (type == null) {
            messages.send(player, "world.tree-unknown", "kind", args[0]);
            return;
        }

        Block target = player.getTargetBlockExact(RANGE);
        if (target == null) {
            messages.send(player, "world.tree-nowhere");
            return;
        }
        if (!ready(player)) {
            return;
        }

        Location where = target.getLocation().add(0, 1, 0);
        if (!where.getWorld().generateTree(where, type)) {
            messages.send(player, "world.tree-refused");
            return;
        }

        settle(player);
        messages.send(player, "world.tree-grown", "kind", args[0].toLowerCase(Locale.ROOT));
    }

    private static TreeType type(String raw) {
        try {
            return TreeType.valueOf(raw.toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static List<String> kinds() {
        List<String> names = new ArrayList<>();
        for (TreeType type : TreeType.values()) {
            names.add(type.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? startingWith(args[0], kinds()) : List.of();
    }
}
