package dev.chorus.core.staff;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.staff.command.FreezeCommand;
import dev.chorus.core.staff.command.GameModeCommand;
import dev.chorus.core.staff.command.LockdownCommand;
import dev.chorus.core.staff.command.NoteCommand;
import dev.chorus.core.staff.command.StaffLogCommand;
import dev.chorus.core.staff.command.SudoCommand;
import dev.chorus.core.staff.command.TeleportHereCommand;
import dev.chorus.core.staff.command.TeleportToCommand;
import dev.chorus.core.staff.command.TempFlyCommand;
import dev.chorus.core.staff.command.TpAllCommand;
import dev.chorus.core.staff.command.TpPosCommand;
import dev.chorus.core.staff.command.VanishCommand;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.GameMode;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class StaffModule implements ChorusModule {

    private static final String CONFIG = "modules/staff.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final TeleportService teleports;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private VanishService vanish;
    private FreezeService freezes;
    private TempFlyService flights;
    private LockdownService lockdown;

    public StaffModule(ChorusPlugin plugin, CommandSupport support, TeleportService teleports) {
        this.plugin = plugin;
        this.support = support;
        this.teleports = teleports;
    }

    @Override
    public String name() {
        return "staff";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("tp", "tphere", "tppos", "tpall", "vanish",
                "gamemode", "gmc", "gms", "gma", "gmsp",
                "freeze", "sudo", "lockdown", "tempfly", "note", "stafflog");
    }

    public VanishService vanish() {
        return vanish;
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        vanish = new VanishService(plugin);
        freezes = new FreezeService(plugin, plugin.messages());
        flights = new TempFlyService(plugin, plugin.messages());
        lockdown = new LockdownService(plugin.messages());
        plugin.register(vanish);
        plugin.register(freezes);
        plugin.register(flights);
        plugin.register(lockdown);
        freezes.start();

        NoteRepository noteStore = new SqlNoteRepository(plugin.storage());
        try {
            noteStore.createTables();
        } catch (SQLException exception) {
            throw new IllegalStateException("The staff notes table could not be created", exception);
        }
        NoteService notes = new NoteService(noteStore, plugin.worker(), plugin.mainThread());
        AuditLog audit = plugin.audit();

        commands.add(plugin.register(new TeleportToCommand(support, teleports)));
        commands.add(plugin.register(new TeleportHereCommand(support, teleports)));
        commands.add(plugin.register(new TpPosCommand(support, teleports)));
        commands.add(plugin.register(new TpAllCommand(support, teleports)));
        commands.add(plugin.register(new VanishCommand(support, vanish)));
        commands.add(plugin.register(new GameModeCommand(support, "gamemode", null)));
        commands.add(plugin.register(new GameModeCommand(support, "gmc", GameMode.CREATIVE)));
        commands.add(plugin.register(new GameModeCommand(support, "gms", GameMode.SURVIVAL)));
        commands.add(plugin.register(new GameModeCommand(support, "gma", GameMode.ADVENTURE)));
        commands.add(plugin.register(new GameModeCommand(support, "gmsp", GameMode.SPECTATOR)));
        commands.add(plugin.register(new FreezeCommand(support, freezes, audit)));
        commands.add(plugin.register(new SudoCommand(support, audit)));
        commands.add(plugin.register(new LockdownCommand(support, lockdown, audit)));
        commands.add(plugin.register(new TempFlyCommand(support, flights, audit)));
        commands.add(plugin.register(new NoteCommand(support, notes, audit)));
        commands.add(plugin.register(new StaffLogCommand(support, audit)));

        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    @Override
    public void disable() {
        if (vanish != null) {
            vanish.shutdown();
        }
        if (freezes != null) {
            freezes.shutdown();
        }
        if (flights != null) {
            flights.shutdown();
        }
    }

    @Override
    public void reload() {
        if (config != null) {
            CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
        }
    }
}
