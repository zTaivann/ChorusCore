package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.utility.InventoryMirror;
import dev.chorus.core.utility.MirrorService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Backs /invsee and /ecsee; only the half of the player they show differs. */
public final class MirrorCommand extends PlayerCommand {

    private static final String EXEMPT_PERMISSION = "chorus.utility.invsee.exempt";

    private final MirrorService mirrors;
    private final InventoryMirror.Kind kind;

    public MirrorCommand(CommandSupport support, MirrorService mirrors,
                         InventoryMirror.Kind kind, String name) {
        super(support, name, "chorus.utility." + name);
        this.mirrors = mirrors;
        this.kind = kind;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "utility.mirror-usage", "command", name());
            return;
        }

        Player target = player.getServer().getPlayerExact(args[0]);
        if (target == null || !player.canSee(target)) {
            messages.send(player, "error.player-not-found", "player", args[0]);
            return;
        }
        if (target.equals(player)) {
            messages.send(player, "utility.mirror-self");
            return;
        }
        // Staff can be shielded from being looked at by anyone below them.
        if (target.hasPermission(EXEMPT_PERMISSION) && !player.hasPermission("chorus.admin")) {
            messages.send(player, "utility.mirror-exempt", "player", target.getName());
            return;
        }
        if (!ready(player)) {
            return;
        }

        boolean editable = player.hasPermission("chorus.utility." + name() + ".edit");
        settle(player);
        mirrors.open(player, target, kind, editable);
        messages.send(player, editable ? "utility.mirror-opened" : "utility.mirror-readonly",
                "player", target.getName());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player)) {
            return List.of();
        }

        return onlineNames(sender, args[args.length - 1], false);
    }
}
