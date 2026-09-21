package dev.chorus.core.world;

import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.ChorusTask;
import dev.chorus.core.platform.Schedulers;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clears the ground on a timer, with a warning first. */
public final class AutoSweep {

    private static final long TICKS_PER_SECOND = 20L;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;

    private final Server server;
    private final Schedulers schedulers;
    private final Messages messages;

    private boolean enabled;
    private int minutes = 15;
    private int warnSeconds = 30;
    private SweepTarget target = SweepTarget.DROPS;
    private List<String> worlds = List.of();

    private @Nullable ChorusTask timer;
    private @Nullable ChorusTask pending;

    public AutoSweep(Server server, Schedulers schedulers, Messages messages) {
        this.server = server;
        this.schedulers = schedulers;
        this.messages = messages;
    }

    public void apply(ConfigurationSection world) {
        ConfigurationSection auto = world.getConfigurationSection("auto-sweep");
        if (auto == null) {
            stop();
            enabled = false;
            return;
        }

        enabled = auto.getBoolean("enabled", false);
        minutes = Math.max(1, auto.getInt("minutes", 15));
        warnSeconds = Math.max(0, Math.min(300, auto.getInt("warn-seconds", 30)));
        SweepTarget wanted = SweepTarget.of(auto.getString("target", "drops"));
        target = wanted == null ? SweepTarget.DROPS : wanted;
        worlds = List.copyOf(auto.getStringList("worlds"));
        restart();
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
    }

    private void restart() {
        stop();
        if (!enabled) {
            return;
        }
        long period = minutes * TICKS_PER_MINUTE;
        timer = schedulers.globalTimer(this::announce, period, period);
    }

    private void announce() {
        if (warnSeconds <= 0) {
            sweep();
            return;
        }
        messages.broadcast(server, "world.auto-sweep-warning",
                "seconds", String.valueOf(warnSeconds),
                "target", target.label());
        pending = schedulers.globalLater(this::sweep, warnSeconds * TICKS_PER_SECOND);
    }

    private void sweep() {
        pending = null;
        SweepTarget clearing = target;
        EntitySweep.run(schedulers, worldsToClear(), clearing::covers, true, removed -> {
            if (removed > 0) {
                messages.broadcast(server, "world.auto-sweep-done",
                        "count", String.valueOf(removed),
                        "target", clearing.label());
            }
        });
    }

    /** An empty list in the config means every world. */
    private List<World> worldsToClear() {
        if (worlds.isEmpty()) {
            return server.getWorlds();
        }
        List<World> found = new ArrayList<>(worlds.size());
        for (String name : worlds) {
            World world = server.getWorld(name);
            if (world != null) {
                found.add(world);
            }
        }
        return found;
    }

    /** For the startup line, so a server owner can see it is on without opening the file. */
    public String describe() {
        return enabled
                ? "every " + minutes + " minutes, " + target.label().toLowerCase(Locale.ROOT)
                : "off";
    }
}
