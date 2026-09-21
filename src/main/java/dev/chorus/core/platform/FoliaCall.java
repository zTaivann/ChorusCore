package dev.chorus.core.platform;

import java.lang.reflect.Method;
import java.util.List;

/** Every Folia method the scheduler bridge reaches for, written down as data. */
enum FoliaCall {

    GLOBAL_RUN(Api.GLOBAL, "run", Api.PLUGIN, Api.CONSUMER),
    GLOBAL_DELAYED(Api.GLOBAL, "runDelayed", Api.PLUGIN, Api.CONSUMER, Api.LONG),
    GLOBAL_RATE(Api.GLOBAL, "runAtFixedRate", Api.PLUGIN, Api.CONSUMER, Api.LONG, Api.LONG),
    GLOBAL_CANCEL(Api.GLOBAL, "cancelTasks", Api.PLUGIN),

    REGION_RUN(Api.REGION, "run", Api.PLUGIN, Api.LOCATION, Api.CONSUMER),

    /** On the entity itself, which is where Folia hangs its scheduler. */
    ENTITY_SCHEDULER(Api.ENTITY_TYPE, "getScheduler"),

    /** Three arguments. The one without a delay, which is what separates it from the next. */
    ENTITY_RUN(Api.ENTITY, "run", Api.PLUGIN, Api.CONSUMER, Api.RUNNABLE),
    ENTITY_DELAYED(Api.ENTITY, "runDelayed", Api.PLUGIN, Api.CONSUMER, Api.RUNNABLE, Api.LONG),
    ENTITY_RATE(Api.ENTITY, "runAtFixedRate",
            Api.PLUGIN, Api.CONSUMER, Api.RUNNABLE, Api.LONG, Api.LONG),

    TASK_CANCEL(Api.TASK, "cancel"),

    SERVER_GLOBAL(Api.SERVER, "getGlobalRegionScheduler"),
    SERVER_REGION(Api.SERVER, "getRegionScheduler");

    /** The names, in a class of their own because an enum may not look forward at its own. */
    static final class Api {

        private static final String SCHEDULERS = "io.papermc.paper.threadedregions.scheduler.";

        static final String GLOBAL = SCHEDULERS + "GlobalRegionScheduler";
        static final String REGION = SCHEDULERS + "RegionScheduler";
        static final String ENTITY = SCHEDULERS + "EntityScheduler";
        static final String TASK = SCHEDULERS + "ScheduledTask";
        static final String ENTITY_TYPE = "org.bukkit.entity.Entity";
        static final String SERVER = "org.bukkit.Server";

        /** The class that only exists on Folia, which is how it is told apart from Paper. */
        static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

        private static final String PLUGIN = "org.bukkit.plugin.Plugin";
        private static final String CONSUMER = "java.util.function.Consumer";
        private static final String RUNNABLE = "java.lang.Runnable";
        private static final String LOCATION = "org.bukkit.Location";
        private static final String LONG = "long";

        private Api() {
        }
    }

    private final String owner;
    private final String method;
    private final List<String> parameters;

    FoliaCall(String owner, String method, String... parameters) {
        this.owner = owner;
        this.method = method;
        this.parameters = List.of(parameters);
    }

    /** Looks this one up against whichever API the class loader holds. */
    Method on(ClassLoader api) throws ReflectiveOperationException {
        Class<?>[] types = new Class<?>[parameters.size()];
        for (int at = 0; at < types.length; at++) {
            types[at] = type(parameters.get(at), api);
        }
        return Class.forName(owner, false, api).getMethod(method, types);
    }

    @Override
    public String toString() {
        return owner + "#" + method + "(" + String.join(", ", parameters) + ")";
    }

    private static Class<?> type(String name, ClassLoader api) throws ClassNotFoundException {
        return Api.LONG.equals(name) ? long.class : Class.forName(name, false, api);
    }
}
