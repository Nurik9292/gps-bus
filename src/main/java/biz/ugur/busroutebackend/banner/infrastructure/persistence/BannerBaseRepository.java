package biz.ugur.busroutebackend.banner.infrastructure.persistence;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
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
        values.put("content", banner.getContent());
        return values;
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
        String content = row.get("content", String.class);

        return Banner.restore(
                id,
                title,
                BannerType.fromValue(type),
                imageUrl,
                targetUrl,
                isActive,
                displayOrder,
                startDate,
                endDate,
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class),
                content,
                row.get("version", Long.class)
        );
    }
}
