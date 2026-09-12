package dev.chorus.core.world.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.world.UnlimitedPlacing;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** {@code /unlimited [list|clear]}: places the held block for ever without using it up. */
public final class UnlimitedCommand extends PlayerCommand {

    private static final List<String> ACTIONS = List.of("list", "clear");

    private final UnlimitedPlacing placing;

    public UnlimitedCommand(CommandSupport support, UnlimitedPlacing placing) {
        super(support, "unlimited", "chorus.world.unlimited");
        this.placing = placing;
    }

    @Override
    protected void execute(Player player, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            list(player);
            return;
        }
        if (action.equals("clear")) {
            messages.send(player, placing.clear(player)
                    ? "world.unlimited-cleared"
                    : "world.unlimited-empty");
            return;
        }

        Material held = player.getInventory().getItemInMainHand().getType();
        if (held.isAir() || !held.isBlock()) {
            messages.send(player, "world.unlimited-not-a-block");
            return;
        }
        if (!ready(player)) {
            return;
        }

        boolean on = placing.toggle(player, held);
        settle(player);
        messages.send(player, on ? "world.unlimited-on" : "world.unlimited-off",
                "item", held.name().toLowerCase(Locale.ROOT));
    }

    private void list(Player player) {
        Set<Material> materials = placing.of(player);
        if (materials.isEmpty()) {
            messages.send(player, "world.unlimited-empty");
            return;
        }

        StringBuilder names = new StringBuilder();
        for (Material material : materials) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(material.name().toLowerCase(Locale.ROOT));
        }
        messages.send(player, "world.unlimited-list",
                "count", String.valueOf(materials.size()), "items", names.toString());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? startingWith(args[0], ACTIONS) : List.of();
    }
}
