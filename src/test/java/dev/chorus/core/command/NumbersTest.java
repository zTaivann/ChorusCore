package dev.chorus.core.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a typed number turns into, and what it never does. */
class NumbersTest {

    @Test
    void anAmountIsReadTheWayPeopleWriteIt() {
        assertEquals(5, Numbers.amount("5"));
        assertEquals(5.5, Numbers.amount("5.50"));
        assertEquals(5.5, Numbers.amount("5,50"));
        assertEquals(5, Numbers.amount("$5"));
        assertEquals(5, Numbers.amount("5€"));
    }

    @Test
    void aShortAmountIsMultipliedOut() {
        assertEquals(1500, Numbers.amount("1.5k"));
        assertEquals(2_000_000, Numbers.amount("2M"));
        assertEquals(3_000_000_000d, Numbers.amount("$3b"));
        assertEquals(1e12, Numbers.amount("1T"));
        assertEquals(5, Numbers.amount("5 k"), "a space in between is not a unit");
        assertEquals(5, Numbers.amount("5mil"), "a word is not a unit");
        assertEquals(5, Numbers.amount("5 coins"));
    }

    @Test
    void anythingThatIsNotAnAmountIsRefused() {
        for (String raw : List.of("-5", "NaN", "Infinity", "-Infinity", "1e999", "abc", "", "5.5.5")) {
            assertTrue(Numbers.amount(raw) < 0, raw);
        }
        assertTrue(Numbers.amount(null) < 0);
    }

    @Test
    void amountsAreKeptToHundredths() {
        assertEquals(1.24, Numbers.cents(1.236));
        assertEquals(0.3, Numbers.cents(0.1 + 0.2));
    }

    @Test
    void aDecimalIsAlwaysAFiniteNumber() {
        assertEquals(2.5, Numbers.decimal("2.5", -1));
        assertEquals(-1, Numbers.decimal("NaN", -1));
        assertEquals(-1, Numbers.decimal("Infinity", -1));
        assertEquals(-1, Numbers.decimal(null, -1));
    }

    @Test
    void aWholeNumberOrTheFallback() {
        assertEquals(7, Numbers.integer(" 7 ", -1));
        assertEquals(-1, Numbers.integer("7.5", -1));
        assertEquals(-1, Numbers.integer("99999999999", -1));
        assertEquals(99999999999L, Numbers.whole("99999999999", -1));
    }
}
