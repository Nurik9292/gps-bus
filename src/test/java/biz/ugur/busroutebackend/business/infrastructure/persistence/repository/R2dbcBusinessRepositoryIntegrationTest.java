package biz.ugur.busroutebackend.business.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.business.domain.enums.BusinessStatus;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.model.Business;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessAddress;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessName;
import biz.ugur.busroutebackend.business.domain.valueobjects.ContactInfo;
import biz.ugur.busroutebackend.business.domain.valueobjects.TaxNumber;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcBusinessRepository.class)
class R2dbcBusinessRepositoryIntegrationTest {

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
    @Autowired private R2dbcBusinessRepository repository;

    @BeforeEach
    void setUp() {
        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS businesses (
                    id                  VARCHAR(36) PRIMARY KEY,
                    name                VARCHAR(200) NOT NULL,
                    business_type       VARCHAR(40)  NOT NULL,
                    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                    tax_number          VARCHAR(32),
                    registration_number VARCHAR(64),
                    contact_person      VARCHAR(200),
                    contact_phone       VARCHAR(32)  NOT NULL,
                    contact_email       VARCHAR(200),
                    website_url         VARCHAR(500),
                    country             VARCHAR(100),
                    city                VARCHAR(100),
                    address_line        VARCHAR(500),
                    logo_url            TEXT,
                    description         TEXT,
                    rejection_reason    TEXT,
                    approved_at         TIMESTAMP WITH TIME ZONE,
                    approved_by_admin_id VARCHAR(36),
                    suspended_at        TIMESTAMP WITH TIME ZONE,
                    suspension_reason   TEXT,
                    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    version             BIGINT       NOT NULL DEFAULT 0
                )
                """).then().block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("DROP TABLE IF EXISTS businesses CASCADE").then().block();
    }

    @Test
    @DisplayName("save + findById round-trip preserves all fields")
    void saveAndFindById() {
        Business business = newPendingBusiness("Acme Cafe", "+993 65 111111", "ACME-001");

        StepVerifier.create(repository.save(business))
                .assertNext(saved -> {
                    assertNotNull(saved);
                    assertEquals("Acme Cafe", saved.getName().getValue());
                    assertEquals(BusinessStatus.PENDING, saved.getStatus());
                })
                .verifyComplete();

        StepVerifier.create(repository.findById(business.getId()))
                .assertNext(found -> {
                    assertEquals(business.getId().getValue(), found.getId().getValue());
                    assertEquals("Acme Cafe", found.getName().getValue());
                    assertEquals("+993 65 111111", found.getContactInfo().getPhone());
                    assertEquals("ACME-001", found.getTaxNumber().getValue());
                    assertEquals(BusinessType.RESTAURANT, found.getType());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("approve transitions PENDING → APPROVED and persists approvedByAdminId")
    void approveLifecycle() {
        Business pending = newPendingBusiness("TestShop", "+993 65 222222", null);
        Business saved = repository.save(pending).block();
        assertNotNull(saved);

        Business approved = saved.approve("admin-uuid-123");
        Business reloaded = repository.save(approved)
                .then(repository.findById(saved.getId()))
                .block();

        assertNotNull(reloaded);
        assertEquals(BusinessStatus.APPROVED, reloaded.getStatus());
        assertEquals("admin-uuid-123", reloaded.getApprovedByAdminId());
        assertNotNull(reloaded.getApprovedAt());
    }

    @Test
    @DisplayName("findByStatus returns only matching rows and counts correctly")
    void findByStatusPagination() {
        Business a = newPendingBusiness("A", "+993 65 300001", null);
        Business b = newPendingBusiness("B", "+993 65 300002", null);
        Business c = newPendingBusiness("C", "+993 65 300003", null);

        repository.save(a).then(repository.save(b.approve("admin"))).then(repository.save(c))
                .block();

        StepVerifier.create(repository.findByStatus(BusinessStatus.PENDING, PageRequest.of(0, 10))
                        .count())
                .assertNext(count -> assertEquals(2L, count))
                .verifyComplete();

        StepVerifier.create(repository.countByStatus(BusinessStatus.APPROVED))
                .assertNext(count -> assertEquals(1L, count))
                .verifyComplete();
    }

    @Test
    @DisplayName("existsByContactPhone and existsByTaxNumber detect duplicates")
    void uniquenessChecks() {
        Business b = newPendingBusiness("UniqueShop", "+993 65 400001", "TAX-400001");
        repository.save(b).block();

        StepVerifier.create(repository.existsByContactPhone("+993 65 400001"))
                .assertNext(v -> assertEquals(true, v))
                .verifyComplete();

        StepVerifier.create(repository.existsByContactPhone("+993 65 999999"))
                .assertNext(v -> assertEquals(false, v))
                .verifyComplete();

        StepVerifier.create(repository.existsByTaxNumber("TAX-400001"))
                .assertNext(v -> assertEquals(true, v))
                .verifyComplete();
    }

    // ---------- helpers ----------

    private static Business newPendingBusiness(String name, String phone, String tax) {
        return Business.create(
                BusinessName.of(name),
                BusinessType.RESTAURANT,
                ContactInfo.of("Contact Person", phone, null, null),
                BusinessAddress.of("Turkmenistan", "Ashgabat", "Main street 1"),
                tax != null ? TaxNumber.of(tax) : null,
                null, null, null);
    }
}
