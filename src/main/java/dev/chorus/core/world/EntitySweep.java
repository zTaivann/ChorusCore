package dev.chorus.core.world;

import dev.chorus.core.platform.Schedulers;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** Counts or removes entities across whole worlds, a chunk at a time. */
public final class EntitySweep {

    private static final int CHUNK_BLOCKS = 16;
    private static final int MIDDLE = 8;
    private static final int SAMPLE_HEIGHT = 64;

    private EntitySweep() {
    }

    /**
     * @param remove false only counts.
     * @param onDone given the total once every chunk has answered, on whichever thread
     *               finished last.
     */
    public static void run(Schedulers schedulers, Collection<World> worlds,
                           Predicate<Entity> matches, boolean remove, IntConsumer onDone) {
        List<Chunk> chunks = new ArrayList<>();
        for (World world : worlds) {
            Collections.addAll(chunks, world.getLoadedChunks());
        }
        if (chunks.isEmpty()) {
            onDone.accept(0);
            return;
        }

        AtomicInteger total = new AtomicInteger();
        AtomicInteger waiting = new AtomicInteger(chunks.size());

        for (Chunk chunk : chunks) {
            schedulers.region(middleOf(chunk), () -> {
                int found = 0;
                for (Entity entity : chunk.getEntities()) {
                    if (matches.test(entity)) {
                        found++;
                        if (remove) {
                            entity.remove();
                        }
                    }
                }
                total.addAndGet(found);
                if (waiting.decrementAndGet() == 0) {
                    onDone.accept(total.get());
                }
            });
        }
    }

    private static Location middleOf(Chunk chunk) {
        return new Location(chunk.getWorld(),
                (chunk.getX() * CHUNK_BLOCKS) + MIDDLE,
                SAMPLE_HEIGHT,
                (chunk.getZ() * CHUNK_BLOCKS) + MIDDLE);
    }
}
