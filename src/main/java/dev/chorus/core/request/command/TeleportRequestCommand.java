package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.request.TeleportRequest;
import dev.chorus.core.request.TeleportRequestService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Backs both /tpa and /tpahere; only the direction of travel differs. */
public final class TeleportRequestCommand extends PlayerCommand {

    private final TeleportRequestService requests;
    private final TeleportRequest.Direction direction;

    public TeleportRequestCommand(CommandSupport support, TeleportRequestService requests,
                                  TeleportRequest.Direction direction, String name, String permission) {
        super(support, name, permission);
        this.requests = requests;
        this.direction = direction;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "request.usage", "command", name());
            return;
        }

        Player target = player.getServer().getPlayerExact(args[0]);
        if (target == null || !player.canSee(target)) {
            messages.send(player, "error.player-not-found", "player", args[0]);
            return;
        }
        if (target.equals(player)) {
            messages.send(player, "request.self");
            return;
        }
        if (!ready(player)) {
            return;
        }

        int timeout = requests.timeoutSeconds();
        TeleportRequest request = new TeleportRequest(player.getUniqueId(), target.getUniqueId(),
                direction, System.currentTimeMillis() + timeout * 1000L);
        if (!requests.add(request)) {
            messages.send(player, "request.duplicate", "player", target.getName());
            return;
        }
        settle(player);

        messages.send(player, "request.sent",
                "player", target.getName(),
                "seconds", String.valueOf(timeout));
        messages.send(target, direction == TeleportRequest.Direction.TO_TARGET
                        ? "request.received"
                        : "request.received-here",
                "player", player.getName());
        target.sendMessage(buttons(player.getName()));
    }

    /** One clickable line, so nobody has to type a name back to answer. */
    private Component buttons(String from) {
        Component accept = messages.render("request.button-accept")
                .clickEvent(ClickEvent.runCommand("/tpaccept " + from))
                .hoverEvent(HoverEvent.showText(
                        messages.render("request.button-accept-hover", "player", from)));
        Component deny = messages.render("request.button-deny")
                .clickEvent(ClickEvent.runCommand("/tpdeny " + from))
                .hoverEvent(HoverEvent.showText(
                        messages.render("request.button-deny-hover", "player", from)));

        return messages.prefix()
                .append(accept)
                .append(messages.render("request.button-separator"))
                .append(deny);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (Player online : player.getServer().getOnlinePlayers()) {
            if (!online.equals(player) && player.canSee(online)) {
                names.add(online.getName());
            }
        }
        return startingWith(args[0], names);
    }
}
