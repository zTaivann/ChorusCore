package dev.chorus.core.economy;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ledger the plugin keeps: who /baltop shows, and what a new account starts with. */
class LedgerTest {

    private final Accounts stored = new Accounts();
    private final UUID rich = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private final UUID poor = UUID.randomUUID();

    @Test
    void theRichestComeFirstAndAHiddenPlayerIsLeftOut() throws Exception {
        Balances ledger = ledger(List.of(
                new BalanceRepository.Account(rich, "Rich", 500, false),
                new BalanceRepository.Account(staff, "Staff", 9000, true),
                new BalanceRepository.Account(poor, "Poor", 5, false)));

        assertEquals(List.of("Rich", "Poor"), names(ledger));
        assertEquals(505, ledger.total(), 1e-9);
    }

    @Test
    void hidingIsRememberedAndShowsAtOnce() throws Exception {
        Balances ledger = ledger(List.of(
                new BalanceRepository.Account(rich, "Rich", 500, false),
                new BalanceRepository.Account(staff, "Staff", 9000, false)));
        assertEquals(List.of("Staff", "Rich"), names(ledger));

        ledger.hide(staff, true);

        assertEquals(List.of("Rich"), names(ledger));
        assertTrue(stored.hidden.get(staff));
    }

    @Test
    void aPlayerWithNoAccountCannotBeHidden() throws Exception {
        Balances ledger = ledger(List.of());
        ledger.hide(staff, true);

        assertTrue(stored.hidden.isEmpty());
    }

    @Test
    void aNewAccountStartsWithTheBalanceItIsGivenAndAnOldOneKeepsItsOwn() throws Exception {
        Balances ledger = ledger(List.of(new BalanceRepository.Account(rich, "Rich", 500, false)));

        ledger.open(poor, "Poor", 250);
        ledger.open(rich, "Rich", 250);

        assertEquals(250, ledger.of(poor), 1e-9);
        assertEquals(500, ledger.of(rich), 1e-9);
    }

    private Balances ledger(List<BalanceRepository.Account> accounts) throws Exception {
        stored.accounts.addAll(accounts);
        Balances ledger = new Balances(stored, Runnable::run, Logger.getLogger("test"),
                Currency.read(new MemoryConfiguration()));
        ledger.load();
        ledger.rankingSeconds(0);
        return ledger;
    }

    private static List<String> names(Balances ledger) {
        return ledger.ranking().stream().map(Balances.Ranked::name).toList();
    }

    /** The table, without a database under it. */
    private static final class Accounts implements BalanceRepository {

        final List<Account> accounts = new ArrayList<>();
        final Map<UUID, Boolean> hidden = new ConcurrentHashMap<>();

        @Override
        public void createTables() {
        }

        @Override
        public List<Account> all() {
            return List.copyOf(accounts);
        }

        @Override
        public void save(UUID player, String name, double balance) {
        }

        @Override
        public void hide(UUID player, boolean hide) {
            hidden.put(player, hide);
        }

        @Override
        public void delete(UUID player) {
        }
    }
}
