package dev.chorus.core.custom;

import dev.chorus.core.command.Cooldowns;
import dev.chorus.core.command.Durations;
import dev.chorus.core.locale.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A command that exists only because the config says so.
 *
 * <p>These are not in plugin.yml, so they are built here and put straight into the server's
 * command map when the module starts. The permission is checked in code for the same reason
 * every other command in the plugin does it: Bukkit would otherwise answer with its own text.
 */
public final class CustomCommand extends Command {

    private static final String COOLDOWN_BYPASS = "chorus.bypass.cooldown";

    /** Names part-way through their console lines, so one cannot call itself. */
    private static final Set<String> running = ConcurrentHashMap.newKeySet();

    private final CustomDefinition definition;
    private final Messages messages;
    private final Cooldowns cooldowns;
    private final Server server;

    CustomCommand(CustomDefinition definition, Messages messages, Cooldowns cooldowns, Server server) {
        super(definition.name(), definition.description(), "/" + definition.name(),
                new ArrayList<>(definition.aliases()));
        this.definition = definition;
        this.messages = messages;
        this.cooldowns = cooldowns;
        this.server = server;
    }

    CustomDefinition definition() {
        return definition;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label,
                           @NotNull String[] args) {
        if (!definition.permission().isEmpty() && !sender.hasPermission(definition.permission())) {
            messages.send(sender, "error.no-permission");
            return true;
        }
        if (onCooldown(sender)) {
            return true;
        }

        String who = sender.getName();
        for (String line : definition.messages()) {
            sender.sendMessage(substitute(messages.parse(line), who));
        }
        if (sender instanceof Player player) {
            definition.sound().play(player, player.getLocation());
            definition.playerCommands().forEach(
                    command -> player.performCommand(command.replace("%player%", who)));
        }
        if (!running.add(definition.name())) {
            // A command listed among its own run-as-console lines would otherwise call
            // itself until the stack ran out, taking the server with it.
            messages.send(sender, "error.command-recursion");
            return true;
        }
        try {
            definition.consoleCommands().forEach(command -> server.dispatchCommand(
                    server.getConsoleSender(), command.replace("%player%", who)));
        } finally {
            running.remove(definition.name());
        }
        return true;
    }

    private boolean onCooldown(CommandSender sender) {
        if (definition.cooldownSeconds() <= 0 || !(sender instanceof Player player)
                || player.hasPermission(COOLDOWN_BYPASS)) {
            return false;
        }

        String key = "custom:" + definition.name();
        long now = System.currentTimeMillis();
        long left = cooldowns.remaining(player.getUniqueId(), key, now);
        if (left > 0) {
            messages.send(player, "cooldown.wait", "time", Durations.format(left));
            return true;
        }
        cooldowns.start(player.getUniqueId(), key, definition.cooldownSeconds(), now);
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
