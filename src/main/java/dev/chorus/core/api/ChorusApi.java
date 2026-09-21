package dev.chorus.core.api;

import dev.chorus.core.economy.Economy;

import java.util.Optional;

/** What ChorusCore offers to other plugins. */
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
