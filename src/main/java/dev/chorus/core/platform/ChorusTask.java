package dev.chorus.core.platform;

/** A scheduled job, whichever scheduler ended up running it. */
@FunctionalInterface
public interface ChorusTask {

    ChorusTask NONE = () -> {
    };

    void cancel();
}
