package biz.ugur.busroutebackend.client.infrastructure.repository;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Repository
@Slf4j
public class R2dbcClientRepository extends BaseR2dbcRepository<Client, ClientId> implements ClientRepository {

    public R2dbcClientRepository(DatabaseClient databaseClient) {
        super(databaseClient, "clients", Client.class);
    }

    @Override
    protected String convertIdToDatabase(ClientId clientId) {
        return clientId.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Client> getRowMapper() {
        return this::mapRowToClient;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Client client) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", client.getId().getValue());
        values.put("name", client.getName());
        values.put("phone", client.getPhoneNumber());
        values.put("otp", client.getOtpCode());
        values.put("otp_verify", client.getOtpVerify());
        values.put("platform", client.getPlatform().name());
        values.put("status", client.getStatus().name());
        values.put("last_activity", client.getLastActivity());
        values.put("access_token", client.getAccessToken());
        values.put("refresh_token", client.getRefreshToken());
        return values;
    }

    @Override
    public Mono<Client> findByPhone(String phone) {
        String sql = "SELECT * FROM clients WHERE phone = :phone";

        return databaseClient.sql(sql)
                .bind("phone", phone)
                .map(getRowMapper())
                .one()
                .doOnSuccess(client -> log.debug("Found client by phone: {}", phone))
                .doOnError(error -> log.error("Failed to find client by phone: {}", phone, error));
    }

    @Override
    public Flux<Client> findByStatus(ClientStatus status) {
        String sql = "SELECT * FROM clients WHERE status = :status ORDER BY created_at DESC";

        return databaseClient.sql(sql)
                .bind("status", status.name())
                .map(getRowMapper())
                .all()
                .doOnComplete(() -> log.debug("Found clients by status: {}", status))
                .doOnError(error -> log.error("Failed to find clients by status: {}", status, error));
    }

    @Override
    public Flux<Client> findActiveClients() {
        return findByStatus(ClientStatus.ACTIVE)
                .doOnComplete(() -> log.debug("Found active clients"));
    }

    @Override
    public Flux<Client> findByLastActivityAfter(Instant since) {
        String sql = "SELECT * FROM clients WHERE last_activity > :since ORDER BY last_activity DESC";

        return databaseClient.sql(sql)
                .bind("since", since)
                .map(getRowMapper())
                .all()
                .doOnComplete(() -> log.debug("Found clients with last activity after: {}", since))
                .doOnError(error -> log.error("Failed to find clients by last activity", error));
    }

    @Override
    public Mono<Boolean> existsByPhone(String phone) {
        String sql = "SELECT COUNT(*) FROM clients WHERE phone = :phone";

        return databaseClient.sql(sql)
                .bind("phone", phone)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0)
                .doOnSuccess(exists -> log.debug("Client exists check for phone '{}': {}", phone, exists));
    }

    @Override
    public Mono<Long> countByStatus(ClientStatus status) {
        String sql = "SELECT COUNT(*) FROM clients WHERE status = :status";

        return databaseClient.sql(sql)
                .bind("status", status.name())
                .map(row -> row.get(0, Long.class))
                .one()
                .doOnSuccess(count -> log.debug("Client count for status '{}': {}", status, count));
    }

    @Override
    public Mono<Long> countActiveClients() {
        return countByStatus(ClientStatus.ACTIVE)
                .doOnSuccess(count -> log.debug("Active clients count: {}", count));
    }

    private Client mapRowToClient(Row row, RowMetadata metadata) {
        return Client.fromDatabase(
                ClientId.of(row.get("id", String.class)),
                row.get("name", String.class),
                row.get("phone", String.class),
                row.get("otp", String.class),
                row.get("otp_verify", Boolean.class),
                Platform.valueOf(row.get("platform", String.class)),
                ClientStatus.valueOf(row.get("status", String.class)),
                row.get("last_activity", Instant.class),
                row.get("access_token", String.class),
                row.get("refresh_token", String.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class)
        );
    }
}