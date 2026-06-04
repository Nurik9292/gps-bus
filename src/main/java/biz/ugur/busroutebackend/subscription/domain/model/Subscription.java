package biz.ugur.busroutebackend.subscription.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;
import biz.ugur.busroutebackend.subscription.domain.events.SubscriptionActivatedEvent;
import biz.ugur.busroutebackend.subscription.domain.events.SubscriptionCancelledEvent;
import biz.ugur.busroutebackend.subscription.domain.events.SubscriptionExpiredEvent;
import biz.ugur.busroutebackend.subscription.domain.events.SubscriptionInitiatedEvent;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionStateTransitionException;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionValidationException;
import biz.ugur.busroutebackend.subscription.domain.valueobjects.SubscriptionId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class Subscription extends AggregateRoot<Subscription, SubscriptionId> {

    private final SubscriptionId id;
    private final String clientId;
    private final SubscriptionPeriod period;
    private final SubscriptionStatus status;
    private final String paymentId;
    private final long amountMinor;
    private final String currency;

    private final LocalDateTime startedAt;
    private final LocalDateTime expiresAt;
    private final LocalDateTime cancelledAt;
    private final String cancellationReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static Subscription initiate(String clientId,
                                         SubscriptionPeriod period,
                                         long amountMinor,
                                         String currency) {
        if (clientId == null || clientId.isBlank()) {
            throw new SubscriptionValidationException("clientId", "must not be blank");
        }
        if (period == null) {
            throw new SubscriptionValidationException("period", "must not be null");
        }
        if (amountMinor <= 0) {
            throw new SubscriptionValidationException("amountMinor", "must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new SubscriptionValidationException("currency", "must not be blank");
        }

        Subscription subscription = builder()
                .id(SubscriptionId.generate())
                .clientId(clientId.trim())
                .period(period)
                .status(SubscriptionStatus.PENDING)
                .amountMinor(amountMinor)
                .currency(currency.trim().toUpperCase())
                .version(0L)
                .build();

        subscription.registerEvent(new SubscriptionInitiatedEvent(
                subscription.id.getValue(),
                subscription.clientId,
                period,
                amountMinor,
                subscription.currency));

        return subscription;
    }

    public static Subscription restore(SubscriptionId id,
                                        String clientId,
                                        SubscriptionPeriod period,
                                        SubscriptionStatus status,
                                        String paymentId,
                                        long amountMinor,
                                        String currency,
                                        LocalDateTime startedAt,
                                        LocalDateTime expiresAt,
                                        LocalDateTime cancelledAt,
                                        String cancellationReason,
                                        LocalDateTime createdAt,
                                        LocalDateTime updatedAt,
                                        Long version) {
        return builder()
                .id(id)
                .clientId(clientId)
                .period(period)
                .status(status)
                .paymentId(paymentId)
                .amountMinor(amountMinor)
                .currency(currency)
                .startedAt(startedAt)
                .expiresAt(expiresAt)
                .cancelledAt(cancelledAt)
                .cancellationReason(cancellationReason)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    public Subscription attachPayment(String paymentId) {
        if (this.paymentId != null) {
            throw new SubscriptionValidationException("paymentId", "already attached");
        }
        if (paymentId == null || paymentId.isBlank()) {
            throw new SubscriptionValidationException("paymentId", "must not be blank");
        }
        return this.toBuilder().paymentId(paymentId.trim()).build();
    }

    public Subscription activate(LocalDateTime now) {
        guardTransition(SubscriptionStatus.ACTIVE);
        LocalDateTime expires = now.plusDays(period.getDaysCount());
        Subscription activated = this.toBuilder()
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(now)
                .expiresAt(expires)
                .build();
        activated.registerEvent(new SubscriptionActivatedEvent(
                this.id.getValue(),
                this.clientId,
                this.period,
                now,
                expires));
        return activated;
    }

    public Subscription cancel(String reason) {
        guardTransition(SubscriptionStatus.CANCELLED);
        Subscription cancelled = this.toBuilder()
                .status(SubscriptionStatus.CANCELLED)
                .cancelledAt(LocalDateTime.now())
                .cancellationReason(reason)
                .build();
        cancelled.registerEvent(new SubscriptionCancelledEvent(
                this.id.getValue(),
                this.clientId,
                reason));
        return cancelled;
    }

    public Subscription expire() {
        guardTransition(SubscriptionStatus.EXPIRED);
        Subscription expired = this.toBuilder()
                .status(SubscriptionStatus.EXPIRED)
                .build();
        expired.registerEvent(new SubscriptionExpiredEvent(
                this.id.getValue(),
                this.clientId));
        return expired;
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }

    public boolean isPending() {
        return status == SubscriptionStatus.PENDING;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    private void guardTransition(SubscriptionStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new SubscriptionStateTransitionException(this.status, target);
        }
    }

    @Override public SubscriptionId getId() { return id; }
    @Override public LocalDateTime getCreatedAt() { return createdAt; }
    @Override public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    @Override public Long getVersion() { return version; }
    @Override public void setVersion(Long version) { this.version = version; }
}
