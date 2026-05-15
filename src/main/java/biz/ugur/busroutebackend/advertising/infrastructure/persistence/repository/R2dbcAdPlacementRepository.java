package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.SalesReportFilter;
import biz.ugur.busroutebackend.advertising.domain.repository.SalesReportRow;
import biz.ugur.busroutebackend.advertising.domain.repository.SalesReportRowTotals;
import biz.ugur.busroutebackend.advertising.infrastructure.mapper.SalesReportRowMapper;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Repository
public class R2dbcAdPlacementRepository extends AdPlacementBaseRepository implements AdPlacementRepository {

    private static final String SELECT_COLUMNS_PREFIXED = String.join(", ",
            "p.id", "p.business_id", "p.tariff_id", "p.placement_type", "p.kind", "p.status",
            "p.title", "p.content", "p.image_url", "p.target_url", "p.cta_text", "p.content_type",
            "p.starts_at", "p.ends_at",
            "p.display_order",
            "p.rejection_reason",
            "p.approved_at", "p.approved_by_admin_id",
            "p.rejected_at", "p.rejected_by_admin_id",
            "p.created_at", "p.updated_at", "p.version"
    );

    public R2dbcAdPlacementRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<AdPlacement> findByBusinessId(BusinessId businessId, Pageable pageable) {
        String sql = String.format("""
                SELECT %s FROM ad_placements
                WHERE business_id = :businessId
                %s
                LIMIT :limit OFFSET :offset
                """, selectColumns(), getOrderByClause(pageable));
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
    public Mono<Long> countByStatus(PlacementStatus status) {
        return databaseClient.sql("SELECT COUNT(*) FROM ad_placements WHERE status = :status")
                .bind("status", status.name())
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Mono<Map<PlacementStatus, Long>> countsByStatus() {
        return databaseClient.sql("SELECT status, COUNT(*) AS cnt FROM ad_placements GROUP BY status")
                .map(row -> Map.entry(
                        PlacementStatus.valueOf(row.get("status", String.class)),
                        row.get("cnt", Long.class)))
                .all()
                .collect(() -> new EnumMap<PlacementStatus, Long>(PlacementStatus.class),
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()))
                .map(map -> {
                    for (PlacementStatus s : PlacementStatus.values()) {
                        map.putIfAbsent(s, 0L);
                    }
                    return map;
                });
    }

    @Override
    public Flux<AdPlacement> findByStatus(PlacementStatus status, Pageable pageable) {
        String sql = String.format("""
                SELECT %s FROM ad_placements
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
    public Flux<AdPlacement> findByKind(PlacementKind kind, Pageable pageable) {
        String sql = String.format("""
                SELECT %s FROM ad_placements
                WHERE kind = :kind
                %s
                LIMIT :limit OFFSET :offset
                """, selectColumns(), getOrderByClause(pageable));
        return databaseClient.sql(sql)
                .bind("kind", kind.name())
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Long> countByKind(PlacementKind kind) {
        return databaseClient.sql("SELECT COUNT(*) FROM ad_placements WHERE kind = :kind")
                .bind("kind", kind.name())
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<AdPlacement> findActiveByTypeAt(PlacementType placementType, LocalDateTime moment) {
        String sql = String.format("""
                        SELECT %s FROM ad_placements
                        WHERE status = 'ACTIVE'
                          AND placement_type = :type
                          AND (starts_at IS NULL OR starts_at <= NOW())
                          AND (ends_at   IS NULL OR ends_at   >  NOW())
                        ORDER BY display_order ASC, created_at DESC
                        """, selectColumns());
        return databaseClient.sql(sql)
                .bind("type", placementType.name())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<AdPlacement> findActiveByKindAndTargetType(PlacementKind kind, TargetType targetType) {
        String sql = "SELECT " + SELECT_COLUMNS_PREFIXED + " FROM ad_placements p "
                + "JOIN ad_placement_targets t ON t.placement_id = p.id "
                + "WHERE p.kind = :kind "
                + "  AND p.status = 'ACTIVE' "
                + "  AND t.target_type = :target_type "
                + "ORDER BY p.display_order ASC, p.created_at DESC";
        return databaseClient.sql(sql)
                .bind("kind", kind.name())
                .bind("target_type", targetType.name())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<AdPlacement> findDueToActivate(LocalDateTime moment) {
        String sql = String.format("""
                        SELECT %s FROM ad_placements
                        WHERE status = 'SCHEDULED'
                          AND starts_at IS NOT NULL
                          AND starts_at <= NOW()
                        """, selectColumns());
        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<AdPlacement> findDueToExpire(LocalDateTime moment) {
        String sql = String.format("""
                        SELECT %s FROM ad_placements
                        WHERE status = 'ACTIVE'
                          AND ends_at IS NOT NULL
                          AND ends_at <= NOW()
                        """, selectColumns());
        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Flux<SalesReportRow> findForSalesReport(SalesReportFilter f) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.title, p.status AS placement_status, p.kind,
                       p.starts_at, p.ends_at,
                       p.business_id,
                       b.name AS business_name,
                       p.tariff_id,
                       t.name AS tariff_name,
                       pay.id AS payment_id, pay.provider, pay.status AS payment_status,
                       pay.amount_minor, pay.currency,
                       COALESCE((SELECT COUNT(*) FROM ad_impression_events WHERE placement_id = p.id::UUID), 0)::BIGINT AS impressions,
                       COALESCE((SELECT COUNT(*) FROM ad_click_events      WHERE placement_id = p.id::UUID), 0)::BIGINT AS clicks
                FROM ad_placements p
                JOIN businesses b  ON b.id = p.business_id
                JOIN ad_tariffs  t ON t.id = p.tariff_id
                LEFT JOIN payments pay ON pay.subject_type = 'AD_PLACEMENT'
                                      AND pay.subject_id   = p.id
                WHERE p.kind = 'COMMERCIAL'
                  AND p.created_at >= :from
                  AND p.created_at <  :to
                """);
        if (f.paymentStatus() != null) sql.append(" AND pay.status = :paymentStatus");
        if (f.provider() != null)      sql.append(" AND pay.provider = :provider");
        sql.append(" ORDER BY p.created_at DESC LIMIT :size OFFSET :offset");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
                .bind("from", f.from())
                .bind("to", f.to())
                .bind("size", f.size())
                .bind("offset", f.offset());
        if (f.paymentStatus() != null) spec = spec.bind("paymentStatus", f.paymentStatus().name());
        if (f.provider() != null)      spec = spec.bind("provider", f.provider().name());
        return spec.map(SalesReportRowMapper::mapRow).all();
    }

    @Override
    public Mono<Long> countForSalesReport(SalesReportFilter f) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)::BIGINT AS cnt
                FROM ad_placements p
                LEFT JOIN payments pay ON pay.subject_type = 'AD_PLACEMENT'
                                      AND pay.subject_id   = p.id
                WHERE p.kind = 'COMMERCIAL'
                  AND p.created_at >= :from
                  AND p.created_at <  :to
                """);
        if (f.paymentStatus() != null) sql.append(" AND pay.status = :paymentStatus");
        if (f.provider() != null)      sql.append(" AND pay.provider = :provider");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
                .bind("from", f.from())
                .bind("to", f.to());
        if (f.paymentStatus() != null) spec = spec.bind("paymentStatus", f.paymentStatus().name());
        if (f.provider() != null)      spec = spec.bind("provider", f.provider().name());
        return spec.map(row -> row.get("cnt", Long.class)).one();
    }

    @Override
    public Mono<SalesReportRowTotals> totalsForSalesReport(SalesReportFilter f) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)::BIGINT                                                              AS orders,
                       COALESCE(SUM(pay.amount_minor), 0)::BIGINT                                   AS revenue,
                       COALESCE(MAX(pay.currency), 'TMT')                                           AS currency,
                       COALESCE(SUM((SELECT COUNT(*) FROM ad_impression_events WHERE placement_id = p.id::UUID)), 0)::BIGINT AS total_impressions,
                       COALESCE(SUM((SELECT COUNT(*) FROM ad_click_events      WHERE placement_id = p.id::UUID)), 0)::BIGINT AS total_clicks
                FROM ad_placements p
                LEFT JOIN payments pay ON pay.subject_type = 'AD_PLACEMENT'
                                      AND pay.subject_id   = p.id
                WHERE p.kind = 'COMMERCIAL'
                  AND p.created_at >= :from
                  AND p.created_at <  :to
                """);
        if (f.paymentStatus() != null) sql.append(" AND pay.status = :paymentStatus");
        if (f.provider() != null)      sql.append(" AND pay.provider = :provider");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
                .bind("from", f.from())
                .bind("to", f.to());
        if (f.paymentStatus() != null) spec = spec.bind("paymentStatus", f.paymentStatus().name());
        if (f.provider() != null)      spec = spec.bind("provider", f.provider().name());

        return spec.map(row -> {
            long orders      = row.get("orders", Long.class);
            long revenue     = row.get("revenue", Long.class);
            String currency  = row.get("currency", String.class);
            long impressions = row.get("total_impressions", Long.class);
            long clicks      = row.get("total_clicks", Long.class);
            BigDecimal avgCtr = impressions == 0 ? null
                    : BigDecimal.valueOf(clicks)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(impressions), 2, RoundingMode.HALF_UP);
            return new SalesReportRowTotals(orders, revenue, currency, avgCtr);
        }).one();
    }
}
