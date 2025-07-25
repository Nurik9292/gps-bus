package biz.ugur.busroutebackend.admin.infrastructure.repository;

import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;

@Repository
@Slf4j
public class R2dbcBannerRepository implements BannerRepository {

    private final DatabaseClient databaseClient;

    public R2dbcBannerRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Banner> save(Banner banner) {
        if (banner.getId() == null) {
            return insert(banner);
        } else {
            return update(banner);
        }
    }

    private Mono<Banner> insert(Banner banner) {
        String sql = """
            INSERT INTO banners (id, title, image_url, target_url, is_active, display_order,
                               start_date, end_date, created_at, updated_at, version)
            VALUES (:id, :title, :imageUrl, :targetUrl, :isActive, :displayOrder,
                   :startDate, :endDate, :createdAt, :updatedAt, :version)
            """;

        Instant now = Instant.now();
        return databaseClient.sql(sql)
                .bind("id", banner.getId().getValue())
                .bind("title", banner.getTitle())
                .bind("imageUrl", banner.getImageUrl())
                .bind("targetUrl", banner.getTargetUrl())
                .bind("isActive", banner.getIsActive())
                .bind("displayOrder", banner.getDisplayOrder())
                .bind("startDate", banner.getStartDate())
                .bind("endDate", banner.getEndDate())
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .bind("version", 0L)
                .then()
                .thenReturn(banner);
    }

    private Mono<Banner> update(Banner banner) {
        String sql = """
            UPDATE banners 
            SET title = :title, image_url = :imageUrl, target_url = :targetUrl,
                is_active = :isActive, display_order = :displayOrder,
                start_date = :startDate, end_date = :endDate,
                updated_at = :updatedAt, version = version + 1
            WHERE id = :id
            """;

        return databaseClient.sql(sql)
                .bind("id", banner.getId().getValue())
                .bind("title", banner.getTitle())
                .bind("imageUrl", banner.getImageUrl())
                .bind("targetUrl", banner.getTargetUrl())
                .bind("isActive", banner.getIsActive())
                .bind("displayOrder", banner.getDisplayOrder())
                .bind("startDate", banner.getStartDate())
                .bind("endDate", banner.getEndDate())
                .bind("updatedAt", Instant.now())
                .then()
                .thenReturn(banner);
    }

    @Override
    public Mono<Banner> findById(BannerId bannerId) {
        String sql = "SELECT * FROM banners WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", bannerId.getValue())
                .map(this::mapRowToBanner)
                .one();
    }

    @Override
    public Flux<Banner> findActiveBanners() {
        String sql = """
            SELECT * FROM banners 
            WHERE is_active = true 
            AND (start_date IS NULL OR start_date <= CURRENT_TIMESTAMP)
            AND (end_date IS NULL OR end_date >= CURRENT_TIMESTAMP)
            ORDER BY display_order, created_at
            """;

        return databaseClient.sql(sql)
                .map(this::mapRowToBanner)
                .all();
    }

    @Override
    public Flux<Banner> findAllBanners() {
        String sql = "SELECT * FROM banners ORDER BY display_order, created_at DESC";

        return databaseClient.sql(sql)
                .map(this::mapRowToBanner)
                .all();
    }

    @Override
    public Mono<Void> deleteById(BannerId bannerId) {
        String sql = "DELETE FROM banners WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", bannerId.getValue())
                .then();
    }

    @Override
    public Mono<Long> countActiveBanners() {
        String sql = """
            SELECT COUNT(*) FROM banners 
            WHERE is_active = true 
            AND (start_date IS NULL OR start_date <= CURRENT_TIMESTAMP)
            AND (end_date IS NULL OR end_date >= CURRENT_TIMESTAMP)
            """;

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    private Banner mapRowToBanner(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new Banner(
                BannerId.of(row.get("id", String.class)),
                row.get("title", String.class),
                row.get("image_url", String.class),
                row.get("target_url", String.class),
                row.get("is_active", Boolean.class),
                row.get("display_order", Integer.class),
                row.get("start_date", LocalDateTime.class),
                row.get("end_date", LocalDateTime.class)
        );
    }
}