package biz.ugur.busroutebackend.advertising.domain.valueobjects;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PriceTest {

    @Test
    void ofMinorStoresExactAmount() {
        Price p = Price.ofMinor(12345, "TMT");
        assertEquals(12345L, p.getAmountMinor());
        assertEquals("TMT", p.getCurrency());
        assertEquals(123.45, p.toMajorUnits(), 1e-9);
    }

    @Test
    void ofMajorRoundsToNearestMinor() {
        assertEquals(12345L, Price.ofMajor(123.45, "USD").getAmountMinor());
        assertEquals(12346L, Price.ofMajor(123.455, "USD").getAmountMinor());
        assertEquals(0L, Price.ofMajor(0.0, "USD").getAmountMinor());
    }

    @Test
    void currencyIsNormalisedToUppercaseAndTrimmed() {
        Price p = Price.ofMinor(100, "  usd  ");
        assertEquals("USD", p.getCurrency());
    }

    @Test
    void rejectsNegativeAmount() {
        AdvertisingValidationException ex = assertThrows(AdvertisingValidationException.class,
                () -> Price.ofMinor(-1, "TMT"));
        assertTrue(ex.getMessage().contains("non-negative"));
    }

    @Test
    void zeroIsAllowed() {
        assertEquals(0L, Price.ofMinor(0, "TMT").getAmountMinor());
    }

    @Test
    void rejectsWrongLengthCurrency() {
        assertThrows(AdvertisingValidationException.class, () -> Price.ofMinor(10, "US"));
        assertThrows(AdvertisingValidationException.class, () -> Price.ofMinor(10, "USDD"));
    }

    @Test
    void rejectsNullOrBlankCurrency() {
        assertThrows(AdvertisingValidationException.class, () -> Price.ofMinor(10, null));
        assertThrows(AdvertisingValidationException.class, () -> Price.ofMinor(10, "   "));
    }

    @Test
    void equalityComparesAmountAndCurrency() {
        Price a = Price.ofMinor(100, "TMT");
        Price b = Price.ofMinor(100, "tmt");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertNotEquals(a, Price.ofMinor(101, "TMT"));
        assertNotEquals(a, Price.ofMinor(100, "USD"));
    }

    @Test
    void toStringIncludesAmountAndCurrency() {
        Price p = Price.ofMinor(12345, "TMT");
        String s = p.toString();
        assertTrue(s.contains("TMT"));
        assertTrue(s.contains("12345"));
    }
}
