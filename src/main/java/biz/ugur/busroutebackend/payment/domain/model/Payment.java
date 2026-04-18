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
import biz.ugur.busroutebackend.payment.domain.valueobjects.OrderNumber;
import biz.ugur.busroutebackend.payment.domain.valueobjects.PaymentId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;


@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class Payment extends AggregateRoot<Payment, PaymentId> {

    private final PaymentId id;
    private final PaymentProvider provider;
    private final String providerOrderId;
    private final OrderNumber orderNumber;

    private final PaymentSubjectType subjectType;
    private final String subjectId;
    private final String businessId;

    private final Money money;

    private final PaymentStatus status;
    private final String formUrl;
    private final String returnUrl;

    private final LocalDateTime initiatedAt;
    private final LocalDateTime completedAt;
    private final LocalDateTime failedAt;
    private final LocalDateTime expiresAt;

    private final String failureCode;
    private final String failureMessage;

    private final String cardPanMasked;
    private final String cardExpiration;
    private final String cardholderName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    // -------------------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------------------

    public static Payment register(PaymentProvider provider,
                                    PaymentSubjectType subjectType,
                                    String subjectId,
                                    String businessId,
                                    Money money,
                                    String returnUrl,
                                    LocalDateTime expiresAt) {
        if (provider == null)     throw new PaymentValidationException("provider", "must not be null");
        if (subjectType == null)  throw new PaymentValidationException("subjectType", "must not be null");
        if (subjectId == null || subjectId.isBlank()) {
            throw new PaymentValidationException("subjectId", "must not be blank");
        }
        if (money == null)        throw new PaymentValidationException("amount", "must not be null");
        if (returnUrl == null || returnUrl.isBlank()) {
            throw new PaymentValidationException("returnUrl", "must not be blank");
        }

        Payment payment = builder()
                .id(PaymentId.generate())
                .provider(provider)
                .orderNumber(OrderNumber.generate())
                .subjectType(subjectType)
                .subjectId(subjectId.trim())
                .businessId(businessId != null && !businessId.isBlank() ? businessId.trim() : null)
                .money(money)
                .status(PaymentStatus.REGISTERED)
                .returnUrl(returnUrl.trim())
                .initiatedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .version(0L)
                .build();

        payment.registerEvent(new PaymentInitiatedEvent(
                payment.id.getValue(),
                provider,
                subjectType,
                subjectId.trim(),
                money.getAmountMinor(),
                money.getCurrency()));

        return payment;
    }

    public static Payment restore(PaymentId id,
                                    PaymentProvider provider,
                                    String providerOrderId,
                                    OrderNumber orderNumber,
                                    PaymentSubjectType subjectType,
                                    String subjectId,
                                    String businessId,
                                    Money money,
                                    PaymentStatus status,
                                    String formUrl,
                                    String returnUrl,
                                    LocalDateTime initiatedAt,
                                    LocalDateTime completedAt,
                                    LocalDateTime failedAt,
                                    LocalDateTime expiresAt,
                                    String failureCode,
                                    String failureMessage,
                                    String cardPanMasked,
                                    String cardExpiration,
                                    String cardholderName,
                                    LocalDateTime createdAt,
                                    LocalDateTime updatedAt,
                                    Long version) {
        return builder()
                .id(id)
                .provider(provider)
                .providerOrderId(providerOrderId)
                .orderNumber(orderNumber)
                .subjectType(subjectType)
                .subjectId(subjectId)
                .businessId(businessId)
                .money(money)
                .status(status)
                .formUrl(formUrl)
                .returnUrl(returnUrl)
                .initiatedAt(initiatedAt)
                .completedAt(completedAt)
                .failedAt(failedAt)
                .expiresAt(expiresAt)
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .cardPanMasked(cardPanMasked)
                .cardExpiration(cardExpiration)
                .cardholderName(cardholderName)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    // -------------------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------------------

    public Payment attachProviderOrder(String providerOrderId, String formUrl) {
        if (this.providerOrderId != null) {
            throw new PaymentValidationException("providerOrderId", "already attached");
        }
        if (providerOrderId == null || providerOrderId.isBlank()) {
            throw new PaymentValidationException("providerOrderId", "must not be blank");
        }
        return this.toBuilder()
                .providerOrderId(providerOrderId.trim())
                .formUrl(formUrl != null ? formUrl.trim() : null)
                .build();
    }

    public Payment markCompleted(String pan, String expiration, String cardholderName) {
        guardTransition(PaymentStatus.COMPLETED);
        Payment completed = this.toBuilder()
                .status(PaymentStatus.COMPLETED)
                .completedAt(LocalDateTime.now())
                .cardPanMasked(pan)
                .cardExpiration(expiration)
                .cardholderName(cardholderName)
                .build();
        completed.registerEvent(new PaymentCompletedEvent(
                this.id.getValue(),
                this.subjectType,
                this.subjectId,
                this.money.getAmountMinor(),
                this.money.getCurrency()));
        return completed;
    }

    public Payment markDeclined(String failureCode, String failureMessage) {
        return transitionToFailure(PaymentStatus.DECLINED, failureCode, failureMessage);
    }

    public Payment markExpired() {
        return transitionToFailure(PaymentStatus.EXPIRED, "EXPIRED", "Payment session timed out");
    }

    public Payment markCancelled() {
        return transitionToFailure(PaymentStatus.CANCELLED, "CANCELLED", "Payment cancelled by admin");
    }

    public Payment markReversed() {
        guardTransition(PaymentStatus.REVERSED);
        return this.toBuilder()
                .status(PaymentStatus.REVERSED)
                .failedAt(LocalDateTime.now())
                .failureCode("REVERSED")
                .failureMessage("Payment authorization reversed")
                .build();
    }

    public Payment markRefunded() {
        guardTransition(PaymentStatus.REFUNDED);
        Payment refunded = this.toBuilder()
                .status(PaymentStatus.REFUNDED)
                .build();
        refunded.registerEvent(new PaymentRefundedEvent(
                this.id.getValue(),
                this.subjectType,
                this.subjectId,
                this.money.getAmountMinor(),
                this.money.getCurrency()));
        return refunded;
    }

    public boolean isCompleted() { return status == PaymentStatus.COMPLETED; }
    public boolean isTerminal()  { return status.isTerminal(); }

    private Payment transitionToFailure(PaymentStatus target, String code, String message) {
        guardTransition(target);
        Payment failed = this.toBuilder()
                .status(target)
                .failedAt(LocalDateTime.now())
                .failureCode(code)
                .failureMessage(message)
                .build();
        failed.registerEvent(new PaymentFailedEvent(
                this.id.getValue(), target,
                this.subjectType, this.subjectId,
                code, message));
        return failed;
    }

    private void guardTransition(PaymentStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new PaymentStateTransitionException(this.status, target);
        }
    }

    // -------------------------------------------------------------------------------------
    // AggregateRoot contract
    // -------------------------------------------------------------------------------------

    @Override public PaymentId getId() { return id; }
    @Override public LocalDateTime getCreatedAt() { return createdAt; }
    @Override public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    @Override public Long getVersion() { return version; }
    @Override public void setVersion(Long version) { this.version = version; }
}
