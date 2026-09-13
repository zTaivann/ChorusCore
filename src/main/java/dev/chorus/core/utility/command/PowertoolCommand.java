package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.utility.powertool.Powertools;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /powertool <command>}: ties a command to the item in your hand.
 *
 * <p>{@code %player%} in the command becomes whoever you right-click, which is what makes a
 * stick that teleports you to somebody worth having.
 */
public final class PowertoolCommand extends PlayerCommand {

    private static final List<String> ACTIONS = List.of("add", "list", "clear");

    private final Powertools powertools;

    public PowertoolCommand(CommandSupport support, Powertools powertools) {
        super(support, "powertool", "chorus.utility.powertool");
        this.powertools = powertools;
    }

    @Override
    protected void execute(Player player, String[] args) {
        Material held = player.getInventory().getItemInMainHand().getType();
        if (held.isAir()) {
            messages.send(player, "utility.powertool-empty-hand");
            return;
        }

        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "" -> messages.send(player, "utility.powertool-usage");
            case "list" -> list(player, held);
            case "clear" -> clear(player, held, args.length > 1 && args[1].equalsIgnoreCase("all"));
            case "add" -> bind(player, held, rest(args, 1), true);
            default -> bind(player, held, rest(args, 0), false);
        }
    }

    private void bind(Player player, Material held, String command, boolean adding) {
        if (command.isEmpty()) {
            messages.send(player, "utility.powertool-usage");
            return;
        }
        if (command.length() > Powertools.MAX_LENGTH) {
            messages.send(player, "utility.powertool-too-long",
                    "max", String.valueOf(Powertools.MAX_LENGTH));
            return;
        }
        if (!ready(player)) {
            return;
        }

        if (adding && !powertools.add(player.getUniqueId(), held, command)) {
            messages.send(player, "utility.powertool-full",
                    "max", String.valueOf(Powertools.MAX_COMMANDS));
            return;
        }
        if (!adding) {
            powertools.bind(player.getUniqueId(), held, command);
        }

        settle(player);
        messages.send(player, adding ? "utility.powertool-added" : "utility.powertool-bound",
                "item", name(held), "command", command);
    }

    private void list(Player player, Material held) {
        List<String> commands = powertools.on(player.getUniqueId(), held);
        if (commands.isEmpty()) {
            messages.send(player, "utility.powertool-list-empty", "item", name(held));
            return;
        }

        messages.send(player, "utility.powertool-list-header", "item", name(held));
        for (String command : commands) {
            messages.send(player, "utility.powertool-list-entry", "command", command);
        }
    }

    private void clear(Player player, Material held, boolean everything) {
        if (everything) {
            int cleared = powertools.unbindAll(player.getUniqueId());
            messages.send(player, "utility.powertool-cleared-all",
                    "count", String.valueOf(cleared));
            return;
        }
        if (!powertools.unbind(player.getUniqueId(), held)) {
            messages.send(player, "utility.powertool-list-empty", "item", name(held));
            return;
        }
        messages.send(player, "utility.powertool-cleared", "item", name(held));
    }

    /** The words from {@code start} on, with a slash the player typed out of habit removed. */
    private static String rest(String[] args, int start) {
        if (start >= args.length) {
            return "";
        }
        String joined = String.join(" ", Arrays.asList(args).subList(start, args.length)).trim();
        return joined.startsWith("/") ? joined.substring(1).trim() : joined;
    }

    private static String name(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 && allowed(sender)
                ? startingWith(args[0], ACTIONS)
                : List.of();
    }
}
