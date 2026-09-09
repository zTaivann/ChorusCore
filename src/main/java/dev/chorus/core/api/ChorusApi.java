package dev.chorus.core.api;

import dev.chorus.core.economy.Economy;

import java.util.Optional;

/**
 * What ChorusCore offers to other plugins.
 *
 * <p>Get hold of it through {@link ChorusProvider}. Every part that belongs to a module is
 * an {@link Optional}, because any module can be switched off in its config file and an
 * addon has to cope with that rather than crash.
 *
 * <p>The interfaces here are the only thing promised to stay stable. Everything else in the
 * plugin is free to change between versions.
 */
public interface ChorusApi {

    /** The plugin version, for an addon that wants to check what it is talking to. */
    String version();

    Optional<HomeApi> homes();

    Optional<WarpApi> warps();

    Optional<SpawnApi> spawns();

    /** Never absent: reports itself disabled when the server has no economy. */
    Economy economy();

    TeleportApi teleports();

    MessageApi messages();
}
