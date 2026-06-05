package biz.ugur.busroutebackend.subscription.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public Flux<Subscription> findPaginated(SubscriptionStatus status, SubscriptionPeriod period, Pageable pageable) {
        List<String> conditions = filterConditions(status, period);
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        String sql = String.format("""
                SELECT %s FROM client_subscriptions
                %s
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
                """, selectColumns(), where);
        var spec = databaseClient.sql(sql)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset());
        spec = bindFilters(spec, status, period);
        return spec.map(getRowMapper()).all();
    }

    @Override
    public Mono<Long> countFiltered(SubscriptionStatus status, SubscriptionPeriod period) {
        List<String> conditions = filterConditions(status, period);
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        var spec = databaseClient.sql("SELECT COUNT(*) FROM client_subscriptions " + where);
        spec = bindFilters(spec, status, period);
        return spec.map(row -> row.get(0, Long.class)).one();
    }

    @Override
    public Flux<Subscription> findAllByClientId(String clientId) {
        String sql = String.format("""
                SELECT %s FROM client_subscriptions
                 WHERE client_id = :clientId
                 ORDER BY created_at DESC
                """, selectColumns());
        return databaseClient.sql(sql)
                .bind("clientId", clientId)
                .map(getRowMapper())
                .all();
    }

    private List<String> filterConditions(SubscriptionStatus status, SubscriptionPeriod period) {
        List<String> conditions = new ArrayList<>();
        if (status != null) {
            conditions.add("status = :status");
        }
        if (period != null) {
            conditions.add("period = :period");
        }
        return conditions;
    }

    private DatabaseClient.GenericExecuteSpec bindFilters(DatabaseClient.GenericExecuteSpec spec,
                                                          SubscriptionStatus status,
                                                          SubscriptionPeriod period) {
        if (status != null) {
            spec = spec.bind("status", status.name());
        }
        if (period != null) {
            spec = spec.bind("period", period.name());
        }
        return spec;
    }
}
