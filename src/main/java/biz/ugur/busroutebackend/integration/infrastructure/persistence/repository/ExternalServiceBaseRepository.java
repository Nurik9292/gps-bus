package biz.ugur.busroutebackend.integration.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import biz.ugur.busroutebackend.integration.infrastructure.mapper.ExternalServiceMapper;
import biz.ugur.busroutebackend.integration.infrastructure.persistence.entity.ExternalServiceEntity;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;


public abstract class ExternalServiceBaseRepository extends BaseR2dbcRepository<ExternalService, ExternalServiceId> {

    protected static final String SELECT_COLUMNS = String.join(", ",
            "id", "name", "description", "api_token", "is_active",
            "allowed_endpoints", "rate_limit_per_minute", "last_used_at",
            "created_by_admin_id", "can_manage_clients",
            "version", "created_at", "updated_at"
    );

    protected ExternalServiceBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "external_services", ExternalService.class);
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
    }

    @Override
    protected String convertIdToDatabase(ExternalServiceId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, ExternalService> getRowMapper() {
        return this::mapRowToExternalService;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(ExternalService externalService) {
        ExternalServiceEntity entity = ExternalServiceMapper.toEntity(externalService);
        Map<String, Object> columns = new java.util.HashMap<>();
        columns.put("id", entity.getId());
        columns.put("name", entity.getName());
        columns.put("description", entity.getDescription());
        columns.put("api_token", entity.getApiToken());
        columns.put("is_active", entity.getIsActive());
        columns.put("allowed_endpoints", entity.getAllowedEndpoints() != null ?
                entity.getAllowedEndpoints().toArray(new String[0]) : null);
        columns.put("rate_limit_per_minute", entity.getRateLimitPerMinute());
        columns.put("last_used_at", entity.getLastUsedAt());
        columns.put("created_by_admin_id", entity.getCreatedByAdminId());
        columns.put("can_manage_clients", entity.getCanManageClients());
        columns.put("version", entity.getVersion());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        return columns;
    }

    private ExternalService mapRowToExternalService(Row row, RowMetadata metadata) {
        String[] allowedEndpointsArray = row.get("allowed_endpoints", String[].class);
        List<String> allowedEndpoints = allowedEndpointsArray != null ?
                Arrays.asList(allowedEndpointsArray) : null;

        return ExternalServiceMapper.toDomain(ExternalServiceEntity.builder()
                .id(row.get("id", String.class))
                .name(row.get("name", String.class))
                .description(row.get("description", String.class))
                .apiToken(row.get("api_token", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .allowedEndpoints(allowedEndpoints)
                .rateLimitPerMinute(row.get("rate_limit_per_minute", Integer.class))
                .lastUsedAt(row.get("last_used_at", LocalDateTime.class))
                .createdByAdminId(row.get("created_by_admin_id", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .canManageClients(row.get("can_manage_clients", Boolean.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
