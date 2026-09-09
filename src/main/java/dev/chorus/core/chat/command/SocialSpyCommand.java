package dev.chorus.core.chat.command;

import dev.chorus.core.chat.PrivateMessages;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.entity.Player;

public final class SocialSpyCommand extends PlayerCommand {

    private final PrivateMessages chat;

    public SocialSpyCommand(CommandSupport support, PrivateMessages chat) {
        super(support, "socialspy", "chorus.chat.spy");
        this.chat = chat;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!chat.settings().spyEnabled()) {
            messages.send(player, "chat.spy-unavailable");
            return;
        }
        if (!ready(player)) {
            return;
        }

        boolean watching = chat.toggleSpy(player.getUniqueId());
        settle(player);
        messages.send(player, watching ? "chat.spy-enabled" : "chat.spy-disabled");
    }
}
