package biz.ugur.busroutebackend.subscription.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcSubscriptionRepository.class)
class R2dbcSubscriptionRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url",
                () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired private DatabaseClient databaseClient;
    @Autowired private R2dbcSubscriptionRepository repository;

    @BeforeEach
    void setUp() {
        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS client_subscriptions (
                    id                  VARCHAR(36) PRIMARY KEY,
                    client_id           VARCHAR(36)  NOT NULL,
                    period              VARCHAR(16)  NOT NULL,
                    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                    payment_id          VARCHAR(36),
                    amount_minor        BIGINT       NOT NULL,
                    currency            VARCHAR(3)   NOT NULL DEFAULT 'TMT',
                    started_at          TIMESTAMP WITH TIME ZONE,
                    expires_at          TIMESTAMP WITH TIME ZONE,
                    cancelled_at        TIMESTAMP WITH TIME ZONE,
                    cancellation_reason VARCHAR(255),
                    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    version             BIGINT       NOT NULL DEFAULT 0
                )
                """).then().block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("DROP TABLE IF EXISTS client_subscriptions CASCADE").then().block();
    }

    private void seed() {
        Subscription activeMonthlyA = Subscription.initiate("client-A", SubscriptionPeriod.MONTHLY, 400, "TMT")
                .activate(LocalDateTime.now());
        Subscription pendingMonthlyA = Subscription.initiate("client-A", SubscriptionPeriod.MONTHLY, 400, "TMT");
        Subscription activeYearlyB = Subscription.initiate("client-B", SubscriptionPeriod.YEARLY, 4000, "TMT")
                .activate(LocalDateTime.now());
        Subscription expiredActiveD = Subscription.initiate("client-D", SubscriptionPeriod.MONTHLY, 400, "TMT")
                .activate(LocalDateTime.now().minusDays(40));

        repository.save(activeMonthlyA)
                .then(repository.save(pendingMonthlyA))
                .then(repository.save(activeYearlyB))
                .then(repository.save(expiredActiveD))
                .block();
    }

    @Test
    @DisplayName("save + findById round-trips all fields")
    void saveAndFindById() {
        Subscription sub = Subscription.initiate("client-X", SubscriptionPeriod.YEARLY, 4000, "TMT");
        repository.save(sub).block();

        StepVerifier.create(repository.findById(sub.getId()))
                .assertNext(found -> {
                    assertEquals("client-X", found.getClientId());
                    assertEquals(SubscriptionPeriod.YEARLY, found.getPeriod());
                    assertEquals(SubscriptionStatus.PENDING, found.getStatus());
                    assertEquals(4000, found.getAmountMinor());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findPaginated applies status and period filters")
    void findPaginatedFilters() {
        seed();

        StepVerifier.create(repository.findPaginated(null, null, PageRequest.of(0, 50)).count())
                .assertNext(n -> assertEquals(4L, n)).verifyComplete();

        StepVerifier.create(repository.findPaginated(SubscriptionStatus.ACTIVE, null, PageRequest.of(0, 50)).count())
                .assertNext(n -> assertEquals(3L, n)).verifyComplete();

        StepVerifier.create(repository.findPaginated(null, SubscriptionPeriod.MONTHLY, PageRequest.of(0, 50)).count())
                .assertNext(n -> assertEquals(3L, n)).verifyComplete();

        StepVerifier.create(repository.findPaginated(SubscriptionStatus.ACTIVE, SubscriptionPeriod.MONTHLY, PageRequest.of(0, 50)).count())
                .assertNext(n -> assertEquals(2L, n)).verifyComplete();
    }

    @Test
    @DisplayName("countFiltered matches the filtered set")
    void countFiltered() {
        seed();

        StepVerifier.create(repository.countFiltered(null, null))
                .assertNext(n -> assertEquals(4L, n)).verifyComplete();
        StepVerifier.create(repository.countFiltered(SubscriptionStatus.ACTIVE, SubscriptionPeriod.MONTHLY))
                .assertNext(n -> assertEquals(2L, n)).verifyComplete();
        StepVerifier.create(repository.countFiltered(SubscriptionStatus.PENDING, null))
                .assertNext(n -> assertEquals(1L, n)).verifyComplete();
    }

    @Test
    @DisplayName("findAllByClientId returns only that client's subscriptions")
    void findAllByClientId() {
        seed();

        StepVerifier.create(repository.findAllByClientId("client-A").count())
                .assertNext(n -> assertEquals(2L, n)).verifyComplete();
        StepVerifier.create(repository.findAllByClientId("client-B").count())
                .assertNext(n -> assertEquals(1L, n)).verifyComplete();
    }

    @Test
    @DisplayName("findExpiredActive returns only ACTIVE rows past expires_at")
    void findExpiredActive() {
        seed();

        StepVerifier.create(repository.findExpiredActive(PageRequest.of(0, 50)).collectList())
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals("client-D", list.get(0).getClientId());
                })
                .verifyComplete();
    }
}
