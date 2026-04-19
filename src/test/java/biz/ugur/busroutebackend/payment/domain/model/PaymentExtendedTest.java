package biz.ugur.busroutebackend.payment.domain.model;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.events.PaymentFailedEvent;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentStateTransitionException;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExtendedTest {

    private static Payment registered() {
        return Payment.register(
                PaymentProvider.RYSGAL,
                PaymentSubjectType.AD_PLACEMENT,
                "placement-id",
                "business-id",
                Money.ofMinor(10_000, "TMT"),
                "https://example.com/return",
                LocalDateTime.now().plusMinutes(30));
    }

    @Nested
    class Validation {

        @Test
        void registerRejectsNullProvider() {
            assertThrows(PaymentValidationException.class, () -> Payment.register(
                    null, PaymentSubjectType.AD_PLACEMENT, "s", "b",
                    Money.ofMinor(100, "TMT"), "https://x", LocalDateTime.now()));
        }

        @Test
        void registerRejectsNullSubjectType() {
            assertThrows(PaymentValidationException.class, () -> Payment.register(
                    PaymentProvider.RYSGAL, null, "s", "b",
                    Money.ofMinor(100, "TMT"), "https://x", LocalDateTime.now()));
        }

        @Test
        void registerRejectsBlankSubjectId() {
            assertThrows(PaymentValidationException.class, () -> Payment.register(
                    PaymentProvider.RYSGAL, PaymentSubjectType.AD_PLACEMENT, " ", "b",
                    Money.ofMinor(100, "TMT"), "https://x", LocalDateTime.now()));
        }

        @Test
        void registerRejectsNullMoney() {
            assertThrows(PaymentValidationException.class, () -> Payment.register(
                    PaymentProvider.RYSGAL, PaymentSubjectType.AD_PLACEMENT, "s", "b",
                    null, "https://x", LocalDateTime.now()));
        }

        @Test
        void registerRejectsBlankReturnUrl() {
            assertThrows(PaymentValidationException.class, () -> Payment.register(
                    PaymentProvider.RYSGAL, PaymentSubjectType.AD_PLACEMENT, "s", "b",
                    Money.ofMinor(100, "TMT"), "   ", LocalDateTime.now()));
        }

        @Test
        void businessIdBecomesNullWhenBlank() {
            Payment p = Payment.register(
                    PaymentProvider.RYSGAL, PaymentSubjectType.AD_PLACEMENT, "s", "   ",
                    Money.ofMinor(100, "TMT"), "https://x", LocalDateTime.now());
            assertNull(p.getBusinessId());
        }

        @Test
        void attachProviderOrderRejectsBlankId() {
            Payment p = registered();
            assertThrows(PaymentValidationException.class,
                    () -> p.attachProviderOrder(" ", "url"));
        }
    }

    @Nested
    class Transitions {

        @Test
        void expireTransitionEmitsFailedEvent() {
            Payment p = registered();
            Payment expired = p.markExpired();
            assertEquals(PaymentStatus.EXPIRED, expired.getStatus());
            assertEquals("EXPIRED", expired.getFailureCode());
            assertNotNull(expired.getFailedAt());
            assertTrue(expired.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof PaymentFailedEvent));
        }

        @Test
        void cancelTransitionFromRegistered() {
            Payment cancelled = registered().markCancelled();
            assertEquals(PaymentStatus.CANCELLED, cancelled.getStatus());
            assertEquals("CANCELLED", cancelled.getFailureCode());
        }

        @Test
        void reversePathRequiresPreauthOrCompleted() {
            Payment registered = registered();
            assertThrows(PaymentStateTransitionException.class, registered::markReversed);

            Payment completed = registered.attachProviderOrder("ext", "url")
                    .markCompleted("pan", "exp", "name");
            Payment reversed = completed.markReversed();
            assertEquals(PaymentStatus.REVERSED, reversed.getStatus());
            assertEquals("REVERSED", reversed.getFailureCode());
        }

        @Test
        void terminalStatesCannotBeMarkedExpired() {
            Payment declined = registered().markDeclined("05", "no");
            assertThrows(PaymentStateTransitionException.class, declined::markExpired);
        }

        @Test
        void isCompletedReturnsTrueAfterMarkCompleted() {
            Payment completed = registered().attachProviderOrder("ext", "url")
                    .markCompleted("pan", "exp", "name");
            assertTrue(completed.isCompleted());
            assertTrue(completed.isTerminal());
        }
    }

    @Nested
    class StatusEnum {

        @Test
        void terminalStatesAreTerminal() {
            assertTrue(PaymentStatus.COMPLETED.isTerminal());
            assertTrue(PaymentStatus.DECLINED.isTerminal());
            assertTrue(PaymentStatus.REVERSED.isTerminal());
            assertTrue(PaymentStatus.REFUNDED.isTerminal());
            assertTrue(PaymentStatus.EXPIRED.isTerminal());
            assertTrue(PaymentStatus.CANCELLED.isTerminal());
        }

        @Test
        void nonTerminalStatesReturnFalseFromIsTerminal() {
            assertFalse(PaymentStatus.REGISTERED.isTerminal());
            assertFalse(PaymentStatus.PREAUTH.isTerminal());
        }

        @Test
        void transitionToSelfIsDisallowed() {
            for (PaymentStatus s : PaymentStatus.values()) {
                assertFalse(s.canTransitionTo(s), s.name());
            }
        }

        @Test
        void completedCanTransitionToRefundAndReverseOnly() {
            assertTrue(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.REFUNDED));
            assertTrue(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.REVERSED));
            assertFalse(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.DECLINED));
            assertFalse(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.REGISTERED));
        }

        @Test
        void preauthCanCompleteOrDeclineOrReverse() {
            assertTrue(PaymentStatus.PREAUTH.canTransitionTo(PaymentStatus.COMPLETED));
            assertTrue(PaymentStatus.PREAUTH.canTransitionTo(PaymentStatus.DECLINED));
            assertTrue(PaymentStatus.PREAUTH.canTransitionTo(PaymentStatus.REVERSED));
            assertFalse(PaymentStatus.PREAUTH.canTransitionTo(PaymentStatus.REGISTERED));
        }

        @Test
        void fromSvEpgUnknownCodeDefaultsToRegistered() {
            assertEquals(PaymentStatus.REGISTERED, PaymentStatus.fromSvEpgOrderStatus(42));
            assertEquals(PaymentStatus.REGISTERED, PaymentStatus.fromSvEpgOrderStatus(-1));
        }

        @Test
        void descriptionsPresentForAllValues() {
            for (PaymentStatus s : PaymentStatus.values()) {
                assertNotNull(s.getDescription());
                assertFalse(s.getDescription().isBlank(), s.name());
            }
        }
    }

    @Nested
    class MoneyAdditional {

        @Test
        void moneyAcceptsTmt() {
            Money m = Money.ofMinor(100, "tmt");
            assertEquals("TMT", m.getCurrency());
            assertEquals(100, m.getAmountMinor());
        }

        @Test
        void moneyRejectsUnknownCurrencyCode() {
            assertThrows(PaymentValidationException.class,
                    () -> Money.ofMinor(100, "XYZ").getIso4217NumericCode());
        }

        @Test
        void moneyToMajorUnits() {
            Money m = Money.ofMinor(12345, "USD");
            assertEquals(123.45, m.toMajorUnits(), 1e-9);
        }
    }
}
