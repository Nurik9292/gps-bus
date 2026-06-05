package biz.ugur.busroutebackend.subscription.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.model.SubscriptionPlanPrice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcSubscriptionPlanPriceRepository.class)
class R2dbcSubscriptionPlanPriceRepositoryIntegrationTest {

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
    @Autowired private R2dbcSubscriptionPlanPriceRepository repository;

    @BeforeEach
    void setUp() {
        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS subscription_plan_prices (
                    id           VARCHAR(16) PRIMARY KEY,
                    amount_minor BIGINT       NOT NULL,
                    currency     VARCHAR(3)   NOT NULL DEFAULT 'TMT',
                    updated_by   VARCHAR(100),
                    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    version      BIGINT       NOT NULL DEFAULT 0
                )
                """).then().block();
        databaseClient.sql("""
                INSERT INTO subscription_plan_prices (id, amount_minor, currency)
                VALUES ('MONTHLY', 400, 'TMT'), ('YEARLY', 4000, 'TMT')
                """).then().block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("DROP TABLE IF EXISTS subscription_plan_prices CASCADE").then().block();
    }

    @Test
    @DisplayName("findAllPrices returns both seeded periods ordered by id")
    void findAllPrices() {
        StepVerifier.create(repository.findAllPrices().collectList())
                .assertNext(list -> {
                    assertEquals(2, list.size());
                    assertEquals(SubscriptionPeriod.MONTHLY, list.get(0).getPeriod());
                    assertEquals(400, list.get(0).getAmountMinor());
                    assertEquals(SubscriptionPeriod.YEARLY, list.get(1).getPeriod());
                    assertEquals(4000, list.get(1).getAmountMinor());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findByPeriod resolves the row by enum key")
    void findByPeriod() {
        StepVerifier.create(repository.findByPeriod(SubscriptionPeriod.YEARLY))
                .assertNext(price -> {
                    assertEquals(SubscriptionPeriod.YEARLY, price.getPeriod());
                    assertEquals(4000, price.getAmountMinor());
                    assertEquals("TMT", price.getCurrency());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("changeAmount + save persists new price and bumps version")
    void updatePersists() {
        SubscriptionPlanPrice loaded = repository.findByPeriod(SubscriptionPeriod.MONTHLY).block();
        SubscriptionPlanPrice changed = loaded.changeAmount(550, "admin");
        repository.save(changed).block();

        StepVerifier.create(repository.findByPeriod(SubscriptionPeriod.MONTHLY))
                .assertNext(price -> {
                    assertEquals(550, price.getAmountMinor());
                    assertEquals("admin", price.getUpdatedBy());
                    assertEquals(1L, price.getVersion());
                })
                .verifyComplete();
    }
}
