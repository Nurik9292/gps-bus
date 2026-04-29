package biz.ugur.busroutebackend.admin.infrastructure.repository;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.specification.AdminSpecifications;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.admin.infrastructure.persistence.repository.R2dbcAdminRepository;
import biz.ugur.busroutebackend.shared.domain.services.PasswordEncoder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
@Import(R2dbcAdminRepository.class)
class R2dbcAdminRepositoryIntegrationTest {

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

    private R2dbcAdminRepository repository;

    private final PasswordEncoder passwordEncoder = new PasswordEncoder() {
        @Override
        public String encode(String rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword != null && encodedPassword.equals("encoded:" + rawPassword);
        }
    };

    @BeforeEach
    void setUp() {
        repository = new R2dbcAdminRepository(databaseClient);

        String createTableSql = """
            CREATE TABLE IF NOT EXISTS admins (
                id VARCHAR(36) PRIMARY KEY,
                username VARCHAR(100) NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                full_name VARCHAR(255) NOT NULL,
                avatar VARCHAR(500),
                is_active BOOLEAN DEFAULT true,
                is_super_admin BOOLEAN DEFAULT false,
                last_login_at TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                version BIGINT DEFAULT 0
            )
            """;

        databaseClient.sql(createTableSql)
                .then()
                .block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("DROP TABLE IF EXISTS admins CASCADE")
                .then()
                .block();
    }

    @Test
    void save_ShouldPersistAdminSuccessfully() {
        Admin admin = Admin.create("testadmin", passwordEncoder.encode("password123"), "Test Admin", null, false, true);

        StepVerifier.create(repository.save(admin))
                .assertNext(savedAdmin -> {
                    assertNotNull(savedAdmin);
                    assertEquals("testadmin", savedAdmin.getUsername());
                    assertEquals("Test Admin", savedAdmin.getFullName());
                    assertTrue(savedAdmin.getIsActive());
                    assertFalse(savedAdmin.getIsSuperAdmin());
                })
                .verifyComplete();
    }

    @Test
    void findById_ShouldRetrieveExistingAdmin() {
        Admin admin = Admin.create("admin1", passwordEncoder.encode("password"), "Admin One", null, false, true);
        Admin savedAdmin = repository.save(admin).block();
        assertNotNull(savedAdmin);

        StepVerifier.create(repository.findById(savedAdmin.getId()))
                .assertNext(found -> {
                    assertEquals(savedAdmin.getId(), found.getId());
                    assertEquals("admin1", found.getUsername());
                })
                .verifyComplete();
    }

    @Test
    void findById_ShouldReturnEmpty_ForNonExistentAdmin() {
        AdminId nonExistentId = AdminId.generate();

        StepVerifier.create(repository.findById(nonExistentId))
                .verifyComplete();
    }

    @Test
    void findByUsername_ShouldRetrieveAdmin() {
        Admin admin = Admin.create("uniqueuser", passwordEncoder.encode("password"), "Unique User", null, false, true);
        repository.save(admin).block();

        StepVerifier.create(repository.findByUsername("uniqueuser"))
                .assertNext(found -> {
                    assertEquals("uniqueuser", found.getUsername());
                    assertEquals("Unique User", found.getFullName());
                })
                .verifyComplete();
    }

    @Test
    void findByUsername_ShouldReturnEmpty_ForNonExistentUsername() {
        StepVerifier.create(repository.findByUsername("nonexistent"))
                .verifyComplete();
    }

    @Test
    void existsByUsername_ShouldReturnTrue_WhenExists() {
        Admin admin = Admin.create("existinguser", passwordEncoder.encode("password"), "Existing User", null, false, true);
        repository.save(admin).block();

        StepVerifier.create(repository.existsByUsername("existinguser"))
                .assertNext(Assertions::assertTrue)
                .verifyComplete();
    }

    @Test
    void existsByUsername_ShouldReturnFalse_WhenNotExists() {
        StepVerifier.create(repository.existsByUsername("nonexistent"))
                .assertNext(Assertions::assertFalse)
                .verifyComplete();
    }

    @Test
    void update_ShouldUpdateAdminSuccessfully() {
        Admin admin = Admin.create("updatetest", passwordEncoder.encode("password"), "Original Name", null, false, true);
        Admin savedAdmin = repository.save(admin).block();
        assertNotNull(savedAdmin);

        Admin updatedAdmin = savedAdmin.updateProfile("updatetest", "Updated Name");

        StepVerifier.create(repository.save(updatedAdmin))
                .assertNext(saved -> {
                    assertEquals("Updated Name", saved.getFullName());
                    assertEquals("updatetest", saved.getUsername());
                })
                .verifyComplete();
    }

    @Test
    void deleteById_ShouldRemoveAdmin() {
        Admin admin = Admin.create("deleteme", passwordEncoder.encode("password"), "Delete Me", null, false, true);
        Admin savedAdmin = repository.save(admin).block();
        assertNotNull(savedAdmin);

        StepVerifier.create(repository.deleteById(savedAdmin.getId()))
                .verifyComplete();

        StepVerifier.create(repository.findById(savedAdmin.getId()))
                .verifyComplete();
    }

