package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.preference.PlayerPreference;
import dev.chorus.core.preference.PlayerPreferenceService;
import dev.chorus.core.request.TeleportRequest;
import dev.chorus.core.request.TeleportRequestService;
import dev.chorus.core.teleport.TeleportService;
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

    private static final String IGNORE_TOGGLE_PERMISSION = "chorus.tpa.toggle.bypass";

    private final TeleportRequestService requests;
    private final TeleportRequest.Direction direction;
    private final PlayerPreferenceService preferences;
    private final TeleportService teleports;

    public TeleportRequestCommand(CommandSupport support, TeleportRequestService requests,
                                  TeleportRequest.Direction direction, String name,
                                  String permission, PlayerPreferenceService preferences,
                                  TeleportService teleports) {
        super(support, name, permission);
        this.requests = requests;
        this.direction = direction;
        this.preferences = preferences;
        this.teleports = teleports;
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
        if (preferences.isSet(target.getUniqueId(), PlayerPreference.TELEPORTS_BLOCKED)
                && !player.hasPermission(IGNORE_TOGGLE_PERMISSION)) {
            messages.send(player, "request.blocked", "player", target.getName());
            return;
        }
        if (!ready(player)) {
            return;
        }

        if (preferences.isSet(target.getUniqueId(), PlayerPreference.TELEPORTS_AUTOMATIC)) {
            accept(player, target);
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
        target.sendMessage(buttons(target, player.getName()));
    }

    /** Nobody is asked: the target has already said yes to everything. */
    private void accept(Player player, Player target) {
        settle(player);
        boolean toTarget = direction == TeleportRequest.Direction.TO_TARGET;
        Player traveller = toTarget ? player : target;
        Player anchor = toTarget ? target : player;

        messages.send(player, "request.auto-accepted", "player", target.getName());
        messages.send(target, "request.auto-accepted-by", "player", player.getName());
        teleports.teleport(traveller, anchor.getLocation(), rules(), name());
    }

    /** One clickable line to accept or deny. */
    private Component buttons(Player reader, String from) {
        Component accept = messages.render(reader, "request.button-accept")
                .clickEvent(ClickEvent.runCommand("/tpaccept " + from))
                .hoverEvent(HoverEvent.showText(
                        messages.render(reader, "request.button-accept-hover", "player", from)));
        Component deny = messages.render(reader, "request.button-deny")
                .clickEvent(ClickEvent.runCommand("/tpdeny " + from))
                .hoverEvent(HoverEvent.showText(
                        messages.render(reader, "request.button-deny-hover", "player", from)));

        return messages.prefix()
                .append(accept)
                .append(messages.render(reader, "request.button-separator"))
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
