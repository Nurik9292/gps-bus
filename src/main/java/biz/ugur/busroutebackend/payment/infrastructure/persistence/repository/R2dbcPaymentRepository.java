package biz.ugur.busroutebackend.payment.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcPaymentRepository extends PaymentBaseRepository implements PaymentRepository {

    public R2dbcPaymentRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Mono<Payment> findByOrderNumber(String orderNumber) {
        return databaseClient.sql("SELECT * FROM payments WHERE order_number = :orderNumber LIMIT 1")
                .bind("orderNumber", orderNumber)
                .map(getRowMapper())
                .one();
    }

    @Override
    public Mono<Payment> findByProviderOrderId(PaymentProvider provider, String providerOrderId) {
        return databaseClient.sql("""
                        SELECT * FROM payments
                        WHERE provider = :provider AND provider_order_id = :providerOrderId
                        LIMIT 1
                        """)
                .bind("provider", provider.name())
                .bind("providerOrderId", providerOrderId)
                .map(getRowMapper())
                .one();
    }

    @Override
    public Flux<Payment> findByStatus(PaymentStatus status, Pageable pageable) {
        String sql = String.format("""
                SELECT * FROM payments
                WHERE status = :status
                %s
                LIMIT :limit OFFSET :offset
                """, getOrderByClause(pageable));
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
        String sql = """
                SELECT * FROM payments
                WHERE status IN ('REGISTERED', 'PREAUTH')
                  AND provider_order_id IS NOT NULL
                  AND initiated_at < NOW() - make_interval(secs => CAST(:seconds AS integer))
                ORDER BY initiated_at ASC
                LIMIT :limit OFFSET :offset
                """;
        return databaseClient.sql(sql)
                .bind("seconds", (int) staleAfterSeconds)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }
}
