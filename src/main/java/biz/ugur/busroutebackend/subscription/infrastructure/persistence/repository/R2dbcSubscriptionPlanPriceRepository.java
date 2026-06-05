package biz.ugur.busroutebackend.subscription.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.model.SubscriptionPlanPrice;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionPlanPriceRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcSubscriptionPlanPriceRepository
        extends SubscriptionPlanPriceBaseRepository
        implements SubscriptionPlanPriceRepository {

    public R2dbcSubscriptionPlanPriceRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<SubscriptionPlanPrice> findAllPrices() {
        String sql = String.format("SELECT %s FROM subscription_plan_prices ORDER BY id", selectColumns());
        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<SubscriptionPlanPrice> findByPeriod(SubscriptionPeriod period) {
        return findById(period);
    }
}
