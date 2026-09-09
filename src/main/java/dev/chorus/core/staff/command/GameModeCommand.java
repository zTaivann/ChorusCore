package dev.chorus.core.staff.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Backs /gamemode and the four short forms.
 *
 * <p>A fixed mode means the command already knows what it does, so /gmc takes a player name
 * where /gamemode takes the mode first. Each mode carries its own permission, which is what
 * lets a rank have creative without also having spectator.
 */
public final class GameModeCommand extends ChorusCommand {

    private static final String OTHERS_PERMISSION = "chorus.staff.gamemode.others";

    private final @Nullable GameMode fixed;

    public GameModeCommand(CommandSupport support, String name, @Nullable GameMode fixed) {
        super(support, name, "chorus.staff.gamemode");
        this.fixed = fixed;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        int firstName = fixed == null ? 1 : 0;

        GameMode mode = fixed;
        if (mode == null) {
            if (args.length == 0) {
                messages.send(sender, "staff.gamemode-usage");
                return;
            }
            mode = parse(args[0]);
            if (mode == null) {
                messages.send(sender, "staff.gamemode-unknown", "mode", args[0]);
                return;
            }
        }

        String modeName = mode.name().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("chorus.staff.gamemode." + modeName)) {
            messages.send(sender, "error.no-permission");
            return;
        }

        Player target;
        if (args.length > firstName) {
            if (!sender.hasPermission(OTHERS_PERMISSION)) {
                messages.send(sender, "error.no-permission");
                return;
            }
            target = online(sender, args[firstName]);
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            messages.send(sender, "error.players-only");
            return;
        }
        if (target == null) {
            return;
        }
        if (target.getGameMode() == mode) {
            messages.send(sender, "staff.gamemode-already", "mode", modeName);
            return;
        }
        if (!ready(sender)) {
            return;
        }

        target.setGameMode(mode);
        settle(sender);
        if (target.equals(sender)) {
            messages.send(sender, "staff.gamemode-set", "mode", modeName);
        } else {
            messages.send(sender, "staff.gamemode-set-other",
                    "player", target.getName(), "mode", modeName);
            messages.send(target, "staff.gamemode-received",
                    "player", sender.getName(), "mode", modeName);
        }
    }

    /** Accepts the name, the short form and the vanilla number. */
    private static @Nullable GameMode parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (fixed == null && args.length == 1) {
            return startingWith(args[0], List.of("survival", "creative", "adventure", "spectator"));
        }
        int firstName = fixed == null ? 2 : 1;
        if (args.length == firstName && sender.hasPermission(OTHERS_PERMISSION)) {
            return onlineNames(sender, args[firstName - 1], true);
        }
        return List.of();
    }
}
