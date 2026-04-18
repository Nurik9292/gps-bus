package biz.ugur.busroutebackend.banner.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcAdminBannerRepository extends BannerBaseRepository implements AdminBannerRepository {


    public R2dbcAdminBannerRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<Banner> findActiveBanners() {
        String sql = String.format("""
            SELECT %s FROM banners
            WHERE is_active = true
            AND (start_date IS NULL OR start_date <= NOW())
            AND (end_date IS NULL OR end_date >= NOW())
            ORDER BY display_order ASC, created_at DESC
            """, selectColumns());

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }


    @Override
    public Mono<Long> countActiveBanners() {
        String sql = """
            SELECT COUNT(*) FROM banners 
            WHERE is_active = true 
            AND (start_date IS NULL OR start_date <= NOW())
            AND (end_date IS NULL OR end_date >= NOW())
            """;

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<Banner> findByTypeAndActive(BannerType type) {
        String sql = String.format("""
            SELECT %s FROM banners
            WHERE is_active = true
            AND LOWER(type) = LOWER(:type)
            AND (start_date IS NULL OR start_date <= NOW())
            AND (end_date IS NULL OR end_date >= NOW())
            ORDER BY display_order ASC, created_at DESC
            """, selectColumns());

        return databaseClient.sql(sql)
                .bind("type", type.getValue())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Long> countByType(biz.ugur.busroutebackend.banner.domain.enums.BannerType type) {
        String sql = """
            SELECT COUNT(*) FROM banners
            WHERE LOWER(type) = LOWER(:type)
            AND is_active = true
            AND (start_date IS NULL OR start_date <= NOW())
            AND (end_date IS NULL OR end_date >= NOW())
            """;

        return databaseClient.sql(sql)
                .bind("type", type.getValue())
                .map(row -> row.get(0, Long.class))
                .one();
    }
}