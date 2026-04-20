package biz.ugur.busroutebackend.complaint.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.complaint.domain.model.Complaint;
import biz.ugur.busroutebackend.complaint.domain.valueobjects.ComplaintId;
import biz.ugur.busroutebackend.complaint.infrastructure.persistence.entity.ComplaintEntity;
import biz.ugur.busroutebackend.complaint.infrastructure.persistence.mapper.ComplaintEntityMapper;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class ComplaintBaseRepository extends BaseR2dbcRepository<Complaint, ComplaintId> {

    protected static final String SELECT_COLUMNS = String.join(", ",
            "id", "type", "title", "description", "status",
            "client_id", "admin_comment",
            "version", "created_at", "updated_at"
    );

    protected ComplaintBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "complaints", Complaint.class);
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
    }

    @Override
    protected String convertIdToDatabase(ComplaintId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Complaint> getRowMapper() {
        return this::mapRow;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Complaint complaint) {
        ComplaintEntity entity = ComplaintEntityMapper.toEntity(complaint);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId());
        columns.put("type", entity.getType());
        columns.put("title", entity.getTitle());
        columns.put("description", entity.getDescription());
        columns.put("status", entity.getStatus());
        columns.put("client_id", entity.getClientId());
        columns.put("admin_comment", entity.getAdminComment());
        columns.put("version", entity.getVersion());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        return columns;
    }

    private Complaint mapRow(Row row, RowMetadata metadata) {
        return ComplaintEntityMapper.toDomain(ComplaintEntity.builder()
                .id(row.get("id", String.class))
                .type(row.get("type", String.class))
                .title(row.get("title", String.class))
                .description(row.get("description", String.class))
                .status(row.get("status", String.class))
                .clientId(row.get("client_id", String.class))
                .adminComment(row.get("admin_comment", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
