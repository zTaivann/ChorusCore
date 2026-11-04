package dev.chorus.core.command;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Prices paid on use, held until the action finishes or is cancelled. */
class PrepaidPricesTest {

    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final PrepaidPrices prepaid = new PrepaidPrices();

    @Test
    void nothingPaidIsNothingToTake() {
        assertNull(prepaid.take(steve, "home"));
    }

    @Test
    void aPriceIsTakenOnce() {
        prepaid.add(steve, "home", paid(10));

        assertEquals(paid(10), prepaid.take(steve, "home"));
        assertNull(prepaid.take(steve, "home"));
    }

    /** A second /home during the warmup of the first: the first cancel takes the first price. */
    @Test
    void theOldestPriceComesOutFirst() {
        prepaid.add(steve, "warp:shop", paid(5));
        prepaid.add(steve, "warp:shop", paid(8));

        assertEquals(paid(5), prepaid.take(steve, "warp:shop"));
        assertEquals(paid(8), prepaid.take(steve, "warp:shop"));
    }

    @Test
    void itemsAreKeptWithTheMoney() {
        PrepaidPrices.Payment both =
                new PrepaidPrices.Payment(10, new ItemPrice(Material.DIAMOND, 2));
        prepaid.add(steve, "home", both);

        assertEquals(both, prepaid.take(steve, "home"));
    }

    @Test
    void playersAndCommandsAreKeptApart() {
        prepaid.add(steve, "home", paid(10));
        prepaid.add(alex, "spawn", paid(3));

        assertNull(prepaid.take(alex, "home"));
        assertNull(prepaid.take(steve, "spawn"));
        assertEquals(paid(3), prepaid.take(alex, "spawn"));
        assertEquals(paid(10), prepaid.take(steve, "home"));
    }

    private static PrepaidPrices.Payment paid(double money) {
        return new PrepaidPrices.Payment(money, ItemPrice.NONE);
    }
}
