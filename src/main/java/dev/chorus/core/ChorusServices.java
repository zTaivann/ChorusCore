package dev.chorus.core;

import dev.chorus.core.api.ChorusApi;
import dev.chorus.core.api.HomeApi;
import dev.chorus.core.api.MessageApi;
import dev.chorus.core.api.SpawnApi;
import dev.chorus.core.api.TeleportApi;
import dev.chorus.core.api.WarpApi;
import dev.chorus.core.economy.Economy;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * What addons see. Modules hand their service over as they start, so a module that is
 * switched off simply leaves its slot empty and the API reports it absent.
 */
final class ChorusServices implements ChorusApi {

    private final String version;
    private final Economy economy;
    private final TeleportApi teleports;
    private final MessageApi messages;

    private volatile @Nullable HomeApi homes;
    private volatile @Nullable WarpApi warps;
    private volatile @Nullable SpawnApi spawns;

    ChorusServices(String version, Economy economy, TeleportApi teleports, MessageApi messages) {
        this.version = version;
        this.economy = economy;
        this.teleports = teleports;
        this.messages = messages;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public Optional<HomeApi> homes() {
        return Optional.ofNullable(homes);
    }

    @Override
    public Optional<WarpApi> warps() {
        return Optional.ofNullable(warps);
    }

    @Override
    public Optional<SpawnApi> spawns() {
        return Optional.ofNullable(spawns);
    }

    @Override
    public Economy economy() {
        return economy;
    }

    @Override
    public TeleportApi teleports() {
        return teleports;
    }

    @Override
    public MessageApi messages() {
        return messages;
    }

    void provide(HomeApi api) {
        this.homes = api;
    }

    void provide(WarpApi api) {
        this.warps = api;
    }

    void provide(SpawnApi api) {
        this.spawns = api;
    }
}
