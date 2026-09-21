package dev.chorus.core.players;

import dev.chorus.core.economy.BalanceRepository;
import dev.chorus.core.economy.SqlBalanceRepository;
import dev.chorus.core.shops.chest.ChestShop;
import dev.chorus.core.shops.chest.ChestShopRepository;
import dev.chorus.core.shops.chest.SqlChestShopRepository;
import dev.chorus.core.storage.TempStorage;
import dev.chorus.core.utility.powertool.PowertoolRepository;
import dev.chorus.core.utility.powertool.SqlPowertoolRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The purge deletes, so every one of these is about what it leaves behind. */
class PlayerPurgeTest {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_700_000_000_000L;
    private static final long CUTOFF = NOW - 90 * DAY;

    private final UUID away = UUID.randomUUID();
    private final UUID here = UUID.randomUUID();

    private TempStorage storage;
    private PlayerPurge purge;
    private PlayerProfileRepository profiles;
    private BalanceRepository balances;
    private PowertoolRepository powertools;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        purge = new PlayerPurge(storage);

        profiles = new SqlPlayerProfileRepository(storage);
        balances = new SqlBalanceRepository(storage);
        powertools = new SqlPowertoolRepository(storage);
        profiles.createTables();
        balances.createTables();
        powertools.createTables();

        profiles.seen(away, "Away", "1.2.3.4", NOW - 200 * DAY);
        profiles.seen(here, "Here", "1.2.3.4", NOW - 1 * DAY);
        balances.save(away, "Away", 500);
        balances.save(here, "Here", 500);
        powertools.save(away, "stick", List.of("heal"));
        powertools.save(here, "stick", List.of("heal"));
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void aTableThatWasNeverMadeIsNotAnError() throws IOException, SQLException {
        // Homes, mail and the rest belong to modules that may never have been switched on.
        try (TempStorage bare = TempStorage.open()) {
            assertEquals(0, new PlayerPurge(bare).run(CUTOFF, Set.of(), true).players());
        }
    }

    @Test
    void theCheckPassDeletesNothing() throws SQLException {
        PlayerPurge.Report report = purge.run(CUTOFF, Set.of(), false);

        assertEquals(1, report.players());
        assertTrue(report.rows() >= 3, "the profile, the balance and the powertool");
        assertNotNull(profiles.find(away), "a check pass must leave the player alone");
        assertEquals(2, balances.all().size());
    }

    @Test
    void onlyTheOnesWhoHaveBeenAwayGo() throws SQLException {
        purge.run(CUTOFF, Set.of(), true);

        assertNull(profiles.find(away));
        assertNotNull(profiles.find(here), "somebody seen yesterday must survive");
    }

    @Test
    void everythingOfTheirsGoesWithThem() throws SQLException {
        purge.run(CUTOFF, Set.of(), true);

        assertEquals(List.of(here), balances.all().stream()
                .map(BalanceRepository.Account::player).toList());
        assertEquals(Map.of(), powertools.findAll(away));
        assertEquals(1, powertools.findAll(here).size());
    }

    @Test
    void somebodyOnlineIsNeverTouched() throws SQLException {
        PlayerPurge.Report report = purge.run(CUTOFF, Set.of(away), true);

        assertEquals(0, report.players());
        assertNotNull(profiles.find(away));
    }

    /** Their row is the only thing standing behind a chest and a sign in the world. */
    @Test
    void aShopOwnerIsLeftAloneInFull() throws SQLException {
        ChestShopRepository shops = new SqlChestShopRepository(storage);
        shops.createTables();
        shops.save(new ChestShop(0, away, "Away", "world", 1, 64, 1, "", 5, true, false, NOW));

        PlayerPurge.Report report = purge.run(CUTOFF, Set.of(), true);

        assertEquals(0, report.players());
        assertEquals(1, report.shopKeep());
        assertNotNull(profiles.find(away));
        assertEquals(2, balances.all().size(), "nothing of theirs should have gone either");
    }

    @Test
    void nobodyToForgetIsNotAFailure() throws SQLException {
        PlayerPurge.Report report = purge.run(NOW - 1000 * DAY, Set.of(), true);

        assertEquals(0, report.players());
        assertEquals(0, report.rows());
        assertFalse(report.foundAnything());
    }
}
