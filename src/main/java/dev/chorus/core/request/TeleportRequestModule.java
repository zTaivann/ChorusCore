package dev.chorus.core.request;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.request.command.BackCommand;
import dev.chorus.core.request.command.TeleportRequestCommand;
import dev.chorus.core.request.command.TeleportResponseCommand;
import dev.chorus.core.request.command.TpCancelCommand;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class TeleportRequestModule implements ChorusModule {

    private static final long SWEEP_INTERVAL_TICKS = 20L * 5;

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final TeleportService teleports;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private TeleportRequestService requests;
    private BukkitTask sweeper;

    public TeleportRequestModule(ChorusPlugin plugin, CommandSupport support, TeleportService teleports) {
        this.plugin = plugin;
        this.support = support;
        this.teleports = teleports;
    }

    @Override
    public String name() {
        return "teleport-requests";
    }

    @Override
    public String configPath() {
        return ChorusPlugin.TELEPORT_CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "back");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(ChorusPlugin.TELEPORT_CONFIG);
        requests = new TeleportRequestService(timeoutSeconds());

        plugin.register(new RequestListener(requests));
        commands.add(plugin.register(new TeleportRequestCommand(support, requests,
                TeleportRequest.Direction.TO_TARGET, "tpa", "chorus.tpa.use")));
        commands.add(plugin.register(new TeleportRequestCommand(support, requests,
                TeleportRequest.Direction.TO_SENDER, "tpahere", "chorus.tpa.here")));
        commands.add(plugin.register(new TeleportResponseCommand(support, requests, teleports,
                true, "tpaccept", "chorus.tpa.accept")));
        commands.add(plugin.register(new TeleportResponseCommand(support, requests, teleports,
                false, "tpdeny", "chorus.tpa.deny")));
        commands.add(plugin.register(new TpCancelCommand(support, requests)));
        commands.add(plugin.register(new BackCommand(support, teleports)));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());

        sweeper = plugin.getServer().getScheduler().runTaskTimer(plugin, this::dropExpired,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
    }

    @Override
    public void disable() {
        if (sweeper != null) {
            sweeper.cancel();
        }
        if (requests != null) {
            requests.clear();
        }
    }

    @Override
    public void reload() {
        if (requests == null) {
            return;
        }
        requests.apply(timeoutSeconds());
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    private void dropExpired() {
        if (requests.isEmpty()) {
            return;
        }

        Server server = plugin.getServer();
        Messages messages = plugin.messages();
        for (TeleportRequest expired : requests.removeExpired(System.currentTimeMillis())) {
            Player sender = server.getPlayer(expired.sender());
            Player target = server.getPlayer(expired.target());
            if (sender != null && target != null) {
                messages.send(sender, "request.expired", "player", target.getName());
            }
        }
    }

    private int timeoutSeconds() {
        return Math.max(5, config.section("requests").getInt("timeout-seconds", 60));
    }
}
