package dev.chorus.core.command;

import dev.chorus.core.ChorusPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RootCommand extends ChorusCommand {

    private final ChorusPlugin plugin;

    public RootCommand(ChorusPlugin plugin, CommandSupport support) {
        super(support, "chorus", "chorus.admin");
        this.plugin = plugin;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            messages.send(sender, "core.reloaded");
            return;
        }
        messages.send(sender, "core.usage", "version", plugin.getDescription().getVersion());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !allowed(sender)) {
            return List.of();
        }
        return startingWith(args[0], List.of("reload"));
    }
}
