package dev.chorus.core.command;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Counting a day's uses, and knowing when the day starts over. */
class DailyUsesTest {

    private static final LocalTime FOUR = LocalTime.of(4, 0);
    private static final UUID STEVE = UUID.randomUUID();

    private final Stored stored = new Stored();
    private final DailyUses uses = open(ZoneOffset.UTC);

    @Test
    void theDayBeginsAtTheResetTime() {
        assertEquals(at("2026-09-25T04:00:00Z"), uses.dayStart(at("2026-09-26T03:59:00Z"), FOUR));
        assertEquals(at("2026-09-26T04:00:00Z"), uses.dayStart(at("2026-09-26T04:00:00Z"), FOUR));
        assertEquals(at("2026-09-27T04:00:00Z"), uses.dayEnd(at("2026-09-26T04:00:00Z")));
    }

    /** The day the clocks go forward is an hour short, and the reset still lands on midnight. */
    @Test
    void aDayWhenTheClocksChangeIsNotTwentyFourHours() {
        DailyUses madrid = open(ZoneId.of("Europe/Madrid"));
        long start = madrid.dayStart(at("2026-03-29T12:00:00Z"), LocalTime.MIDNIGHT);

        assertEquals(Duration.ofHours(23).toMillis(), madrid.dayEnd(start) - start);
    }

    @Test
    void usesAreCountedWithinTheDay() {
        long day = at("2026-09-26T04:00:00Z");
        uses.add(STEVE, "home", day);
        uses.add(STEVE, "home", day);

        assertEquals(2, uses.used(STEVE, "home", day));
        assertEquals(0, uses.used(STEVE, "warp", day));
        assertEquals(0, uses.used(UUID.randomUUID(), "home", day));
    }

    @Test
    void aNewDayStartsFromZero() {
        long monday = at("2026-09-28T00:00:00Z");
        long tuesday = at("2026-09-29T00:00:00Z");
        uses.add(STEVE, "home", monday);
        uses.add(STEVE, "home", monday);

        assertEquals(0, uses.used(STEVE, "home", tuesday));
        uses.add(STEVE, "home", tuesday);
        assertEquals(1, uses.used(STEVE, "home", tuesday));
    }

    @Test
    void theCountSurvivesARestart() throws Exception {
        long today = System.currentTimeMillis() - Duration.ofHours(1).toMillis();
        uses.add(STEVE, "home", today);
        uses.add(STEVE, "home", today);

        DailyUses afterRestart = open(ZoneOffset.UTC);
        afterRestart.load();

        assertEquals(2, afterRestart.used(STEVE, "home", today));
    }

    @Test
    void daysLongGoneAreDropped() throws Exception {
        long lastWeek = System.currentTimeMillis() - Duration.ofDays(7).toMillis();
        uses.add(STEVE, "home", lastWeek);

        uses.sweep(System.currentTimeMillis());
        assertEquals(0, uses.used(STEVE, "home", lastWeek));

        open(ZoneOffset.UTC).load();
        assertEquals(List.of(), stored.findSince(0), "a restart clears them from the table too");
    }

    private DailyUses open(ZoneId zone) {
        return new DailyUses(stored, Runnable::run, Logger.getAnonymousLogger(), zone);
    }

    private static long at(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

    /** The table, kept in memory. */
    private static final class Stored implements DailyUseRepository {

        private final Map<String, Row> rows = new LinkedHashMap<>();

        @Override
        public void createTables() {
        }

        @Override
        public List<Row> findSince(long dayStart) {
            List<Row> found = new ArrayList<>();
            rows.values().stream().filter(row -> row.dayStart() >= dayStart).forEach(found::add);
            return found;
        }

        @Override
        public void save(Row row) {
            rows.put(row.owner() + " " + row.timer(), row);
        }

        @Override
        public int deleteBefore(long dayStart) {
            int before = rows.size();
            rows.values().removeIf(row -> row.dayStart() < dayStart);
            return before - rows.size();
        }
    }
}
