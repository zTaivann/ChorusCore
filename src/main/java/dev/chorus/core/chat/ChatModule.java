package dev.chorus.core.chat;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.chat.command.MessageCommand;
import dev.chorus.core.chat.command.ReplyCommand;
import dev.chorus.core.chat.command.SocialSpyCommand;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;

public final class ChatModule implements ChorusModule, Listener {

    private static final String CONFIG = "modules/chat.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private PrivateMessages chat;

    public ChatModule(ChorusPlugin plugin, CommandSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("msg", "reply", "socialspy");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        chat = new PrivateMessages(ChatSettings.read(config.section("chat")));

        plugin.register(this);
        commands.add(plugin.register(new MessageCommand(support, chat)));
        commands.add(plugin.register(new ReplyCommand(support, chat)));
        commands.add(plugin.register(new SocialSpyCommand(support, chat)));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chat.forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void disable() {
        if (chat != null) {
            chat.clear();
        }
    }

    @Override
    public void reload() {
        if (chat == null) {
            return;
        }
        chat.apply(ChatSettings.read(config.section("chat")));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }
}
