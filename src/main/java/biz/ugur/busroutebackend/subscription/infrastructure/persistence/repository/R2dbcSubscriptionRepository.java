package biz.ugur.busroutebackend.subscription.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcSubscriptionRepository extends SubscriptionBaseRepository implements SubscriptionRepository {

    public R2dbcSubscriptionRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Mono<Subscription> findActiveByClientId(String clientId) {
        String sql = String.format("""
                SELECT %s FROM client_subscriptions
                 WHERE client_id = :clientId
                   AND status = 'ACTIVE'
                   AND (expires_at IS NULL OR expires_at > NOW())
                 ORDER BY expires_at DESC NULLS LAST
                 LIMIT 1
                """, selectColumns());
        return databaseClient.sql(sql)
                .bind("clientId", clientId)
                .map(getRowMapper())
                .one();
    }

    @Override
    public Mono<Subscription> findLatestByClientId(String clientId) {
        String sql = String.format("""
                SELECT %s FROM client_subscriptions
                 WHERE client_id = :clientId
                 ORDER BY created_at DESC
                 LIMIT 1
                """, selectColumns());
        return databaseClient.sql(sql)
                .bind("clientId", clientId)
                .map(getRowMapper())
                .one();
    }

    @Override
    public Mono<Subscription> findByPaymentId(String paymentId) {
        String sql = String.format("""
                SELECT %s FROM client_subscriptions
                 WHERE payment_id = :paymentId
                 LIMIT 1
                """, selectColumns());
        return databaseClient.sql(sql)
                .bind("paymentId", paymentId)
                .map(getRowMapper())
                .one();
    }
}
