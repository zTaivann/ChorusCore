package dev.chorus.core.command;

import org.bukkit.command.CommandSender;

/**
 * Stands in for the commands of a module that is switched off.
 *
 * <p>Leaving them unregistered would make Bukkit print its own usage line instead, which
 * reads like the plugin is broken rather than like a deliberate setting.
 */
public final class DisabledCommand extends ChorusCommand {

    public DisabledCommand(CommandSupport support, String name) {
        super(support, name, null);
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        messages.send(sender, "error.module-disabled");
    }
}
