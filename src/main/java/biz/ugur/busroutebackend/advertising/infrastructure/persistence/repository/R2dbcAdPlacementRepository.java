package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public class R2dbcAdPlacementRepository extends AdPlacementBaseRepository implements AdPlacementRepository {

    public R2dbcAdPlacementRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<AdPlacement> findByBusinessId(BusinessId businessId, Pageable pageable) {
        String sql = String.format("""
                SELECT * FROM ad_placements
                WHERE business_id = :businessId
                %s
                LIMIT :limit OFFSET :offset
                """, getOrderByClause(pageable));
        return databaseClient.sql(sql)
                .bind("businessId", businessId.getValue())
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Long> countByBusinessId(BusinessId businessId) {
        return databaseClient.sql("SELECT COUNT(*) FROM ad_placements WHERE business_id = :businessId")
                .bind("businessId", businessId.getValue())
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<AdPlacement> findByStatus(PlacementStatus status, Pageable pageable) {
        String sql = String.format("""
                SELECT * FROM ad_placements
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
    public Flux<AdPlacement> findActiveByTypeAt(PlacementType placementType, LocalDateTime moment) {
        return databaseClient.sql("""
                        SELECT * FROM ad_placements
                        WHERE status = 'ACTIVE'
                          AND placement_type = :type
                          AND (starts_at IS NULL OR starts_at <= :moment)
                          AND (ends_at   IS NULL OR ends_at   >  :moment)
                        ORDER BY display_order ASC, created_at DESC
                        """)
                .bind("type", placementType.name())
                .bind("moment", moment)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<AdPlacement> findDueToActivate(LocalDateTime moment) {
        return databaseClient.sql("""
                        SELECT * FROM ad_placements
                        WHERE status = 'SCHEDULED'
                          AND starts_at IS NOT NULL
                          AND starts_at <= :moment
                        """)
                .bind("moment", moment)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<AdPlacement> findDueToExpire(LocalDateTime moment) {
        return databaseClient.sql("""
                        SELECT * FROM ad_placements
                        WHERE status = 'ACTIVE'
                          AND ends_at IS NOT NULL
                          AND ends_at <= :moment
                        """)
                .bind("moment", moment)
                .map(getRowMapper())
                .all();
    }
}
