package biz.ugur.busroutebackend.admin.infrastructure.repository;

import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
@Slf4j
public class R2dbcBannerRepository extends BaseR2dbcRepository<Banner, BannerId> implements BannerRepository {


    public R2dbcBannerRepository(DatabaseClient databaseClient) {
        super(databaseClient, "banners", Banner.class);
    }


    @Override
    protected String convertIdToDatabase(BannerId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Banner> getRowMapper() {
        return this::mapRowToBanner;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Banner banner) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", banner.getId().getValue());
        values.put("title", banner.getTitle());
        values.put("type", banner.getType());
        values.put("image_url", banner.getImageUrl());
        values.put("target_url", banner.getTargetUrl());
        values.put("is_active", banner.getIsActive());
        values.put("display_order", banner.getDisplayOrder());
        values.put("start_date", banner.getStartDate());
        values.put("end_date", banner.getEndDate());
        return values;
    }

    @Override
    public Flux<Banner> findActiveBanners() {
        String sql = """
            SELECT * FROM banners 
            WHERE is_active = true 
            AND (start_date IS NULL OR start_date <= NOW())
            AND (end_date IS NULL OR end_date >= NOW())
            ORDER BY display_order ASC, created_at DESC
            """;

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
    public Flux<Banner> findByTypeAndActive(String type) {
        String sql = """
            SELECT * FROM banners 
            WHERE is_active = true 
            AND type = :type
            AND (start_date IS NULL OR start_date <= NOW())
            AND (end_date IS NULL OR end_date >= NOW())
            ORDER BY display_order ASC, created_at DESC
            """;

        return databaseClient.sql(sql)
                .bind("type", type)
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Long> countByType(String type) {
        String sql = """
            SELECT COUNT(*) FROM banners 
            WHERE type = :type 
            AND is_active = true
            AND (start_date IS NULL OR start_date <= NOW())
            AND (end_date IS NULL OR end_date >= NOW())
            """;

        return databaseClient.sql(sql)
                .bind("type", type)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    private String getSortField(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return "display_order";
        }

        String property = pageable.getSort().iterator().next().getProperty();
        return switch (property) {
            case "title" -> "title";
            case "id" -> "id";
            case "created_at" -> "created_at";
            default -> "display_order";
        };
    }

    private String getSortDirection(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return "ASC";
        }

        return pageable.getSort().iterator().next().getDirection().name();
    }

    private Banner mapRowToBanner(Row row, RowMetadata metadata) {
        BannerId id = BannerId.of(row.get("id", String.class));
        String title = row.get("title", String.class);
        String type = row.get("type", String.class);
        String imageUrl = row.get("image_url", String.class);
        String targetUrl = row.get("target_url", String.class);
        Boolean isActive = row.get("is_active", Boolean.class);
        Integer displayOrder = row.get("display_order", Integer.class);
        LocalDateTime startDate = row.get("start_date", LocalDateTime.class);
        LocalDateTime endDate = row.get("end_date", LocalDateTime.class);

        return Banner.restore(
                id, title, type, imageUrl, targetUrl, isActive, displayOrder, startDate, endDate,
                row.get("created_at", java.time.Instant.class),
                row.get("updated_at", java.time.Instant.class),
                row.get("version", Long.class)
        );
    }
}