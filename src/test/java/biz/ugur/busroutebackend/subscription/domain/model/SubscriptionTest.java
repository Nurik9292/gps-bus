package biz.ugur.busroutebackend.subscription.domain.model;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;
import biz.ugur.busroutebackend.subscription.domain.events.SubscriptionActivatedEvent;
import biz.ugur.busroutebackend.subscription.domain.events.SubscriptionCancelledEvent;
import biz.ugur.busroutebackend.subscription.domain.events.SubscriptionInitiatedEvent;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionStateTransitionException;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {

    private static final String CLIENT_ID = "client-123";

    @Test
    void initiate_setsPendingStatus_andRaisesInitiatedEvent() {
        Subscription s = Subscription.initiate(CLIENT_ID, SubscriptionPeriod.YEARLY, 4000, "TMT");

        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(s.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(s.getPeriod()).isEqualTo(SubscriptionPeriod.YEARLY);
        assertThat(s.getAmountMinor()).isEqualTo(4000);
        assertThat(s.getCurrency()).isEqualTo("TMT");
        assertThat(s.getDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(SubscriptionInitiatedEvent.class);
    }

    @Test
    void initiate_blankClientId_throws() {
        assertThatThrownBy(() -> Subscription.initiate("  ", SubscriptionPeriod.MONTHLY, 400, "TMT"))
                .isInstanceOf(SubscriptionValidationException.class)
                .hasMessageContaining("clientId");
    }

    @Test
    void initiate_zeroAmount_throws() {
        assertThatThrownBy(() -> Subscription.initiate(CLIENT_ID, SubscriptionPeriod.MONTHLY, 0, "TMT"))
                .isInstanceOf(SubscriptionValidationException.class)
                .hasMessageContaining("amountMinor");
    }

    @Test
    void activate_setsActiveAndExpiresAtPlusPeriodDays_andRaisesActivatedEvent() {
        Subscription pending = Subscription.initiate(CLIENT_ID, SubscriptionPeriod.MONTHLY, 400, "TMT");
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 0);

        Subscription active = pending.activate(now);

        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(active.getStartedAt()).isEqualTo(now);
        assertThat(active.getExpiresAt()).isEqualTo(now.plusDays(30));
        assertThat(active.isActive()).isTrue();
        assertThat(active.getDomainEvents())
                .anyMatch(e -> e instanceof SubscriptionActivatedEvent);
    }

    @Test
    void cancel_fromPending_keepsCancellationReason_andRaisesCancelledEvent() {
        Subscription pending = Subscription.initiate(CLIENT_ID, SubscriptionPeriod.YEARLY, 4000, "TMT");

        Subscription cancelled = pending.cancel("payment declined");

        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("payment declined");
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getDomainEvents())
                .anyMatch(e -> e instanceof SubscriptionCancelledEvent);
    }

    @Test
    void activate_fromCancelled_throwsStateTransition() {
        Subscription cancelled = Subscription.initiate(CLIENT_ID, SubscriptionPeriod.MONTHLY, 400, "TMT")
                .cancel("user back-tap");

        assertThatThrownBy(() -> cancelled.activate(LocalDateTime.now()))
                .isInstanceOf(SubscriptionStateTransitionException.class);
    }

    @Test
    void attachPayment_twice_throws() {
        Subscription withPayment = Subscription.initiate(CLIENT_ID, SubscriptionPeriod.MONTHLY, 400, "TMT")
                .attachPayment("pay-001");

        assertThat(withPayment.getPaymentId()).isEqualTo("pay-001");
        assertThatThrownBy(() -> withPayment.attachPayment("pay-002"))
                .isInstanceOf(SubscriptionValidationException.class)
                .hasMessageContaining("already attached");
    }

    @Test
    void expire_fromActive_setsExpiredStatus() {
        Subscription active = Subscription.initiate(CLIENT_ID, SubscriptionPeriod.YEARLY, 4000, "TMT")
                .activate(LocalDateTime.now());

        Subscription expired = active.expire();

        assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(expired.isTerminal()).isTrue();
    }
}
