package biz.ugur.busroutebackend.payment.infrastructure.mapper;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
import biz.ugur.busroutebackend.payment.domain.valueobjects.OrderNumber;
import biz.ugur.busroutebackend.payment.domain.valueobjects.PaymentId;
import biz.ugur.busroutebackend.payment.infrastructure.persistence.entity.PaymentEntity;

public final class PaymentMapper {

    private PaymentMapper() {}

    public static Payment toDomain(PaymentEntity e) {
        return Payment.restore(
                PaymentId.of(e.getId()),
                PaymentProvider.valueOf(e.getProvider()),
                e.getProviderOrderId(),
                OrderNumber.of(e.getOrderNumber()),
                PaymentSubjectType.valueOf(e.getSubjectType()),
                e.getSubjectId(),
                e.getBusinessId(),
                Money.ofMinor(e.getAmountMinor(), e.getCurrency()),
                PaymentStatus.valueOf(e.getStatus()),
                e.getFormUrl(),
                e.getReturnUrl(),
                e.getFailUrl(),
                e.getInitiatedAt(),
                e.getCompletedAt(),
                e.getFailedAt(),
                e.getExpiresAt(),
                e.getCompletedBy(),
                e.getFailureCode(),
                e.getFailureMessage(),
                e.getCardPanMasked(),
                e.getCardExpiration(),
                e.getCardholderName(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion()
        );
    }

    public static PaymentEntity toEntity(Payment p) {
        return PaymentEntity.builder()
                .id(p.getId().getValue())
                .provider(p.getProvider().name())
                .providerOrderId(p.getProviderOrderId())
                .orderNumber(p.getOrderNumber().getValue())
                .subjectType(p.getSubjectType().name())
                .subjectId(p.getSubjectId())
                .businessId(p.getBusinessId())
                .amountMinor(p.getMoney().getAmountMinor())
                .currency(p.getMoney().getCurrency())
                .status(p.getStatus().name())
                .formUrl(p.getFormUrl())
                .returnUrl(p.getReturnUrl())
                .failUrl(p.getFailUrl())
                .initiatedAt(p.getInitiatedAt())
                .completedAt(p.getCompletedAt())
                .failedAt(p.getFailedAt())
                .expiresAt(p.getExpiresAt())
                .completedBy(p.getCompletedBy())
                .failureCode(p.getFailureCode())
                .failureMessage(p.getFailureMessage())
                .cardPanMasked(p.getCardPanMasked())
                .cardExpiration(p.getCardExpiration())
                .cardholderName(p.getCardholderName())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .version(p.getVersion())
                .build();
    }
}
