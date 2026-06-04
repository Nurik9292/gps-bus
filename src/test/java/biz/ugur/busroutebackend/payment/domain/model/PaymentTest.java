package biz.ugur.busroutebackend.payment.domain.model;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.events.PaymentCompletedEvent;
import biz.ugur.busroutebackend.payment.domain.events.PaymentFailedEvent;
import biz.ugur.busroutebackend.payment.domain.events.PaymentInitiatedEvent;
import biz.ugur.busroutebackend.payment.domain.events.PaymentRefundedEvent;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentStateTransitionException;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentTest {

    @Test
    @DisplayName("register creates REGISTERED payment and emits PaymentInitiatedEvent")
    void registerEmitsInitiatedEvent() {
        Payment p = registered();

        assertEquals(PaymentStatus.REGISTERED, p.getStatus());
        assertNotNull(p.getOrderNumber());
        assertNotNull(p.getInitiatedAt());
        assertEquals(1, p.getDomainEvents().size());
        assertInstanceOf(PaymentInitiatedEvent.class, p.getDomainEvents().get(0));
    }

    @Test
    @DisplayName("register without failUrl leaves it null")
    void registerWithoutFailUrlIsNull() {
        assertEquals(null, registered().getFailUrl());
    }

    @Test
    @DisplayName("register with failUrl stores trimmed value; blank becomes null")
    void registerWithFailUrl() {
        Payment withFail = Payment.register(
                PaymentProvider.HALK,
                PaymentSubjectType.CLIENT_SUBSCRIPTION,
                "sub-id", null,
                Money.ofMinor(400, "TMT"),
                "https://api.duralga.tm/api/v1/payments/return/HALK",
                "  https://api.duralga.tm/api/v1/payments/return/HALK  ",
                LocalDateTime.now().plusMinutes(30));
        assertEquals("https://api.duralga.tm/api/v1/payments/return/HALK", withFail.getFailUrl());

        Payment blankFail = Payment.register(
                PaymentProvider.HALK,
                PaymentSubjectType.CLIENT_SUBSCRIPTION,
                "sub-id", null,
                Money.ofMinor(400, "TMT"),
                "https://api.duralga.tm/api/v1/payments/return/HALK",
                "   ",
                LocalDateTime.now().plusMinutes(30));
        assertEquals(null, blankFail.getFailUrl());
    }

    @Test
    @DisplayName("attachProviderOrder fails when already attached")
    void attachProviderOrderIsOneShot() {
        Payment p = registered().attachProviderOrder("a", "url");
        assertThrows(PaymentValidationException.class,
                () -> p.attachProviderOrder("b", "url2"));
    }

    @Test
    @DisplayName("markCompleted transitions REGISTERED → COMPLETED and emits event")
    void completeFromRegistered() {
        Payment p = registered().attachProviderOrder("ext", "url");

        Payment completed = p.markCompleted("4111********1111", "202512", "TEST");

        assertEquals(PaymentStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        assertEquals("4111********1111", completed.getCardPanMasked());
        assertTrue(completed.getDomainEvents().stream()
                .anyMatch(e -> e instanceof PaymentCompletedEvent));
    }

    @Test
    @DisplayName("cannot complete a DECLINED payment")
    void declinedCannotComplete() {
        Payment declined = registered().markDeclined("05", "Do not honor");
        assertThrows(PaymentStateTransitionException.class,
                () -> declined.markCompleted("x", "y", "z"));
    }

    @Test
    @DisplayName("markDeclined is terminal and emits PaymentFailedEvent")
    void declineEmitsFailedEvent() {
        Payment p = registered();
        Payment declined = p.markDeclined("05", "Do not honor");

        assertEquals(PaymentStatus.DECLINED, declined.getStatus());
        assertTrue(declined.isTerminal());
        assertTrue(declined.getDomainEvents().stream()
                .anyMatch(e -> e instanceof PaymentFailedEvent));
    }

    @Test
    @DisplayName("refund is allowed only from COMPLETED and emits PaymentRefundedEvent")
    void refundOnlyFromCompleted() {
        Payment registered = registered();
        assertThrows(PaymentStateTransitionException.class, registered::markRefunded);

        Payment completed = registered.attachProviderOrder("e", "u")
                .markCompleted("4111", "2025", "T");
        Payment refunded = completed.markRefunded();

        assertEquals(PaymentStatus.REFUNDED, refunded.getStatus());
        assertTrue(refunded.getDomainEvents().stream()
                .anyMatch(e -> e instanceof PaymentRefundedEvent));
    }

    @Test
    @DisplayName("Money requires a positive amount and valid currency")
    void moneyValidation() {
        assertThrows(PaymentValidationException.class, () -> Money.ofMinor(0, "TMT"));
        assertThrows(PaymentValidationException.class, () -> Money.ofMinor(-1, "TMT"));
        assertThrows(PaymentValidationException.class, () -> Money.ofMinor(100, "TM"));
    }

    @Test
    @DisplayName("Money.getIso4217NumericCode maps TMT/USD/EUR/RUB")
    void moneyCurrencyCodes() {
        assertEquals(934, Money.ofMinor(100, "TMT").getIso4217NumericCode());
        assertEquals(840, Money.ofMinor(100, "USD").getIso4217NumericCode());
        assertEquals(978, Money.ofMinor(100, "EUR").getIso4217NumericCode());
        assertEquals(643, Money.ofMinor(100, "RUB").getIso4217NumericCode());
    }

    @Test
    @DisplayName("PaymentStatus.fromSvEpgOrderStatus maps all known codes")
    void mapSvEpgOrderStatus() {
        assertEquals(PaymentStatus.REGISTERED, PaymentStatus.fromSvEpgOrderStatus(0));
        assertEquals(PaymentStatus.PREAUTH,    PaymentStatus.fromSvEpgOrderStatus(1));
        assertEquals(PaymentStatus.COMPLETED,  PaymentStatus.fromSvEpgOrderStatus(2));
        assertEquals(PaymentStatus.REVERSED,   PaymentStatus.fromSvEpgOrderStatus(3));
        assertEquals(PaymentStatus.REFUNDED,   PaymentStatus.fromSvEpgOrderStatus(4));
        assertEquals(PaymentStatus.REGISTERED, PaymentStatus.fromSvEpgOrderStatus(5));
        assertEquals(PaymentStatus.DECLINED,   PaymentStatus.fromSvEpgOrderStatus(6));
    }

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
}
