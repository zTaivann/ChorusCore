package dev.chorus.core.economy;

public final class EconomyService {

    private volatile EconomySettings settings;

    EconomyService(EconomySettings settings) {
        this.settings = settings;
    }

    public EconomySettings settings() {
        return settings;
    }

    void apply(EconomySettings updated) {
        this.settings = updated;
    }
}
