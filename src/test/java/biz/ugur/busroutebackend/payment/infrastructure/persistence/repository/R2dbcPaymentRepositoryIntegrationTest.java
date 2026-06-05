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
import java.util.Collections;
import java.util.List;

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
                    fail_url            TEXT,
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
        Payment payment = repository.save(newPayment(PaymentProvider.RYSGAL)
                .attachProviderOrder("ext-1", "https://form")).block();

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

        repository.save(a).block();
        Payment savedB = repository.save(b).block();
        repository.save(c).block();

        StepVerifier.create(repository.countByStatus(PaymentStatus.REGISTERED))
                .assertNext(count -> assertEquals(3L, count))
                .verifyComplete();

        repository.save(savedB.markCompleted("4444", "202612", "TEST")).block();

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

    @Test
    @DisplayName("save + find round-trips fail_url")
    void failUrlRoundTrip() {
        Payment withFail = Payment.register(
                PaymentProvider.HALK,
                PaymentSubjectType.CLIENT_SUBSCRIPTION,
                "sub-" + System.nanoTime(),
                null,
                Money.ofMinor(400, "TMT"),
                "https://admduralga.ulgam.biz/api/v1/payments/return/HALK",
                "https://admduralga.ulgam.biz/api/v1/payments/return/HALK",
                LocalDateTime.now().plusMinutes(30));

        repository.save(withFail).block();

        StepVerifier.create(repository.findById(withFail.getId()))
                .assertNext(found -> {
                    assertEquals("https://admduralga.ulgam.biz/api/v1/payments/return/HALK", found.getReturnUrl());
                    assertEquals("https://admduralga.ulgam.biz/api/v1/payments/return/HALK", found.getFailUrl());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findBySubjectTypeAndSubjectIdIn filters by subject IN-list and status")
    void findBySubjectIn() {
        repository.save(newSubscriptionPayment("sub-1", PaymentProvider.HALK)).block();
        repository.save(newSubscriptionPayment("sub-2", PaymentProvider.RYSGAL)).block();
        repository.save(newSubscriptionPayment("sub-3", PaymentProvider.HALK)).block();
        repository.save(newPayment(PaymentProvider.HALK)).block();

        StepVerifier.create(repository.findBySubjectTypeAndSubjectIdIn(
                        PaymentSubjectType.CLIENT_SUBSCRIPTION, List.of("sub-1", "sub-2"),
                        null, null, null, PageRequest.of(0, 50)).count())
                .assertNext(n -> assertEquals(2L, n))
                .verifyComplete();

        StepVerifier.create(repository.countBySubjectTypeAndSubjectIdIn(
                        PaymentSubjectType.CLIENT_SUBSCRIPTION, List.of("sub-1", "sub-2", "sub-3"),
                        null, null, null))
                .assertNext(n -> assertEquals(3L, n))
                .verifyComplete();
    }

    @Test
    @DisplayName("findBySubjectTypeAndSubjectIdIn with COMPLETED status filter")
    void findBySubjectInWithStatus() {
        Payment p1 = repository.save(
                newSubscriptionPayment("sub-10", PaymentProvider.HALK).attachProviderOrder("o10", "url")).block();
        repository.save(newSubscriptionPayment("sub-11", PaymentProvider.HALK)).block();
        repository.save(p1.markCompleted("4444", "202612", "TEST")).block();

        StepVerifier.create(repository.countBySubjectTypeAndSubjectIdIn(
                        PaymentSubjectType.CLIENT_SUBSCRIPTION, List.of("sub-10", "sub-11"),
                        PaymentStatus.COMPLETED, null, null))
                .assertNext(n -> assertEquals(1L, n))
                .verifyComplete();
    }

    @Test
    @DisplayName("findBySubjectTypeAndSubjectIdIn with empty id list returns nothing")
    void findBySubjectInEmpty() {
        repository.save(newSubscriptionPayment("sub-20", PaymentProvider.HALK)).block();

        StepVerifier.create(repository.findBySubjectTypeAndSubjectIdIn(
                        PaymentSubjectType.CLIENT_SUBSCRIPTION, Collections.emptyList(),
                        null, null, null, PageRequest.of(0, 50)).count())
                .assertNext(n -> assertEquals(0L, n))
                .verifyComplete();
        StepVerifier.create(repository.countBySubjectTypeAndSubjectIdIn(
                        PaymentSubjectType.CLIENT_SUBSCRIPTION, Collections.emptyList(), null, null, null))
                .assertNext(n -> assertEquals(0L, n))
                .verifyComplete();
    }

    @Test
    @DisplayName("findLatestBySubject returns the most recent by initiated_at")
    void findLatestBySubject() {
        Payment older = newSubscriptionPayment("sub-30", PaymentProvider.HALK);
        Payment newer = newSubscriptionPayment("sub-30", PaymentProvider.RYSGAL);
        repository.save(older).block();
        repository.save(newer).block();
        databaseClient.sql("UPDATE payments SET initiated_at = NOW() - INTERVAL '10 days' WHERE id = :id")
                .bind("id", older.getId().getValue())
                .then().block();

        StepVerifier.create(repository.findLatestBySubject(
                        PaymentSubjectType.CLIENT_SUBSCRIPTION, "sub-30"))
                .assertNext(found -> assertEquals(newer.getId(), found.getId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("countBySubjectTypeAndSubjectIdInGroupByStatus aggregates per status")
    void groupByStatus() {
        Payment completed = repository.save(
                newSubscriptionPayment("sub-40", PaymentProvider.HALK).attachProviderOrder("o40", "url")).block();
        repository.save(completed.markCompleted("4444", "202612", "TEST")).block();
        repository.save(newSubscriptionPayment("sub-40", PaymentProvider.RYSGAL)).block();
        repository.save(newSubscriptionPayment("sub-41", PaymentProvider.HALK)).block();

        StepVerifier.create(repository.countBySubjectTypeAndSubjectIdInGroupByStatus(
                        PaymentSubjectType.CLIENT_SUBSCRIPTION, List.of("sub-40", "sub-41")))
                .assertNext(counts -> {
                    assertEquals(1L, counts.get("COMPLETED"));
                    assertEquals(2L, counts.get("REGISTERED"));
                })
                .verifyComplete();
    }

    // ---------- helpers ----------

    private static Payment newSubscriptionPayment(String subjectId, PaymentProvider provider) {
        return Payment.register(
                provider,
                PaymentSubjectType.CLIENT_SUBSCRIPTION,
                subjectId,
                null,
                Money.ofMinor(400, "TMT"),
                "https://example.com/return/" + provider.name(),
                LocalDateTime.now().plusMinutes(30));
    }

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
