package biz.ugur.busroutebackend.payment.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
public class R2dbcPaymentRepository extends PaymentBaseRepository implements PaymentRepository {

    public R2dbcPaymentRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Mono<Payment> findByOrderNumber(String orderNumber) {
        String sql = String.format(
                "SELECT %s FROM payments WHERE order_number = :orderNumber LIMIT 1",
                selectColumns()
        );
        return databaseClient.sql(sql)
                .bind("orderNumber", orderNumber)
                .map(getRowMapper())
                .one();
    }

    @Override
    public Mono<Payment> findByProviderOrderId(PaymentProvider provider, String providerOrderId) {
        String sql = String.format("""
                        SELECT %s FROM payments
                        WHERE provider = :provider AND provider_order_id = :providerOrderId
                        LIMIT 1
                        """, selectColumns());
        return databaseClient.sql(sql)
                .bind("provider", provider.name())
                .bind("providerOrderId", providerOrderId)
                .map(getRowMapper())
                .one();
    }

    @Override
    public Flux<Payment> findByStatus(PaymentStatus status, Pageable pageable) {
        String sql = String.format("""
                SELECT %s FROM payments
                WHERE status = :status
                %s
                LIMIT :limit OFFSET :offset
                """, selectColumns(), getOrderByClause(pageable));
        return databaseClient.sql(sql)
                .bind("status", status.name())
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Long> countByStatus(PaymentStatus status) {
        return databaseClient.sql("SELECT COUNT(*) FROM payments WHERE status = :status")
                .bind("status", status.name())
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<Payment> findPendingStale(long staleAfterSeconds, Pageable pageable) {
        String sql = String.format("""
                SELECT %s FROM payments
                WHERE status IN ('REGISTERED', 'PREAUTH')
                  AND provider_order_id IS NOT NULL
                  AND initiated_at < NOW() - make_interval(secs => CAST(:seconds AS integer))
                ORDER BY initiated_at ASC
                LIMIT :limit OFFSET :offset
                """, selectColumns());
        return databaseClient.sql(sql)
                .bind("seconds", (int) staleAfterSeconds)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Payment> findFirstBySubjectAndProviderAndStatus(
            PaymentSubjectType subjectType,
            String subjectId,
            PaymentProvider provider,
            PaymentStatus status) {
        String sql = String.format("""
                SELECT %s FROM payments
                WHERE subject_type = :subjectType
                  AND subject_id   = :subjectId
                  AND provider     = :provider
                  AND status       = :status
                ORDER BY created_at DESC
                LIMIT 1
                """, selectColumns());
        return databaseClient.sql(sql)
                .bind("subjectType", subjectType.name())
                .bind("subjectId", subjectId)
                .bind("provider", provider.name())
                .bind("status", status.name())
                .map(getRowMapper())
                .one();
    }

    @Override
    public Mono<Long> sumCompletedRevenueBetween(Instant from, Instant to) {
        return databaseClient.sql("""
                SELECT COALESCE(SUM(amount_minor), 0)::BIGINT AS total
                FROM payments
                WHERE status = 'COMPLETED'
                  AND completed_at >= :from
                  AND completed_at <  :to
                """)
                .bind("from", from)
                .bind("to", to)
                .map(row -> row.get("total", Long.class))
                .one();
    }

    @Override
    public Mono<Long> countByProviderAndStatus(PaymentProvider provider, PaymentStatus status) {
        return databaseClient.sql("""
                SELECT COUNT(*)::BIGINT AS cnt
                FROM payments
                WHERE provider = :provider AND status = :status
                """)
                .bind("provider", provider.name())
                .bind("status", status.name())
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    @Override
    public Flux<Payment> findBySubjectTypeAndSubjectIdIn(PaymentSubjectType subjectType,
                                                         Collection<String> subjectIds,
                                                         PaymentStatus status,
                                                         Instant from,
                                                         Instant to,
                                                         Pageable pageable) {
        if (subjectIds.isEmpty()) {
            return Flux.empty();
        }
        String where = subjectConditions(status, from, to);
        String sql = String.format("""
                SELECT %s FROM payments
                %s
                ORDER BY initiated_at DESC
                LIMIT :limit OFFSET :offset
                """, selectColumns(), where);
        var spec = databaseClient.sql(sql)
                .bind("subjectType", subjectType.name())
                .bind("subjectIds", subjectIds)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset());
        spec = bindSubjectFilters(spec, status, from, to);
        return spec.map(getRowMapper()).all();
    }

    @Override
    public Mono<Long> countBySubjectTypeAndSubjectIdIn(PaymentSubjectType subjectType,
                                                       Collection<String> subjectIds,
                                                       PaymentStatus status,
                                                       Instant from,
                                                       Instant to) {
        if (subjectIds.isEmpty()) {
            return Mono.just(0L);
        }
        String where = subjectConditions(status, from, to);
        var spec = databaseClient.sql("SELECT COUNT(*) FROM payments " + where)
                .bind("subjectType", subjectType.name())
                .bind("subjectIds", subjectIds);
        spec = bindSubjectFilters(spec, status, from, to);
        return spec.map(row -> row.get(0, Long.class)).one();
    }

    @Override
    public Mono<Payment> findLatestBySubject(PaymentSubjectType subjectType, String subjectId) {
        String sql = String.format("""
                SELECT %s FROM payments
                WHERE subject_type = :subjectType AND subject_id = :subjectId
                ORDER BY initiated_at DESC
                LIMIT 1
                """, selectColumns());
        return databaseClient.sql(sql)
                .bind("subjectType", subjectType.name())
                .bind("subjectId", subjectId)
                .map(getRowMapper())
                .one();
    }

    @Override
    public Mono<Map<String, Long>> countBySubjectTypeAndSubjectIdInGroupByStatus(
            PaymentSubjectType subjectType, Collection<String> subjectIds) {
        if (subjectIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        String sql = """
                SELECT status, COUNT(*)::BIGINT AS cnt FROM payments
                WHERE subject_type = :subjectType AND subject_id IN (:subjectIds)
                GROUP BY status
                """;
        return databaseClient.sql(sql)
                .bind("subjectType", subjectType.name())
                .bind("subjectIds", subjectIds)
                .map(row -> Map.entry(row.get("status", String.class), row.get("cnt", Long.class)))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private String subjectConditions(PaymentStatus status, Instant from, Instant to) {
        List<String> conditions = new ArrayList<>();
        conditions.add("subject_type = :subjectType");
        conditions.add("subject_id IN (:subjectIds)");
        if (status != null) {
            conditions.add("status = :status");
        }
        if (from != null) {
            conditions.add("initiated_at >= :from");
        }
        if (to != null) {
            conditions.add("initiated_at < :to");
        }
        return "WHERE " + String.join(" AND ", conditions);
    }

    private DatabaseClient.GenericExecuteSpec bindSubjectFilters(DatabaseClient.GenericExecuteSpec spec,
                                                                 PaymentStatus status,
                                                                 Instant from,
                                                                 Instant to) {
        if (status != null) {
            spec = spec.bind("status", status.name());
        }
        if (from != null) {
            spec = spec.bind("from", from);
        }
        if (to != null) {
            spec = spec.bind("to", to);
        }
        return spec;
    }
}
