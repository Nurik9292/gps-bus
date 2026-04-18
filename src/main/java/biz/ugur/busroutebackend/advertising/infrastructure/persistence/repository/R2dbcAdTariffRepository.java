package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public class R2dbcAdTariffRepository extends AdTariffBaseRepository implements AdTariffRepository {

    public R2dbcAdTariffRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<AdTariff> findActive() {
        String sql = String.format("""
                        SELECT %s FROM ad_tariffs
                        WHERE is_active = true
                        ORDER BY display_order ASC, created_at DESC
                        """, selectColumns());
        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<AdTariff> findActiveByType(PlacementType placementType) {
        String sql = String.format("""
                        SELECT %s FROM ad_tariffs
                        WHERE is_active = true AND placement_type = :type
                        ORDER BY display_order ASC, price_amount ASC
                        """, selectColumns());
        return databaseClient.sql(sql)
                .bind("type", placementType.name())
                .map(getRowMapper())
                .all();
    }
}