    @Test
    void immutabilitySupport_ChangePassword() {
        Admin admin = Admin.create("passtest", passwordEncoder.encode("oldpassword"), "Password Test", null, false, true);
        Admin savedAdmin = repository.save(admin).block();
        assertNotNull(savedAdmin);

        Admin adminWithNewPassword = savedAdmin.changePassword(passwordEncoder.encode("newpassword"));

        StepVerifier.create(repository.save(adminWithNewPassword))
                .assertNext(updated -> {
                    assertTrue(updated.checkPassword("newpassword", passwordEncoder));
                    assertFalse(updated.checkPassword("oldpassword", passwordEncoder));
                })
                .verifyComplete();

        assertTrue(savedAdmin.checkPassword("oldpassword", passwordEncoder));
    }

    @Test
    void immutabilitySupport_ActivateDeactivate() {
        Admin admin = Admin.create("activetest", passwordEncoder.encode("password"), "Active Test", null, false, true);
        Admin savedAdmin = repository.save(admin).block();
        assertNotNull(savedAdmin);
        assertTrue(savedAdmin.getIsActive());

        Admin deactivatedAdmin = savedAdmin.deactivate();

        StepVerifier.create(repository.save(deactivatedAdmin))
                .assertNext(saved -> {
                    assertFalse(saved.getIsActive());
                })
                .verifyComplete();

        assertTrue(savedAdmin.getIsActive());

        Admin reactivatedAdmin = deactivatedAdmin.activate();

        StepVerifier.create(repository.save(reactivatedAdmin))
                .assertNext(saved -> {
                    assertTrue(saved.getIsActive());
                })
                .verifyComplete();
    }

    @Test
    void immutabilitySupport_UpdateAvatar() {
        Admin admin = Admin.create("avatartest", passwordEncoder.encode("password"), "Avatar Test", null, false, true);
        Admin savedAdmin = repository.save(admin).block();
        assertNotNull(savedAdmin);
        assertNull(savedAdmin.getAvatar());

        Admin adminWithAvatar = savedAdmin.updateAvatar("avatar.jpg");

        StepVerifier.create(repository.save(adminWithAvatar))
                .assertNext(saved -> {
                    assertEquals("avatar.jpg", saved.getAvatar());
                })
                .verifyComplete();

        assertNull(savedAdmin.getAvatar());

        Admin adminWithoutAvatar = adminWithAvatar.removeAvatar();

        StepVerifier.create(repository.save(adminWithoutAvatar))
                .assertNext(saved -> {
                    assertNull(saved.getAvatar());
                })
                .verifyComplete();
    }

    @Test
    void findAll_ShouldReturnAllAdmins() {
        Admin admin1 = Admin.create("admin1", passwordEncoder.encode("pass1"), "Admin One", null, false, true);
        Admin admin2 = Admin.create("admin2", passwordEncoder.encode("pass2"), "Admin Two", null, true, true);
        repository.save(admin1).block();
        repository.save(admin2).block();

        StepVerifier.create(repository.findAll())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findBySpecification_WithCamelCaseSortField_ShouldNotThrow() {
        repository.save(Admin.create("alpha", passwordEncoder.encode("pw"), "Alpha", null, false, true)).block();
        repository.save(Admin.create("beta",  passwordEncoder.encode("pw"), "Beta",  null, false, false)).block();

        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

        StepVerifier.create(repository.findBySpecification(AdminSpecifications.isActive(), pageable).collectList())
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals("alpha", list.get(0).getUsername());
                })
                .verifyComplete();
    }

    @Test
    void findBySpecification_WithDefaultControllerSortField_ShouldSucceed() {
        repository.save(Admin.create("gamma", passwordEncoder.encode("pw"), "Gamma", null, false, true)).block();

        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "created_at"));

        StepVerifier.create(repository.findBySpecification(AdminSpecifications.isActive(), pageable).collectList())
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void findBySpecification_WithUnknownSortField_ShouldFallBackToCreatedAt() {
        repository.save(Admin.create("delta", passwordEncoder.encode("pw"), "Delta", null, false, true)).block();

        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "doesNotExist"));

        StepVerifier.create(repository.findBySpecification(AdminSpecifications.isActive(), pageable).collectList())
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void saveAndRetrieve_ShouldPreserveAllProperties() {
        Admin admin = Admin.create("complete", passwordEncoder.encode("password"), "Complete Admin", null, true, true);
        Admin adminWithAvatar = admin.updateAvatar("test-avatar.jpg");

        Admin saved = repository.save(adminWithAvatar).block();
        assertNotNull(saved);

        StepVerifier.create(repository.findById(saved.getId()))
                .assertNext(retrieved -> {
                    assertEquals("complete", retrieved.getUsername());
                    assertEquals("Complete Admin", retrieved.getFullName());
                    assertEquals("test-avatar.jpg", retrieved.getAvatar());
                    assertTrue(retrieved.getIsActive());
                    assertTrue(retrieved.getIsSuperAdmin());
                    assertNotNull(retrieved.getLastLoginAt());
                    assertNotNull(retrieved.getCreatedAt());
                    assertNotNull(retrieved.getUpdatedAt());
                    assertEquals(1L, retrieved.getVersion());
                })
                .verifyComplete();
    }
}
