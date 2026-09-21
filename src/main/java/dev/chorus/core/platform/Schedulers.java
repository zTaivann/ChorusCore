package dev.chorus.core.platform;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Runs work on the thread that is allowed to touch what it is about to touch. */
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

        private Folia(Plugin plugin, ClassLoader api) throws ReflectiveOperationException {
            this.plugin = plugin;

            this.globalRun = FoliaCall.GLOBAL_RUN.on(api);
            this.globalDelayed = FoliaCall.GLOBAL_DELAYED.on(api);
            this.globalRate = FoliaCall.GLOBAL_RATE.on(api);
            this.globalCancel = FoliaCall.GLOBAL_CANCEL.on(api);
            this.regionRun = FoliaCall.REGION_RUN.on(api);
            this.entityScheduler = FoliaCall.ENTITY_SCHEDULER.on(api);
            this.entityRun = FoliaCall.ENTITY_RUN.on(api);
            this.entityDelayed = FoliaCall.ENTITY_DELAYED.on(api);
            this.entityRate = FoliaCall.ENTITY_RATE.on(api);
            this.cancel = FoliaCall.TASK_CANCEL.on(api);

            this.global = FoliaCall.SERVER_GLOBAL.on(api).invoke(plugin.getServer());
            this.regional = FoliaCall.SERVER_REGION.on(api).invoke(plugin.getServer());
        }

        /** Null on an ordinary server. On Folia it either works or it throws. */
        static @Nullable Folia detect(Plugin plugin) {
            ClassLoader api = Schedulers.class.getClassLoader();
            try {
                Class.forName(FoliaCall.Api.FOLIA_MARKER, false, api);
            } catch (ClassNotFoundException ordinary) {
                return null;
            }

            try {
                return new Folia(plugin, api);
            } catch (ReflectiveOperationException | RuntimeException unexpected) {
                // Never a fall back to the Bukkit scheduler: on Folia every method throws.
                throw new IllegalStateException(
                        "This is Folia, but its schedulers could not be reached. The plugin "
                                + "cannot run safely without them; please report this.",
                        unexpected);
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
                    ? call(entityRun, scheduler, plugin, consume(job), null)
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
                // The cause, not the wrapper: an InvocationTargetException says nothing on its own.
                Throwable cause = failed instanceof InvocationTargetException wrapped
                        && wrapped.getCause() != null ? wrapped.getCause() : failed;
                plugin.getLogger().log(Level.WARNING,
                        "Folia refused " + method.getName(), cause);
                return null;
            }
        }
    }
}
