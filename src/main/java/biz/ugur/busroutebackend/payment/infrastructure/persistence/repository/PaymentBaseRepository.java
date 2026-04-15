package biz.ugur.busroutebackend.payment.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.valueobjects.PaymentId;
import biz.ugur.busroutebackend.payment.infrastructure.mapper.PaymentMapper;
import biz.ugur.busroutebackend.payment.infrastructure.persistence.entity.PaymentEntity;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class PaymentBaseRepository extends BaseR2dbcRepository<Payment, PaymentId> {

    protected PaymentBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "payments", Payment.class);
    }

    @Override protected String convertIdToDatabase(PaymentId id) { return id.getValue(); }

    @Override protected BiFunction<Row, RowMetadata, Payment> getRowMapper() { return this::mapRow; }

    @Override
    protected Map<String, Object> mapEntityToColumns(Payment payment) {
        PaymentEntity e = PaymentMapper.toEntity(payment);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", e.getId());
        columns.put("provider", e.getProvider());
        columns.put("provider_order_id", e.getProviderOrderId());
        columns.put("order_number", e.getOrderNumber());
        columns.put("subject_type", e.getSubjectType());
        columns.put("subject_id", e.getSubjectId());
        columns.put("business_id", e.getBusinessId());
        columns.put("amount_minor", e.getAmountMinor());
        columns.put("currency", e.getCurrency());
        columns.put("status", e.getStatus());
        columns.put("form_url", e.getFormUrl());
        columns.put("return_url", e.getReturnUrl());
        columns.put("initiated_at", e.getInitiatedAt());
        columns.put("completed_at", e.getCompletedAt());
        columns.put("failed_at", e.getFailedAt());
        columns.put("expires_at", e.getExpiresAt());
        columns.put("failure_code", e.getFailureCode());
        columns.put("failure_message", e.getFailureMessage());
        columns.put("card_pan_masked", e.getCardPanMasked());
        columns.put("card_expiration", e.getCardExpiration());
        columns.put("cardholder_name", e.getCardholderName());
        columns.put("created_at", e.getCreatedAt());
        columns.put("updated_at", e.getUpdatedAt());
        columns.put("version", e.getVersion());
        return columns;
    }

    private Payment mapRow(Row row, RowMetadata meta) {
        return PaymentMapper.toDomain(PaymentEntity.builder()
                .id(row.get("id", String.class))
                .provider(row.get("provider", String.class))
                .providerOrderId(row.get("provider_order_id", String.class))
                .orderNumber(row.get("order_number", String.class))
                .subjectType(row.get("subject_type", String.class))
                .subjectId(row.get("subject_id", String.class))
                .businessId(row.get("business_id", String.class))
                .amountMinor(row.get("amount_minor", Long.class))
                .currency(row.get("currency", String.class))
                .status(row.get("status", String.class))
                .formUrl(row.get("form_url", String.class))
                .returnUrl(row.get("return_url", String.class))
                .initiatedAt(row.get("initiated_at", LocalDateTime.class))
                .completedAt(row.get("completed_at", LocalDateTime.class))
                .failedAt(row.get("failed_at", LocalDateTime.class))
                .expiresAt(row.get("expires_at", LocalDateTime.class))
                .failureCode(row.get("failure_code", String.class))
                .failureMessage(row.get("failure_message", String.class))
                .cardPanMasked(row.get("card_pan_masked", String.class))
                .cardExpiration(row.get("card_expiration", String.class))
                .cardholderName(row.get("cardholder_name", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
