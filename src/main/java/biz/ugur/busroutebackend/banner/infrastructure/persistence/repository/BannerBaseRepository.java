package biz.ugur.busroutebackend.banner.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.infrastructure.mapper.BannerMapper;
import biz.ugur.busroutebackend.banner.infrastructure.persistence.entity.BannerEntity;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
public abstract class BannerBaseRepository extends BaseR2dbcRepository<Banner, BannerId> {

    protected BannerBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "banners", Banner.class);
    }


    @Override
    protected String convertIdToDatabase(BannerId bannerId) {
        return bannerId.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Banner> getRowMapper() {
        return this::mapRowToBanner;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Banner banner) {
        BannerEntity entity = BannerMapper.toEntity(banner);
        return Map.ofEntries(
                Map.entry("id", entity.getId()),
                Map.entry("title", entity.getTitle()),
                Map.entry("type", entity.getType()),
                Map.entry("image_url", entity.getImageUrl()),
                Map.entry("target_url", entity.getTargetUrl()),
                Map.entry("is_active", entity.getIsActive()),
                Map.entry("display_order", entity.getDisplayOrder()),
                Map.entry("start_date", entity.getStartDate()),
                Map.entry("end_date", entity.getEndDate()),
                Map.entry("content", entity.getContent()),
                Map.entry("version", entity.getVersion()),
                Map.entry("created_at", entity.getCreatedAt()),
                Map.entry("updated_at", entity.getUpdatedAt())
        );
    }

    private Banner mapRowToBanner(Row row, RowMetadata metadata) {
        return BannerMapper.toDomain(BannerEntity.builder()
                .id(row.get("id", String.class))
                .title(row.get("title", String.class))
                .type(row.get("type", String.class))
                .imageUrl(row.get("image_url", String.class))
                .targetUrl(row.get("target_url", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .displayOrder(row.get("display_order", Integer.class))
                .startDate(row.get("start_date", LocalDateTime.class))
                .endDate(row.get("end_date", LocalDateTime.class))
                .content(row.get("content", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }


    public Flux<Banner> findBySpecification(Specification<Banner> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT * FROM banners WHERE %s ORDER BY display_order ASC, created_at DESC",
            criteria.getWhereClause()
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(getRowMapper())
                .all();
    }


    public Flux<Banner> findBySpecification(Specification<Banner> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT * FROM banners WHERE %s %s LIMIT :limit OFFSET :offset",
            criteria.getWhereClause(),
            getOrderByClause(pageable)
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset());

        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(getRowMapper())
                .all();
    }


    public Mono<Long> countBySpecification(Specification<Banner> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
            "SELECT COUNT(*) FROM banners WHERE %s",
            criteria.getWhereClause()
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(row -> row.get(0, Long.class))
                .one();
    }
}
