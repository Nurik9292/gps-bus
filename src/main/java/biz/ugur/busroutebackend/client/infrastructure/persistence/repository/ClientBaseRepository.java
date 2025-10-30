package biz.ugur.busroutebackend.client.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.infrastructure.mapper.ClientMapper;
import biz.ugur.busroutebackend.client.infrastructure.persistence.entity.ClientEntity;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Base repository for Client aggregate.
 * This abstract class provides common mapping and query functionality for all Client repository implementations.
 * Follows the same pattern as BannerBaseRepository and AdminBaseRepository.
 */
public abstract class ClientBaseRepository extends BaseR2dbcRepository<Client, ClientId> {

    protected ClientBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "clients", Client.class);
    }

    @Override
    protected String convertIdToDatabase(ClientId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Client> getRowMapper() {
        return this::mapRowToClient;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Client client) {
        ClientEntity entity = ClientMapper.toEntity(client);
        Map<String, Object> columns = new java.util.HashMap<>();
        columns.put("id", entity.getId());
        columns.put("name", entity.getName());
        columns.put("phone", entity.getPhone());
        columns.put("otp", entity.getOtp());
        columns.put("otp_verify", entity.getOtpVerify());
        columns.put("platform", entity.getPlatform());
        columns.put("status", entity.getStatus());
        columns.put("last_activity", entity.getLastActivity());
        columns.put("access_token", entity.getAccessToken());
        columns.put("refresh_token", entity.getRefreshToken());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        columns.put("version", entity.getVersion());
        return columns;
    }

    /**
     * Maps database row to Client domain model using ClientMapper.
     */
    private Client mapRowToClient(Row row, RowMetadata metadata) {
        return ClientMapper.toDomain(ClientEntity.builder()
                .id(row.get("id", String.class))
                .name(row.get("name", String.class))
                .phone(row.get("phone", String.class))
                .otp(row.get("otp", String.class))
                .otpVerify(row.get("otp_verify", Boolean.class))
                .platform(row.get("platform", String.class))
                .status(row.get("status", String.class))
                .lastActivity(row.get("last_activity", LocalDateTime.class))
                .accessToken(row.get("access_token", String.class))
                .refreshToken(row.get("refresh_token", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }

    // ============= Specification Pattern Support =============

    /**
     * Finds clients matching the given specification.
     * Converts specification to SQL criteria and executes query.
     *
     * @param specification the specification to match
     * @return Flux of clients matching the specification
     */
    public Flux<Client> findBySpecification(Specification<Client> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT * FROM clients WHERE %s ORDER BY created_at DESC",
                criteria.getWhereClause()
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

        // Bind all parameters from specification
        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(getRowMapper())
                .all();
    }

    /**
     * Finds clients matching the given specification with pagination.
     * Converts specification to SQL criteria, adds pagination, and executes query.
     *
     * @param specification the specification to match
     * @param pageable pagination parameters
     * @return Flux of clients matching the specification
     */
    public Flux<Client> findBySpecification(Specification<Client> specification, Pageable pageable) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT * FROM clients WHERE %s %s LIMIT :limit OFFSET :offset",
                criteria.getWhereClause(),
                getOrderByClause(pageable)
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset());

        // Bind all parameters from specification
        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(getRowMapper())
                .all();
    }

    /**
     * Counts clients matching the given specification.
     * Converts specification to SQL criteria and executes count query.
     *
     * @param specification the specification to match
     * @return Mono of Long (count of matching clients)
     */
    public Mono<Long> countBySpecification(Specification<Client> specification) {
        SqlCriteria criteria = specification.toSqlCriteria();

        String sql = String.format(
                "SELECT COUNT(*) FROM clients WHERE %s",
                criteria.getWhereClause()
        );

        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

        // Bind all parameters from specification
        for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
            executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
        }

        return executeSpec
                .map(row -> row.get(0, Long.class))
                .one();
    }
}
