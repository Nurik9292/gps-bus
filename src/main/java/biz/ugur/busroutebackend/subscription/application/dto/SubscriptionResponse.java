package biz.ugur.busroutebackend.subscription.application.dto;

import biz.ugur.busroutebackend.client.application.dto.ClientSummary;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record SubscriptionResponse(
        @JsonProperty("subscription_id")    String subscriptionId,
        @JsonProperty("client_id")          String clientId,
        @JsonProperty("period")             String period,
        @JsonProperty("status")             String status,
        @JsonProperty("payment_id")         String paymentId,
        @JsonProperty("amount_minor")       long amountMinor,
        @JsonProperty("currency")           String currency,
        @JsonProperty("started_at")         LocalDateTime startedAt,
        @JsonProperty("expires_at")         LocalDateTime expiresAt,
        @JsonProperty("cancelled_at")       LocalDateTime cancelledAt,
        @JsonProperty("cancellation_reason") String cancellationReason,
        @JsonProperty("active")             boolean active,
        @JsonProperty("payment_status")     String paymentStatus,
        @JsonProperty("last_transaction")   TransactionSummary lastTransaction,
        @JsonProperty("client")             ClientSummary client
) {
    public static SubscriptionResponse fromDomain(Subscription subscription) {
        return fromDomain(subscription, null, null);
    }

    public static SubscriptionResponse fromDomain(Subscription subscription,
                                                  String paymentStatus,
                                                  TransactionSummary lastTransaction) {
        return new SubscriptionResponse(
                subscription.getId().getValue(),
                subscription.getClientId(),
                subscription.getPeriod().name(),
                subscription.getStatus().name(),
                subscription.getPaymentId(),
                subscription.getAmountMinor(),
                subscription.getCurrency(),
                subscription.getStartedAt(),
                subscription.getExpiresAt(),
                subscription.getCancelledAt(),
                subscription.getCancellationReason(),
                subscription.isActive(),
                paymentStatus,
                lastTransaction,
                null
        );
    }

    public SubscriptionResponse withClient(ClientSummary client) {
        return new SubscriptionResponse(
                subscriptionId, clientId, period, status, paymentId, amountMinor, currency,
                startedAt, expiresAt, cancelledAt, cancellationReason, active,
                paymentStatus, lastTransaction, client
        );
    }
}
