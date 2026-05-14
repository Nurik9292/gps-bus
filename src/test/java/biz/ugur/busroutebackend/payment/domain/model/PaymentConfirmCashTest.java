package biz.ugur.busroutebackend.payment.domain.model;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.events.PaymentCompletedEvent;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentStateTransitionException;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentConfirmCashTest {

    private static Payment newCashPayment() {
        return Payment.register(
                PaymentProvider.CASH,
                PaymentSubjectType.AD_PLACEMENT,
                "placement-id",
                "business-id",
                Money.ofMinor(50_000, "TMT"),
                "https://example.com/return",
                LocalDateTime.now().plusHours(1));
    }

    @Test
    void confirmCash_movesRegisteredCashToCompleted() {
        Payment p = newCashPayment();
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 14, 15, 30, 0);

        Payment confirmed = p.confirmCash("admin_user", confirmedAt);

        assertEquals(PaymentStatus.COMPLETED, confirmed.getStatus());
    }

    @Test
    void confirmCash_setsCompletedAtAndCompletedBy() {
        Payment p = newCashPayment();
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 14, 15, 30, 0);

        Payment confirmed = p.confirmCash("admin_user", confirmedAt);

        assertEquals(confirmedAt, confirmed.getCompletedAt());
        assertEquals("admin_user", confirmed.getCompletedBy());
    }

    @Test
    void confirmCash_emitsPaymentCompletedEvent() {
        Payment p = newCashPayment();

        Payment confirmed = p.confirmCash("admin_user", LocalDateTime.now());

        assertTrue(confirmed.getDomainEvents().stream()
                .anyMatch(e -> e instanceof PaymentCompletedEvent));
    }

    @Test
    void confirmCash_throwsIfProviderIsNotCash() {
        Payment p = Payment.register(
                PaymentProvider.RYSGAL,
                PaymentSubjectType.AD_PLACEMENT,
                "placement-id",
                "business-id",
                Money.ofMinor(50_000, "TMT"),
                "https://example.com/return",
                LocalDateTime.now().plusHours(1));

        PaymentValidationException ex = assertThrows(
                PaymentValidationException.class,
                () -> p.confirmCash("admin_user", LocalDateTime.now()));
        assertTrue(ex.getMessage().toLowerCase().contains("cash"));
    }

    @Test
    void confirmCash_throwsIfNotInRegisteredStatus() {
        Payment p = newCashPayment()
                .confirmCash("admin_user", LocalDateTime.of(2026, 5, 14, 13, 0, 0));

        assertThrows(
                PaymentStateTransitionException.class,
                () -> p.confirmCash("admin_user", LocalDateTime.now()));
    }

}
