package biz.ugur.busroutebackend.integration.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.integration.infrastructure.persistence.entity.ApiLogEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Slf4j
public class R2dbcApiLogRepository {

    private static final String SELECT_COLUMNS = String.join(", ",
            "id", "external_service_id", "endpoint", "http_method",
            "response_status", "response_time_ms", "ip_address",
            "user_agent", "error_message", "created_at"
    );

    private final DatabaseClient databaseClient;

    public Mono<Void> save(ApiLogEntity logEntity) {
        String sql = """
            INSERT INTO external_service_api_logs (
                id, external_service_id, endpoint, http_method,
                response_status, response_time_ms, ip_address,
                user_agent, error_message, created_at
            ) VALUES (
                :id, :externalServiceId, :endpoint, :httpMethod,
                :responseStatus, :responseTimeMs, :ipAddress,
                :userAgent, :errorMessage, :createdAt
            )
            """;

        return databaseClient.sql(sql)
                .bind("id", logEntity.getId() != null ? logEntity.getId() : UUID.randomUUID().toString())
                .bind("externalServiceId", logEntity.getExternalServiceId())
                .bind("endpoint", logEntity.getEndpoint())
                .bind("httpMethod", logEntity.getHttpMethod())
                .bind("responseStatus", logEntity.getResponseStatus())
                .bind("responseTimeMs", logEntity.getResponseTimeMs())
                .bind("ipAddress", logEntity.getIpAddress())
                .bind("userAgent", logEntity.getUserAgent())
                .bind("errorMessage", logEntity.getErrorMessage())
                .bind("createdAt", logEntity.getCreatedAt() != null ? logEntity.getCreatedAt() : LocalDateTime.now())
                .then()
                .doOnSuccess(v -> log.debug("Saved API log for service: {} endpoint: {}",
                        logEntity.getExternalServiceId(), logEntity.getEndpoint()))
                .doOnError(error -> log.error("Error saving API log", error));
    }

    public Flux<ApiLogEntity> findByExternalServiceId(String externalServiceId, int limit) {
        String sql = String.format("""
            SELECT %s FROM external_service_api_logs
            WHERE external_service_id = :externalServiceId
            ORDER BY created_at DESC
            LIMIT :limit
            """, SELECT_COLUMNS);

        return databaseClient.sql(sql)
                .bind("externalServiceId", externalServiceId)
                .bind("limit", limit)
                .map((row, metadata) -> ApiLogEntity.builder()
                        .id(row.get("id", String.class))
                        .externalServiceId(row.get("external_service_id", String.class))
                        .endpoint(row.get("endpoint", String.class))
                        .httpMethod(row.get("http_method", String.class))
                        .responseStatus(row.get("response_status", Integer.class))
                        .responseTimeMs(row.get("response_time_ms", Integer.class))
                        .ipAddress(row.get("ip_address", String.class))
                        .userAgent(row.get("user_agent", String.class))
                        .errorMessage(row.get("error_message", String.class))
                        .createdAt(row.get("created_at", LocalDateTime.class))
                        .build())
                .all();
    }

    public Mono<Long> countRecentCalls(String externalServiceId, LocalDateTime since) {
        String sql = """
            SELECT COUNT(*) AS cnt FROM external_service_api_logs
            WHERE external_service_id = :externalServiceId
            AND created_at >= :since
            """;

        return databaseClient.sql(sql)
                .bind("externalServiceId", externalServiceId)
                .bind("since", since)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    public Mono<Long> deleteOlderThan(LocalDateTime threshold) {
        String sql = """
            DELETE FROM external_service_api_logs
            WHERE created_at < :threshold
            """;

        return databaseClient.sql(sql)
                .bind("threshold", threshold)
                .fetch()
                .rowsUpdated()
                .doOnSuccess(count -> log.info("Deleted {} old API log entries", count));
    }
}
