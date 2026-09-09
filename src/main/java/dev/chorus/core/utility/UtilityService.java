package dev.chorus.core.utility;

public final class UtilityService {

    private volatile UtilitySettings settings;

    UtilityService(UtilitySettings settings) {
        this.settings = settings;
    }

    public UtilitySettings settings() {
        return settings;
    }

    void apply(UtilitySettings updated) {
        this.settings = updated;
    }
}
