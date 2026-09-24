package dev.chorus.core.chat;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.chat.command.IgnoreCommand;
import dev.chorus.core.chat.command.MessageCommand;
import dev.chorus.core.chat.command.MessageToggleCommand;
import dev.chorus.core.chat.command.ReplyCommand;
import dev.chorus.core.chat.command.ReplyToggleCommand;
import dev.chorus.core.chat.command.SocialSpyCommand;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.mail.MailListener;
import dev.chorus.core.mail.MailService;
import dev.chorus.core.mail.SqlMailRepository;
import dev.chorus.core.mail.command.MailCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ChatModule implements ChorusModule, Listener {

    private static final String CONFIG = "modules/chat.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private PrivateMessages chat;
    private IgnoreList ignores;
    private MailService mail;

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
        return List.of("msg", "reply", "socialspy", "msgtoggle", "ignore", "mail", "rtoggle");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        chat = new PrivateMessages(ChatSettings.read(config.section("chat")));

        SqlIgnoreRepository store = new SqlIgnoreRepository(plugin.storage());
        try {
            store.createTables();
        } catch (SQLException exception) {
            throw new IllegalStateException("The ignore table could not be created", exception);
        }
        ignores = new IgnoreList(store, plugin.worker(), plugin.getLogger());

        SqlMailRepository letters = new SqlMailRepository(plugin.storage());
        try {
            letters.createTables();
        } catch (SQLException exception) {
            throw new IllegalStateException("The mail table could not be created", exception);
        }
        mail = new MailService(letters, plugin.worker(), plugin.mainThread(), plugin.getLogger());
        mail.apply(config.section("mail"));
        mail.prune();
        plugin.register(new MailListener(mail, plugin.messages()));

        plugin.register(this);
        commands.add(plugin.register(new MessageCommand(support, chat, plugin.flags(), ignores)));
        commands.add(plugin.register(new ReplyCommand(support, chat, plugin.flags(), ignores)));
        commands.add(plugin.register(new SocialSpyCommand(support, chat)));
        commands.add(plugin.register(new MessageToggleCommand(support, plugin.flags())));
        commands.add(plugin.register(new IgnoreCommand(support, ignores)));
        commands.add(plugin.register(new MailCommand(support, mail, plugin.profiles())));
        commands.add(plugin.register(new ReplyToggleCommand(support, plugin.flags())));
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
        if (ignores != null) {
            ignores.clear();
        }
    }

    @Override
    public void reload() {
        if (chat == null) {
            return;
        }
        chat.apply(ChatSettings.read(config.section("chat")));
        mail.apply(config.section("mail"));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }
}
