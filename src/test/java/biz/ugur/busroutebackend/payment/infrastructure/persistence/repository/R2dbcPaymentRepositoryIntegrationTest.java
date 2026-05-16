package biz.ugur.busroutebackend.payment.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcPaymentRepository.class)
class R2dbcPaymentRepositoryIntegrationTest {

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
    @Autowired private R2dbcPaymentRepository repository;

    @BeforeEach
    void setUp() {
        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS payments (
                    id                  VARCHAR(36) PRIMARY KEY,
                    provider            VARCHAR(20)  NOT NULL,
                    provider_order_id   VARCHAR(64),
                    order_number        VARCHAR(36)  NOT NULL,
                    subject_type        VARCHAR(40)  NOT NULL,
                    subject_id          VARCHAR(36)  NOT NULL,
                    business_id         VARCHAR(36),
                    amount_minor        BIGINT       NOT NULL,
                    currency            VARCHAR(3)   NOT NULL DEFAULT 'TMT',
                    status              VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED',
                    form_url            TEXT,
                    return_url          TEXT NOT NULL,
                    initiated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    completed_at        TIMESTAMP WITH TIME ZONE,
                    failed_at           TIMESTAMP WITH TIME ZONE,
                    expires_at          TIMESTAMP WITH TIME ZONE,
                    completed_by        VARCHAR(100),
                    failure_code        VARCHAR(50),
                    failure_message     VARCHAR(512),
                    card_pan_masked     VARCHAR(32),
                    card_expiration     VARCHAR(6),
                    cardholder_name     VARCHAR(100),
                    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    version             BIGINT       NOT NULL DEFAULT 0,
                    CONSTRAINT payments_order_number_uniq UNIQUE (order_number)
                )
                """).then().block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("DROP TABLE IF EXISTS payments CASCADE").then().block();
    }

    @Test
    @DisplayName("save + findByOrderNumber returns the same payment")
    void saveAndFindByOrderNumber() {
        Payment payment = newPayment(PaymentProvider.RYSGAL);
        String orderNumber = payment.getOrderNumber().getValue();

        repository.save(payment).block();

        StepVerifier.create(repository.findByOrderNumber(orderNumber))
                .assertNext(found -> {
                    assertEquals(payment.getId(), found.getId());
                    assertEquals(PaymentProvider.RYSGAL, found.getProvider());
                    assertEquals(5000L, found.getMoney().getAmountMinor());
                    assertEquals("TMT", found.getMoney().getCurrency());
                    assertEquals(PaymentStatus.REGISTERED, found.getStatus());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("attachProviderOrder + findByProviderOrderId round-trip")
    void attachAndFindByProviderOrderId() {
        Payment payment = newPayment(PaymentProvider.SENAGAT);
        Payment attached = payment.attachProviderOrder("ext-order-42", "https://bank/form/42");

        repository.save(attached).block();

        StepVerifier.create(repository.findByProviderOrderId(PaymentProvider.SENAGAT, "ext-order-42"))
                .assertNext(found -> {
                    assertEquals(payment.getId(), found.getId());
                    assertEquals("ext-order-42", found.getProviderOrderId());
                    assertEquals("https://bank/form/42", found.getFormUrl());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("markCompleted persists COMPLETED status + card data")
    void completePayment() {
        Payment payment = newPayment(PaymentProvider.RYSGAL)
                .attachProviderOrder("ext-1", "https://form");
        repository.save(payment).block();

        Payment completed = payment.markCompleted("411111******1111", "202512", "IVAN IVANOV");
        repository.save(completed).block();

        StepVerifier.create(repository.findById(payment.getId()))
                .assertNext(found -> {
                    assertEquals(PaymentStatus.COMPLETED, found.getStatus());
                    assertNotNull(found.getCompletedAt());
                    assertEquals("411111******1111", found.getCardPanMasked());
                    assertEquals("IVAN IVANOV", found.getCardholderName());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("countByStatus reflects inserted rows correctly")
    void countByStatus() {
        Payment a = newPayment(PaymentProvider.RYSGAL).attachProviderOrder("a", "url");
        Payment b = newPayment(PaymentProvider.RYSGAL).attachProviderOrder("b", "url");
        Payment c = newPayment(PaymentProvider.SENAGAT).attachProviderOrder("c", "url");

        repository.save(a).then(repository.save(b)).then(repository.save(c)).block();

        StepVerifier.create(repository.countByStatus(PaymentStatus.REGISTERED))
                .assertNext(count -> assertEquals(3L, count))
                .verifyComplete();

        Payment completedB = b.markCompleted("4444", "202612", "TEST");
        repository.save(completedB).block();

        StepVerifier.create(repository.countByStatus(PaymentStatus.REGISTERED))
                .assertNext(count -> assertEquals(2L, count))
                .verifyComplete();
        StepVerifier.create(repository.countByStatus(PaymentStatus.COMPLETED))
                .assertNext(count -> assertEquals(1L, count))
                .verifyComplete();
    }

    @Test
    @DisplayName("findByStatus paginates correctly")
    void findByStatusPagination() {
        for (int i = 0; i < 5; i++) {
            repository.save(newPayment(PaymentProvider.RYSGAL)).block();
        }

        StepVerifier.create(repository.findByStatus(PaymentStatus.REGISTERED, PageRequest.of(0, 3))
                        .count())
                .assertNext(n -> assertEquals(3L, n))
                .verifyComplete();
    }

    // ---------- helpers ----------

    private static Payment newPayment(PaymentProvider provider) {
        return Payment.register(
                provider,
                PaymentSubjectType.AD_PLACEMENT,
                "placement-" + System.nanoTime(),
                "business-abc",
                Money.ofMinor(5_000, "TMT"),
                "https://example.com/return",
                LocalDateTime.now().plusMinutes(30));
    }
}
