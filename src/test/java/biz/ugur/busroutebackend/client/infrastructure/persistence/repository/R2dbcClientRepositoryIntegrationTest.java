package biz.ugur.busroutebackend.client.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcClientRepository.class)
class R2dbcClientRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private R2dbcClientRepository repository;

    @BeforeEach
    void setUp() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS clients (
                id VARCHAR(36) PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                phone VARCHAR(20) NOT NULL UNIQUE,
                otp VARCHAR(5),
                otp_verify BOOLEAN DEFAULT FALSE,
                platform VARCHAR(20) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
                last_activity TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                access_token TEXT,
                refresh_token TEXT,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                version BIGINT DEFAULT 0,
                created_by_service_id VARCHAR(36),
                external_user_id VARCHAR(255)
            )
            """;

        databaseClient.sql(createTableSql).then().block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("DROP TABLE IF EXISTS clients CASCADE").then().block();
    }

    @Test
    void save_persistsClientAndReturnsHydrated() {
        Client client = Client.create("Alice", "+99361000001", Platform.ANDROID);

        StepVerifier.create(repository.save(client))
                .assertNext(saved -> {
                    assertNotNull(saved);
                    assertEquals("Alice", saved.getName());
                    assertEquals("+99361000001", saved.getPhoneNumber());
                    assertEquals(Platform.ANDROID, saved.getPlatform());
                    assertEquals(ClientStatus.INACTIVE, saved.getStatus());
                })
                .verifyComplete();
    }

    @Test
    void findByIds_returnsOnlyRequestedClients() {
        Client a = Client.create("Alice", "+99361000010", Platform.ANDROID);
        Client b = Client.create("Bob", "+99361000011", Platform.IOS);
        Client c = Client.create("Carol", "+99361000012", Platform.ANDROID);
        repository.save(a).then(repository.save(b)).then(repository.save(c)).block();

        StepVerifier.create(repository.findByIds(java.util.List.of(
                        a.getId().getValue(), b.getId().getValue())).collectList())
                .assertNext(list -> {
                    assertEquals(2, list.size());
                    assertTrue(list.stream().anyMatch(x -> "Alice".equals(x.getName())));
                    assertTrue(list.stream().anyMatch(x -> "Bob".equals(x.getName())));
                })
                .verifyComplete();
    }

    @Test
    void findByIds_emptyInput_returnsEmpty() {
        StepVerifier.create(repository.findByIds(java.util.List.of()).collectList())
                .assertNext(list -> assertTrue(list.isEmpty()))
                .verifyComplete();
    }

    @Test
    void findByPhone_returnsExistingClient() {
        Client client = Client.create("Bob", "+99361000002", Platform.IOS);
        repository.save(client).block();

        StepVerifier.create(repository.findByPhone("+99361000002"))
                .assertNext(found -> {
                    assertEquals("Bob", found.getName());
                    assertEquals(Platform.IOS, found.getPlatform());
                })
                .verifyComplete();
    }

    @Test
    void findByPhone_emptyForUnknownPhone() {
        StepVerifier.create(repository.findByPhone("+99361999999"))
                .verifyComplete();
    }

    @Test
    void existsByPhone_truthyForExisting() {
        repository.save(Client.create("Carol", "+99361000003", Platform.WEB)).block();

        StepVerifier.create(repository.existsByPhone("+99361000003"))
                .assertNext(exists -> assertTrue(exists))
                .verifyComplete();

        StepVerifier.create(repository.existsByPhone("+99361999999"))
                .assertNext(exists -> assertFalse(exists))
                .verifyComplete();
    }

    @Test
    void findByStatus_filtersOnStatusColumn() {
        Client active = Client.create("Active", "+99361000004", Platform.ANDROID).activate();
        Client inactive = Client.create("Inactive", "+99361000005", Platform.ANDROID);
        repository.save(active).block();
        repository.save(inactive).block();

        StepVerifier.create(repository.findByStatus(ClientStatus.ACTIVE))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void countActiveClients_returnsExactCount() {
        repository.save(Client.create("A1", "+99361000010", Platform.ANDROID).activate()).block();
        repository.save(Client.create("A2", "+99361000011", Platform.ANDROID).activate()).block();
        repository.save(Client.create("I1", "+99361000012", Platform.ANDROID)).block();

        StepVerifier.create(repository.countActiveClients())
                .assertNext(count -> assertEquals(2L, count))
                .verifyComplete();
    }

    @Test
    void findByServiceAndExternalUserId_locatesIntegrationClient() {
        Client integration = Client.createViaExternalService("ExternalUser", "svc-1", "ext-user-42");
        repository.save(integration).block();

        StepVerifier.create(repository.findByServiceAndExternalUserId("svc-1", "ext-user-42"))
                .assertNext(found -> {
                    assertEquals("ExternalUser", found.getName());
                    assertEquals("svc-1", found.getCreatedByServiceId());
                    assertEquals("ext-user-42", found.getExternalUserId());
                })
                .verifyComplete();
    }

    @Test
    void saveAndUpdate_optimisticLockingIncrementsVersion() {
        Client created = repository.save(Client.create("Versioned", "+99361000020", Platform.ANDROID)).block();
        assertNotNull(created);
        Long initialVersion = created.getVersion();

        Client updated = created.activate();
        Client persistedUpdate = repository.save(updated).block();
        assertNotNull(persistedUpdate);

        assertTrue(persistedUpdate.getVersion() > initialVersion,
                "Version must increase on update");
    }

    @Test
    void saveAndRetrieve_preservesAllPersistedFields() {
        Client client = Client.create("Full", "+99361000030", Platform.ANDROID);
        Client saved = repository.save(client).block();
        assertNotNull(saved);

        StepVerifier.create(repository.findById(saved.getId()))
                .assertNext(retrieved -> {
                    assertEquals("Full", retrieved.getName());
                    assertEquals("+99361000030", retrieved.getPhoneNumber());
                    assertEquals(Platform.ANDROID, retrieved.getPlatform());
                    assertEquals(ClientStatus.INACTIVE, retrieved.getStatus());
                    assertNotNull(retrieved.getLastActivity());
                    assertNotNull(retrieved.getCreatedAt());
                    assertNotNull(retrieved.getUpdatedAt());
                })
                .verifyComplete();
    }
}
