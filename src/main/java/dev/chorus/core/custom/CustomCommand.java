package dev.chorus.core.custom;

import dev.chorus.commons.command.Durations;
import dev.chorus.commons.platform.Schedulers;
import dev.chorus.core.command.ActionGuard;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Cooldowns;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.rules.Action;
import dev.chorus.core.rules.Requirement;
import dev.chorus.core.stafflog.StaffLog;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A command defined in the config. */
public final class CustomCommand extends Command {

    private static final String COOLDOWN_BYPASS = "chorus.bypass.cooldown";

    /** Names part-way through their console lines, so one cannot call itself. */
    private static final Set<String> running = ConcurrentHashMap.newKeySet();

    private final Messages messages;
    private final ActionGuard guard;
    private final Schedulers schedulers;
    private final StaffLog staffLog;
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
        this.staffLog = support.staffLog();
        this.cooldowns = cooldowns;
        this.server = server;
        update(definition);
    }

    CustomDefinition definition() {
        return definition;
    }

    /** Replaces its definition. The name and aliases stay until a restart. */
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
        if (!ready(sender, current)) {
            failed(sender, current);
            return true;
        }
        if (current.log()) {
            staffLog.record(sender, "used", "/" + current.name(),
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

    /** Checks the cooldown and the day's uses, and counts this one when it may run. */
    private boolean ready(CommandSender sender, CustomDefinition current) {
        if (!(sender instanceof Player player) || player.hasPermission(COOLDOWN_BYPASS)) {
            return true;
        }

        String timer = Cooldowns.timer("custom:" + current.name(), current.cooldownGroup());
        UUID owner = current.cooldownScope().owner(player);
        long now = System.currentTimeMillis();
        if (current.cooldownSeconds() > 0) {
            long left = cooldowns.remaining(owner, timer, now);
            if (left > 0) {
                messages.send(player, current.cooldownScope().waitMessage(),
                        "time", Durations.format(left));
                return false;
            }
        }
        if (!guard.hasUsesLeft(player, timer, current.cooldownScope(), current.daily())) {
            return false;
        }

        if (current.cooldownSeconds() > 0) {
            cooldowns.start(owner, timer, current.cooldownSeconds(), now);
        }
        guard.countUse(player, timer, current.cooldownScope(), current.daily());
        return true;
    }

    /** Inserted after parsing, as plain text. */
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
