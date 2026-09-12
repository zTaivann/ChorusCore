package dev.chorus.core.platform;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Runs work on the thread that is allowed to touch what it is about to touch.
 *
 * <p>On an ordinary server that is the single server thread and every method here goes to
 * the Bukkit scheduler. On Folia there is no single server thread: the world is split into
 * regions that tick in parallel, a player belongs to whichever region they are standing in,
 * and reaching them from anywhere else is a crash. Folia's schedulers are reached by
 * reflection, since the classes are not in the 1.18.2 API this is compiled against.
 *
 * <p>Which method to call follows from what the job touches: {@link #entity} for a player,
 * {@link #region} for blocks somewhere, {@link #global} for the server as a whole.
 */
public final class Schedulers {

    private final Plugin plugin;
    private final @Nullable Folia folia;

    public Schedulers(Plugin plugin) {
        this.plugin = plugin;
        this.folia = Folia.detect(plugin);
    }

    public boolean folia() {
        return folia != null;
    }

    /** The server as a whole: pruning, sweeps, anything belonging to no one place. */
    public void global(Runnable task) {
        if (folia == null) {
            plugin.getServer().getScheduler().runTask(plugin, task);
            return;
        }
        folia.runGlobal(task);
    }

    public ChorusTask globalLater(Runnable task, long delayTicks) {
        long delay = Math.max(1, delayTicks);
        if (folia == null) {
            return wrap(plugin.getServer().getScheduler().runTaskLater(plugin, task, delay));
        }
        return folia.runGlobalDelayed(task, delay);
    }

    public ChorusTask globalTimer(Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(1, delayTicks);
        long period = Math.max(1, periodTicks);
        if (folia == null) {
            return wrap(plugin.getServer().getScheduler().runTaskTimer(plugin, task, delay, period));
        }
        return folia.runGlobalRate(task, delay, period);
    }

    /** Work on one player or mob, which on Folia follows them from region to region. */
    public void entity(Entity entity, Runnable task) {
        if (folia == null) {
            plugin.getServer().getScheduler().runTask(plugin, task);
            return;
        }
        folia.runOnEntity(entity, task, 0);
    }

    public ChorusTask entityLater(Entity entity, Runnable task, long delayTicks) {
        long delay = Math.max(1, delayTicks);
        if (folia == null) {
            return wrap(plugin.getServer().getScheduler().runTaskLater(plugin, task, delay));
        }
        return folia.runOnEntity(entity, task, delay);
    }

    public ChorusTask entityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(1, delayTicks);
        long period = Math.max(1, periodTicks);
        if (folia == null) {
            return wrap(plugin.getServer().getScheduler().runTaskTimer(plugin, task, delay, period));
        }
        return folia.repeatOnEntity(entity, task, delay, period);
    }

    /** Work on the blocks at a place, which may be a region this thread does not own. */
    public void region(Location where, Runnable task) {
        if (folia == null) {
            plugin.getServer().getScheduler().runTask(plugin, task);
            return;
        }
        folia.runAt(where, task);
    }

    public void shutdown() {
        if (folia == null) {
            plugin.getServer().getScheduler().cancelTasks(plugin);
            return;
        }
        folia.cancelAll();
    }

    /** For the startup line. */
    public String describe() {
        return folia == null ? "one server thread" : "Folia regions";
    }

    private static ChorusTask wrap(BukkitTask task) {
        return task::cancel;
    }

    /**
     * The reflective half. Every lookup happens once, on a server that turned out to be
     * Folia; a server that is not never builds one of these.
     */
    private static final class Folia {

        private final Plugin plugin;
        private final Object global;
        private final Object regional;

        private final Method globalRun;
        private final Method globalDelayed;
        private final Method globalRate;
        private final Method globalCancel;
        private final Method regionRun;
        private final Method entityScheduler;
        private final Method entityRun;
        private final Method entityDelayed;
        private final Method entityRate;
        private final Method cancel;

        private Folia(Plugin plugin, Class<?> task, Class<?> globalType, Class<?> regionType,
                      Class<?> entityType, Object global, Object regional)
                throws ReflectiveOperationException {
            this.plugin = plugin;
            this.global = global;
            this.regional = regional;

            this.globalRun = globalType.getMethod("run", Plugin.class, Consumer.class);
            this.globalDelayed = globalType.getMethod("runDelayed", Plugin.class, Consumer.class,
                    long.class);
            this.globalRate = globalType.getMethod("runAtFixedRate", Plugin.class, Consumer.class,
                    long.class, long.class);
            this.globalCancel = globalType.getMethod("cancelTasks", Plugin.class);
            this.regionRun = regionType.getMethod("run", Plugin.class, Location.class, Consumer.class);
            this.entityScheduler = Entity.class.getMethod("getScheduler");
            this.entityRun = entityType.getMethod("run", Plugin.class, Consumer.class,
                    Runnable.class, long.class);
            this.entityDelayed = entityType.getMethod("runDelayed", Plugin.class, Consumer.class,
                    Runnable.class, long.class);
            this.entityRate = entityType.getMethod("runAtFixedRate", Plugin.class, Consumer.class,
                    Runnable.class, long.class, long.class);
            this.cancel = task.getMethod("cancel");
        }

        static @Nullable Folia detect(Plugin plugin) {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            } catch (ClassNotFoundException ordinary) {
                return null;
            }
            try {
                String base = "io.papermc.paper.threadedregions.scheduler.";
                Class<?> task = Class.forName(base + "ScheduledTask");
                Class<?> globalType = Class.forName(base + "GlobalRegionScheduler");
                Class<?> regionType = Class.forName(base + "RegionScheduler");
                Class<?> entityType = Class.forName(base + "EntityScheduler");
                Object global = plugin.getServer().getClass()
                        .getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
                Object regional = plugin.getServer().getClass()
                        .getMethod("getRegionScheduler").invoke(plugin.getServer());
                return new Folia(plugin, task, globalType, regionType, entityType, global, regional);
            } catch (ReflectiveOperationException | RuntimeException unexpected) {
                plugin.getLogger().warning("This looks like Folia but its schedulers could not be "
                        + "reached, so everything will run on the server thread instead");
                return null;
            }
        }

        void runGlobal(Runnable job) {
            call(globalRun, global, plugin, consume(job));
        }

        ChorusTask runGlobalDelayed(Runnable job, long delay) {
            return handle(call(globalDelayed, global, plugin, consume(job), delay));
        }

        ChorusTask runGlobalRate(Runnable job, long delay, long period) {
            return handle(call(globalRate, global, plugin, consume(job), delay, period));
        }

        void runAt(Location where, Runnable job) {
            call(regionRun, regional, plugin, where, consume(job));
        }

        ChorusTask runOnEntity(Entity entity, Runnable job, long delay) {
            Object scheduler = call(entityScheduler, entity);
            if (scheduler == null) {
                return ChorusTask.NONE;
            }
            Object scheduled = delay <= 0
                    ? call(entityRun, scheduler, plugin, consume(job), null, 1L)
                    : call(entityDelayed, scheduler, plugin, consume(job), null, delay);
            return handle(scheduled);
        }

        ChorusTask repeatOnEntity(Entity entity, Runnable job, long delay, long period) {
            Object scheduler = call(entityScheduler, entity);
            if (scheduler == null) {
                return ChorusTask.NONE;
            }
            return handle(call(entityRate, scheduler, plugin, consume(job), null, delay, period));
        }

        void cancelAll() {
            call(globalCancel, global, plugin);
        }

        /** Folia hands the task itself to the job; nothing here wants it. */
        private static Consumer<Object> consume(Runnable job) {
            return ignored -> job.run();
        }

        private ChorusTask handle(@Nullable Object scheduled) {
            if (scheduled == null) {
                // The entity was removed between the lookup and the schedule.
                return ChorusTask.NONE;
            }
            return () -> call(cancel, scheduled);
        }

        private @Nullable Object call(Method method, Object target, Object... arguments) {
            try {
                return method.invoke(target, arguments);
            } catch (IllegalAccessException | InvocationTargetException failed) {
                plugin.getLogger().warning("Folia refused " + method.getName() + ": " + failed.getMessage());
                return null;
            }
        }
    }
}
