package biz.ugur.busroutebackend.suggestion.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.valueobjects.AliasSuggestionId;
import biz.ugur.busroutebackend.suggestion.infrastructure.mapper.AliasSuggestionEntityMapper;
import biz.ugur.busroutebackend.suggestion.infrastructure.persistence.entity.AliasSuggestionEntity;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class SuggestionBaseRepository extends BaseR2dbcRepository<AliasSuggestion, AliasSuggestionId> {

    protected static final String SELECT_COLUMNS = String.join(", ",
            "id", "entity_type", "entity_id", "suggested_alias", "language",
            "status", "client_id", "reviewer_id", "review_comment",
            "version", "created_at", "updated_at"
    );

    protected SuggestionBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "alias_suggestions", AliasSuggestion.class);
    }

    @Override
    protected String selectColumns() {
        return SELECT_COLUMNS;
    }

    @Override
    protected String convertIdToDatabase(AliasSuggestionId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, AliasSuggestion> getRowMapper() {
        return this::mapRow;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(AliasSuggestion suggestion) {
        AliasSuggestionEntity entity = AliasSuggestionEntityMapper.toEntity(suggestion);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId());
        columns.put("entity_type", entity.getEntityType());
        columns.put("entity_id", entity.getEntityId());
        columns.put("suggested_alias", entity.getSuggestedAlias());
        columns.put("language", entity.getLanguage());
        columns.put("status", entity.getStatus());
        columns.put("client_id", entity.getClientId());
        columns.put("reviewer_id", entity.getReviewerId());
        columns.put("review_comment", entity.getReviewComment());
        columns.put("version", entity.getVersion());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        return columns;
    }

    private AliasSuggestion mapRow(Row row, RowMetadata metadata) {
        return AliasSuggestionEntityMapper.toDomain(AliasSuggestionEntity.builder()
                .id(row.get("id", String.class))
                .entityType(row.get("entity_type", String.class))
                .entityId(row.get("entity_id", String.class))
                .suggestedAlias(row.get("suggested_alias", String.class))
                .language(row.get("language", String.class))
                .status(row.get("status", String.class))
                .clientId(row.get("client_id", String.class))
                .reviewerId(row.get("reviewer_id", String.class))
                .reviewComment(row.get("review_comment", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
