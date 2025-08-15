package biz.ugur.busroutebackend.admin.infrastructure.repository;

import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
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
        return findById(banner.getId())
                .flatMap(existing -> update(banner))
                .switchIfEmpty(insert(banner));
    }


    private Mono<Banner> insert(Banner banner) {
        String sql = """
            INSERT INTO banners (id, title, type, image_url, target_url, is_active, display_order,
                               start_date, end_date, created_at, updated_at, version)
            VALUES (:id, :title, :type, :imageUrl, :targetUrl, :isActive, :displayOrder,
                   :startDate, :endDate, :createdAt, :updatedAt, :version)
            """;

        Instant now = Instant.now();
        return databaseClient.sql(sql)
                .bind("id", banner.getId().getValue())
                .bind("title", banner.getTitle())
                .bind("type", banner.getType())
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
            SET title = :title, type = :type, image_url = :imageUrl, target_url = :targetUrl,
                is_active = :isActive, display_order = :displayOrder,
                start_date = :startDate, end_date = :endDate,
                updated_at = :updatedAt, version = version + 1
            WHERE id = :id
            """;

        return databaseClient.sql(sql)
                .bind("id", banner.getId().getValue())
                .bind("title", banner.getTitle())
                .bind("type", banner.getType())
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
    public Mono<Boolean> existsById(BannerId bannerId) {
        String sql = "SELECT * FROM banners WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", bannerId.getValue())
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
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
                .map(this::mapRowToBanner)
                .all();
    }

    @Override
    public Flux<Banner> findAllBanners() {
        String sql = "SELECT * FROM banners ORDER BY display_order ASC, created_at DESC";

        return databaseClient.sql(sql)
                .map(this::mapRowToBanner)
                .all();
    }

    @Override
    public Flux<Banner> findActiveBannersWithPagination(Pageable pageable) {
        String sql = """
            SELECT * FROM banners 
            WHERE is_active = true 
            AND (start_date IS NULL OR start_date <= NOW())
            AND (end_date IS NULL OR end_date >= NOW())
            ORDER BY %s %s
            LIMIT :limit OFFSET :offset
            """.formatted(
                getSortField(pageable),
                getSortDirection(pageable)
        );

        return databaseClient.sql(sql)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(this::mapRowToBanner)
                .all();
    }

    @Override
    public Flux<Banner> findAllBannersWithPagination(Pageable pageable) {
        String sql = """
            SELECT * FROM banners 
            ORDER BY %s %s
            LIMIT :limit OFFSET :offset
            """.formatted(
                getSortField(pageable),
                getSortDirection(pageable)
        );

        return databaseClient.sql(sql)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
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
            AND (start_date IS NULL OR start_date <= NOW())
            AND (end_date IS NULL OR end_date >= NOW())
            """;

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Mono<Long> countTotalBanners() {
        String sql = "SELECT COUNT(*) FROM banners";

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }


    @Override
    public Flux<Banner> findByTypeAndActive(String type) {
        String sql = "SELECT * FROM banners WHERE is_active = true AND type = :type";

        return databaseClient.sql(sql)
                .bind("type", type)
                .map(this::mapRowToBanner)
                .all();
    }

    @Override
    public Mono<Long> countByType(String type) {
        String sql = "SELECT COUNT(*) FROM banners WHERE type = :type and is_active = true";

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
            case "display_order" -> "display_order";
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

        return Banner.restore(id, title, type, imageUrl, targetUrl, isActive, displayOrder, startDate, endDate);
    }
}