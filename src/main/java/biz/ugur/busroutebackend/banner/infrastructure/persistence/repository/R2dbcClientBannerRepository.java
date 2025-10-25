package biz.ugur.busroutebackend.banner.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.repository.ClientBannerRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public class R2dbcClientBannerRepository extends BannerBaseRepository implements ClientBannerRepository  {


    protected R2dbcClientBannerRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    public Flux<Banner> findActiveBannersByTypeWithPagination(BannerType type, Pageable pageable) {
        String sql = """
        SELECT * FROM banners 
        WHERE is_active = true 
        AND (start_date IS NULL OR start_date <= NOW())
        AND (end_date IS NULL OR end_date >= NOW())
        AND type = :type
        ORDER BY display_order ASC, created_at DESC
        LIMIT :limit OFFSET :offset
        """;

        return databaseClient.sql(sql)
                .bind("type", type.getValue().toUpperCase())
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

}
