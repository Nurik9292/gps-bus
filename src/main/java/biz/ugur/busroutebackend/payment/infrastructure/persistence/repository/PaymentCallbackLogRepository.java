package biz.ugur.busroutebackend.payment.infrastructure.persistence.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Thin writer for the {@code payment_callbacks_log} audit table.
 * Every provider interaction (return URL, webhook, manual sync) should land here for forensics.
 */
@Repository
@Slf4j
public class PaymentCallbackLogRepository {

    private final DatabaseClient databaseClient;

    public PaymentCallbackLogRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Void> log(String paymentId,
                          String provider,
                          String kind,
                          String providerOrderId,
                          Integer orderStatusCode,
                          Integer errorCode,
                          String errorMessage,
                          String rawPayload,
                          String sourceIp) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO payment_callbacks_log
                            (payment_id, provider, kind, provider_order_id, order_status_code,
                             error_code, error_message, raw_payload, source_ip)
                        VALUES (:paymentId, :provider, :kind, :providerOrderId, :orderStatusCode,
                                :errorCode, :errorMessage, :rawPayload, :sourceIp)
                        """)
                .bind("provider", provider)
                .bind("kind", kind);

        spec = paymentId != null
                ? spec.bind("paymentId", paymentId)
                : spec.bindNull("paymentId", String.class);
        spec = providerOrderId != null
                ? spec.bind("providerOrderId", providerOrderId)
                : spec.bindNull("providerOrderId", String.class);
        spec = orderStatusCode != null
                ? spec.bind("orderStatusCode", orderStatusCode)
                : spec.bindNull("orderStatusCode", Integer.class);
        spec = errorCode != null
                ? spec.bind("errorCode", errorCode)
                : spec.bindNull("errorCode", Integer.class);
        spec = errorMessage != null
                ? spec.bind("errorMessage", errorMessage)
                : spec.bindNull("errorMessage", String.class);
        spec = rawPayload != null
                ? spec.bind("rawPayload", rawPayload)
                : spec.bindNull("rawPayload", String.class);
        spec = sourceIp != null
                ? spec.bind("sourceIp", sourceIp)
                : spec.bindNull("sourceIp", String.class);

        return spec.then()
                .onErrorResume(e -> {
                    log.warn("Failed to write payment_callbacks_log: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
