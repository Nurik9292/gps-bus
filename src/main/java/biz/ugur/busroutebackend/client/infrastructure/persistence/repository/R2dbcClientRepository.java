package biz.ugur.busroutebackend.client.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
@Slf4j
@ToString
public class R2dbcClientRepository extends ClientBaseRepository implements ClientRepository {

    public R2dbcClientRepository(DatabaseClient databaseClient) {
        super(databaseClient);
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
    public Flux<Client> findByLastActivityAfter(LocalDateTime since) {
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

    @Override
    public Mono<Client> findByServiceAndExternalUserId(String serviceId, String externalUserId) {
        String sql = """
            SELECT * FROM clients
            WHERE created_by_service_id = :serviceId
            AND external_user_id = :externalUserId
            """;

        return databaseClient.sql(sql)
                .bind("serviceId", serviceId)
                .bind("externalUserId", externalUserId)
                .map(getRowMapper())
                .one()
                .doOnSuccess(client -> log.debug("Found client by service {} and external user {}",
                        serviceId, externalUserId));
    }

    @Override
    public Flux<Client> findByCreatedByServiceId(String serviceId, Pageable pageable) {
        String sql = """
            SELECT * FROM clients
            WHERE created_by_service_id = :serviceId
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """;

        return databaseClient.sql(sql)
                .bind("serviceId", serviceId)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all()
                .doOnComplete(() -> log.debug("Found clients for service {}", serviceId));
    }

    @Override
    public Mono<Long> countByCreatedByServiceId(String serviceId) {
        String sql = "SELECT COUNT(*) FROM clients WHERE created_by_service_id = :serviceId";

        return databaseClient.sql(sql)
                .bind("serviceId", serviceId)
                .map(row -> row.get(0, Long.class))
                .one()
                .doOnSuccess(count -> log.debug("Client count for service '{}': {}", serviceId, count));
    }
}