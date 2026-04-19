package biz.ugur.busroutebackend.payment.domain.valueobjects;

import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentValueObjectsTest {

    @Nested
    class MoneyValidation {

        @Test
        void rejectsZeroAmount() {
            PaymentValidationException ex = assertThrows(PaymentValidationException.class,
                    () -> Money.ofMinor(0, "TMT"));
            assertTrue(ex.getMessage().toLowerCase().contains("positive"));
        }

        @Test
        void rejectsNegativeAmount() {
            assertThrows(PaymentValidationException.class, () -> Money.ofMinor(-1, "TMT"));
        }

        @Test
        void rejectsMalformedCurrency() {
            assertThrows(PaymentValidationException.class, () -> Money.ofMinor(100, "TM"));
            assertThrows(PaymentValidationException.class, () -> Money.ofMinor(100, "TMTT"));
            assertThrows(PaymentValidationException.class, () -> Money.ofMinor(100, null));
            assertThrows(PaymentValidationException.class, () -> Money.ofMinor(100, "   "));
        }

        @Test
        void currencyIsUppercased() {
            assertEquals("USD", Money.ofMinor(100, "usd").getCurrency());
        }

        @Test
        void currencyIsTrimmed() {
            assertEquals("TMT", Money.ofMinor(100, "  TMT  ").getCurrency());
        }

        @Test
        void isoNumericCodesForKnownCurrencies() {
            assertEquals(934, Money.ofMinor(100, "TMT").getIso4217NumericCode());
            assertEquals(840, Money.ofMinor(100, "USD").getIso4217NumericCode());
            assertEquals(978, Money.ofMinor(100, "EUR").getIso4217NumericCode());
            assertEquals(643, Money.ofMinor(100, "RUB").getIso4217NumericCode());
        }

        @Test
        void isoNumericCodeThrowsForUnknown() {
            assertThrows(PaymentValidationException.class,
                    () -> Money.ofMinor(100, "JPY").getIso4217NumericCode());
        }

        @Test
        void toMajorUnitsScalesByHundred() {
            assertEquals(1.0, Money.ofMinor(100, "TMT").toMajorUnits());
            assertEquals(0.01, Money.ofMinor(1, "TMT").toMajorUnits());
            assertEquals(99.99, Money.ofMinor(9999, "USD").toMajorUnits(), 1e-9);
        }

        @Test
        void equalityBasedOnAmountAndCurrency() {
            Money a = Money.ofMinor(100, "TMT");
            Money b = Money.ofMinor(100, "tmt");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertNotEquals(a, Money.ofMinor(200, "TMT"));
            assertNotEquals(a, Money.ofMinor(100, "USD"));
        }
    }

    @Nested
    class OrderNumberValidation {

        @Test
        void generateProducesNonBlankValue() {
            OrderNumber n = OrderNumber.generate();
            assertNotNull(n.getValue());
            assertFalse(n.getValue().isBlank());
            assertEquals(32, n.getValue().length());
        }

        @Test
        void ofAcceptsValidValue() {
            assertEquals("ORDER-1", OrderNumber.of("ORDER-1").getValue());
        }

        @Test
        void ofTrimsInput() {
            assertEquals("abc", OrderNumber.of("  abc  ").getValue());
        }

        @Test
        void rejectsBlankInput() {
            assertThrows(PaymentValidationException.class, () -> OrderNumber.of(""));
            assertThrows(PaymentValidationException.class, () -> OrderNumber.of("   "));
            assertThrows(PaymentValidationException.class, () -> OrderNumber.of(null));
        }

        @Test
        void rejectsTooLongInput() {
            String tooLong = "x".repeat(33);
            assertThrows(PaymentValidationException.class, () -> OrderNumber.of(tooLong));
        }

        @Test
        void acceptsExactlyMaxLength() {
            String atMax = "x".repeat(32);
            assertEquals(atMax, OrderNumber.of(atMax).getValue());
        }

        @Test
        void equalityBasedOnValue() {
            assertEquals(OrderNumber.of("X-1"), OrderNumber.of("X-1"));
        }
    }

    @Nested
    class PaymentIdsAndSubjectType {

        @Test
        void paymentIdGenerateAndOf() {
            PaymentId id = PaymentId.generate();
            assertNotNull(id.getValue());
            assertEquals(36, id.getValue().length());
            assertEquals("abc", PaymentId.of("abc").getValue());
        }

        @Test
        void paymentIdEqualityBasedOnValue() {
            PaymentId a = PaymentId.of("x");
            assertEquals(a, PaymentId.of("x"));
            assertEquals(a.hashCode(), PaymentId.of("x").hashCode());
        }
    }
}
