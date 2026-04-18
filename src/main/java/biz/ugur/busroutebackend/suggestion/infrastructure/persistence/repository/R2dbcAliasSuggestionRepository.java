package biz.ugur.busroutebackend.suggestion.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionStatus;
import biz.ugur.busroutebackend.suggestion.domain.repository.AliasSuggestionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcAliasSuggestionRepository extends SuggestionBaseRepository implements AliasSuggestionRepository {

    public R2dbcAliasSuggestionRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<AliasSuggestion> findByFilters(SuggestionStatus status, SuggestionEntityType entityType, Pageable pageable) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(selectColumns())
                .append(" FROM alias_suggestions WHERE 1=1");

        if (status != null) sql.append(" AND status = :status");
        if (entityType != null) sql.append(" AND entity_type = :entityType");

        sql.append(" ").append(getOrderByClause(pageable));
        sql.append(" LIMIT :limit OFFSET :offset");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        if (status != null) spec = spec.bind("status", status.name());
        if (entityType != null) spec = spec.bind("entityType", entityType.name());
        spec = spec.bind("limit", pageable.getPageSize());
        spec = spec.bind("offset", pageable.getOffset());

        return spec.map(getRowMapper()).all();
    }

    @Override
    public Mono<Long> countByFilters(SuggestionStatus status, SuggestionEntityType entityType) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM alias_suggestions WHERE 1=1");

        if (status != null) sql.append(" AND status = :status");
        if (entityType != null) sql.append(" AND entity_type = :entityType");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        if (status != null) spec = spec.bind("status", status.name());
        if (entityType != null) spec = spec.bind("entityType", entityType.name());

        return spec.map(row -> row.get(0, Long.class)).one();
    }

    @Override
    public Mono<Boolean> existsByEntityAndAlias(SuggestionEntityType entityType, String entityId, String suggestedAlias) {
        String sql = "SELECT EXISTS(SELECT 1 FROM alias_suggestions WHERE entity_type = :entityType AND entity_id = :entityId AND LOWER(suggested_alias) = LOWER(:alias) AND status != 'REJECTED')";
        return databaseClient.sql(sql)
                .bind("entityType", entityType.name())
                .bind("entityId", entityId)
                .bind("alias", suggestedAlias)
                .map(row -> row.get(0, Boolean.class))
                .one();
    }

    @Override
    public Flux<AliasSuggestion> findByClientId(String clientId, Pageable pageable) {
        String sql = String.format(
                "SELECT %s FROM alias_suggestions WHERE client_id = :clientId %s LIMIT :limit OFFSET :offset",
                selectColumns(),
                getOrderByClause(pageable)
        );
        return databaseClient.sql(sql)
                .bind("clientId", clientId)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Long> countByClientId(String clientId) {
        String sql = "SELECT COUNT(*) FROM alias_suggestions WHERE client_id = :clientId";
        return databaseClient.sql(sql)
                .bind("clientId", clientId)
                .map(row -> row.get(0, Long.class))
                .one();
    }
}
