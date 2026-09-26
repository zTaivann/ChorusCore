package dev.chorus.core.custom;

import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.command.ActionGuard;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Cooldowns;
import dev.chorus.core.command.Durations;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.Schedulers;
import dev.chorus.core.rules.Action;
import dev.chorus.core.rules.Requirement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A command that exists only because the config says so. */
public final class CustomCommand extends Command {

    private static final String COOLDOWN_BYPASS = "chorus.bypass.cooldown";

    /** Names part-way through their console lines, so one cannot call itself. */
    private static final Set<String> running = ConcurrentHashMap.newKeySet();

    private final Messages messages;
    private final ActionGuard guard;
    private final Schedulers schedulers;
    private final AuditLog audit;
    private final Cooldowns cooldowns;
    private final Server server;

    private volatile CustomDefinition definition;

    CustomCommand(CustomDefinition definition, CommandSupport support, Cooldowns cooldowns,
                  Server server) {
        super(definition.name(), definition.description(), "/" + definition.name(),
                new ArrayList<>(definition.aliases()));
        this.messages = support.messages().forCommand();
        this.guard = support.guard();
        this.schedulers = support.schedulers();
        this.audit = support.audit();
        this.cooldowns = cooldowns;
        this.server = server;
        update(definition);
    }

    CustomDefinition definition() {
        return definition;
    }

    /** What it says and does, read again. Its name and aliases stay until a restart. */
    void update(CustomDefinition changed) {
        this.definition = changed;
        messages.override(changed.permissionMessage().isEmpty()
                ? Map.of() : Map.of("error.no-permission", changed.permissionMessage()));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label,
                           @NotNull String[] args) {
        CustomDefinition current = definition;
        if (!current.permission().isEmpty() && !sender.hasPermission(current.permission())) {
            messages.send(sender, "error.no-permission");
            failed(sender, current);
            return true;
        }
        if (sender instanceof Player player) {
            Requirement unmet = guard.unmet(player, current.requires());
            if (unmet != null) {
                unmet.tell(player, messages, "error.command-requirement", "command", current.name());
                failed(sender, current);
                return true;
            }
        }
        if (onCooldown(sender, current)) {
            failed(sender, current);
            return true;
        }
        if (current.log()) {
            audit.record(sender, "used", "/" + current.name(),
                    args.length == 0 ? null : String.join(" ", args));
        }

        String who = sender.getName();
        for (String line : current.messages()) {
            sender.sendMessage(substitute(messages.parse(line), who));
        }
        if (sender instanceof Player player) {
            current.sound().play(player, player.getLocation());
            current.playerCommands().forEach(
                    command -> player.performCommand(command.replace("%player%", who)));
            Action.runAll(current.onSuccess(), player, messages, schedulers,
                    "command", current.name());
        }
        if (!current.consoleCommands().isEmpty()) {
            schedulers.withGlobal(() -> asConsole(sender, who, current));
        }
        return true;
    }

    private void failed(CommandSender sender, CustomDefinition current) {
        if (sender instanceof Player player) {
            Action.runAll(current.onFail(), player, messages, schedulers, "command", current.name());
        }
    }

    private void asConsole(CommandSender sender, String who, CustomDefinition current) {
        if (!running.add(current.name())) {
            // A command among its own run-as-console lines would call itself for ever.
            messages.send(sender, "error.command-recursion");
            return;
        }
        try {
            current.consoleCommands().forEach(command -> server.dispatchCommand(
                    server.getConsoleSender(), command.replace("%player%", who)));
        } finally {
            running.remove(current.name());
        }
    }

    private boolean onCooldown(CommandSender sender, CustomDefinition current) {
        if (current.cooldownSeconds() <= 0 || !(sender instanceof Player player)
                || player.hasPermission(COOLDOWN_BYPASS)) {
            return false;
        }

        String key = Cooldowns.timer("custom:" + current.name(), current.cooldownGroup());
        long now = System.currentTimeMillis();
        long left = cooldowns.remaining(player.getUniqueId(), key, now);
        if (left > 0) {
            messages.send(player, "cooldown.wait", "time", Durations.format(left));
            return true;
        }
        cooldowns.start(player.getUniqueId(), key, current.cooldownSeconds(), now);
        return false;
    }

    /** Put in after parsing, so a nickname can never carry formatting into the line. */
    private static Component substitute(Component line, String player) {
        return line.replaceText(TextReplacementConfig.builder()
                .matchLiteral("%player%")
                .replacement(player)
                .build());
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
                                             @NotNull String[] args) {
        return List.of();
    }
}
