package dev.chorus.core.staff.command;

import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.staff.FreezeService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FreezeCommand extends ChorusCommand {

    private static final String EXEMPT_PERMISSION = "chorus.staff.freeze.exempt";

    private final FreezeService freezes;
    private final AuditLog audit;

    public FreezeCommand(CommandSupport support, FreezeService freezes, AuditLog audit) {
        super(support, "freeze", "chorus.staff.freeze");
        this.freezes = freezes;
        this.audit = audit;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "staff.freeze-usage");
            return;
        }

        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }
        if (target.hasPermission(EXEMPT_PERMISSION)) {
            messages.send(sender, "staff.freeze-exempt", "player", target.getName());
            return;
        }
        if (!ready(sender)) {
            return;
        }

        boolean frozen = freezes.toggle(target);
        settle(sender);
        audit.record(sender, frozen ? "freeze" : "unfreeze", target.getName(), null);

        messages.send(sender, frozen ? "staff.freeze-on" : "staff.freeze-off",
                "player", target.getName());
        messages.send(target, frozen ? "staff.freeze-received" : "staff.freeze-released");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? onlineNames(sender, args[0], true) : List.of();
    }
}
