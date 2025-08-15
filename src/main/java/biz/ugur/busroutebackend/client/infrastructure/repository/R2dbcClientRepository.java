package biz.ugur.busroutebackend.client.infrastructure.repository;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Instant;

@Transactional(readOnly = true)
@Repository
@RequiredArgsConstructor
@Slf4j
public class R2dbcClientRepository implements ClientRepository {

    private final DatabaseClient databaseClient;

    @Transactional
    @Override
    public Mono<Client> save(Client client) {
        return findById(client.getId())
                .switchIfEmpty(insert(client))
                .flatMap( exs -> update(client));
    }

    private Mono<Client> insert(Client client) {
        String sql = """
        INSERT INTO clients (
            id, name, phone, otp, otp_verify, platform, status,
            last_activity, access_token, refresh_token, created_at, updated_at
        ) VALUES (
            :id, :name, :phone, :otp, :otpVerify, :platform, :status,
            :lastActivity, :accessToken, :refreshToken, :createdAt, :updatedAt
        )
        """;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("id", client.getId().getValue())
                .bind("name", client.getName())
                .bind("phone", client.getPhoneNumber())
                .bind("otp", client.getOtpCode())
                .bind("otpVerify", client.getOtpVerify())
                .bind("platform", client.getPlatform().name())
                .bind("status", client.getStatus().name())
                .bind("lastActivity", client.getLastActivity());

        spec = bindOrNull(spec, "accessToken", client.getAccessToken(), String.class);
        spec = bindOrNull(spec, "refreshToken", client.getRefreshToken(), String.class);

        spec = spec
                .bind("createdAt", client.getCreatedAt())
                .bind("updatedAt", client.getUpdatedAt());

        return spec
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> rows == 1
                        ? Mono.just(client)
                        : Mono.error(new IllegalStateException("Insert failed, rowsUpdated=" + rows)));
    }

    private Mono<Client> update(Client client) {
        String sql = """
            UPDATE clients 
            SET name = :name, phone = :phone, otp = :otp, otp_verify = :otpVerify,
                platform = :platform, status = :status, last_activity = :lastActivity,
                access_token = :accessToken, refresh_token = :refreshToken, updated_at = :updatedAt
            WHERE id = :id
            """;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("id", client.getId().getValue())
                .bind("name", client.getName())
                .bind("phone", client.getPhoneNumber())
                .bind("otp", client.getOtpCode())
                .bind("otpVerify", client.getOtpVerify())
                .bind("platform", client.getPlatform().name())
                .bind("status", client.getStatus().name())
                .bind("lastActivity", client.getLastActivity());


        spec = bindOrNull(spec, "accessToken", client.getAccessToken(), String.class);
        spec = bindOrNull(spec, "refreshToken", client.getRefreshToken(), String.class);

        spec = spec
                .bind("updatedAt", client.getUpdatedAt());

        return spec.then()
                .thenReturn(client);
    }

    private <T> DatabaseClient.GenericExecuteSpec bindOrNull(DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, type);
    }

    @Override
    public Mono<Client> findById(ClientId clientId) {
        String sql = "SELECT * FROM clients WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", clientId.getValue())
                .map(this::mapRowToClient)
                .one();
    }

    @Override
    public Mono<Client> findByPhone(String phone) {
        String sql = "SELECT * FROM clients WHERE phone = :phone";

        return databaseClient.sql(sql)
                .bind("phone", phone)
                .map(this::mapRowToClient)
                .one();
    }

    @Override
    public Flux<Client> findByStatus(ClientStatus status) {
        String sql = "SELECT * FROM clients WHERE status = :status ORDER BY created_at DESC";

        return databaseClient.sql(sql)
                .bind("status", status.name())
                .map(this::mapRowToClient)
                .all();
    }

    @Override
    public Flux<Client> findActiveClients() {
        return findByStatus(ClientStatus.ACTIVE);
    }

    @Override
    public Flux<Client> findByLastActivityAfter(Instant since) {
        String sql = "SELECT * FROM clients WHERE last_activity > :since ORDER BY last_activity DESC";

        return databaseClient.sql(sql)
                .bind("since", since)
                .map(this::mapRowToClient)
                .all();
    }

    @Override
    public Mono<Boolean> existsByPhone(String phone) {
        String sql = "SELECT COUNT(*) FROM clients WHERE phone = :phone";

        return databaseClient.sql(sql)
                .bind("phone", phone)
                .map(row -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Transactional
    @Override
    public Mono<Void> deleteById(ClientId clientId) {
        String sql = "DELETE FROM clients WHERE id = :id";

        return databaseClient.sql(sql)
                .bind("id", clientId.getValue())
                .then();
    }

    @Override
    public Mono<Long> countByStatus(ClientStatus status) {
        String sql = "SELECT COUNT(*) FROM clients WHERE status = :status";

        return databaseClient.sql(sql)
                .bind("status", status.name())
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Mono<Long> countActiveClients() {
        return countByStatus(ClientStatus.ACTIVE);
    }

    private Client mapRowToClient(Row row, RowMetadata metadata) {
        Client client = new Client();

        setField(client, "id", ClientId.of(row.get("id", String.class)));
        setField(client, "name", row.get("name", String.class));
        setField(client, "phoneNumber", row.get("phone", String.class));
        setField(client, "otpCode", row.get("otp", String.class));
        setField(client, "otpVerify", row.get("otp_verify", Boolean.class));
        setField(client, "platform", Platform.valueOf(row.get("platform", String.class)));
        setField(client, "status", ClientStatus.valueOf(row.get("status", String.class)));
        setField(client, "lastActivity", row.get("last_activity", Instant.class));
        setField(client, "accessToken", row.get("access_token", String.class));
        setField(client, "refreshToken", row.get("refresh_token", String.class));

        client.setCreatedAt(row.get("created_at", Instant.class));
        client.setUpdatedAt(row.get("updated_at", Instant.class));

        return client;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            log.warn("Failed to set field {}: {}", fieldName, e.getMessage());
        }
    }
}
