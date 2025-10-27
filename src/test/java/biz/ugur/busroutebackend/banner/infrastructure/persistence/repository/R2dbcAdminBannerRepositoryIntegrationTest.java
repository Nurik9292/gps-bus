package biz.ugur.busroutebackend.banner.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;


@DataR2dbcTest
@Testcontainers
@Import(R2dbcAdminBannerRepository.class)
class R2dbcAdminBannerRepositoryIntegrationTest {

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

    private R2dbcAdminBannerRepository repository;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        repository = new R2dbcAdminBannerRepository(databaseClient);
        now = LocalDateTime.now();

        // Create minimal banners table schema
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS banners (
                id UUID PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                type VARCHAR(50) NOT NULL,
                image_url VARCHAR(500) NOT NULL,
                target_url VARCHAR(500),
                is_active BOOLEAN DEFAULT true,
                display_order INTEGER DEFAULT 0,
                start_date TIMESTAMP,
                end_date TIMESTAMP,
                content TEXT,
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
        // Clean up test data
        databaseClient.sql("DROP TABLE IF EXISTS banners CASCADE")
                .then()
                .block();
    }

    @Test
    void save_ShouldPersistBannerSuccessfully() {
        // Given
        Banner banner = Banner.create(
                BannerTitle.of("Test Banner"),
                BannerType.MAIN,
                BannerPeriod.between(now, now.plusDays(7)),
                BannerImage.of("test.jpg"),
                "http://test.com",
                1,
                "Test content"
        );

        // When & Then
        StepVerifier.create(repository.save(banner))
                .assertNext(savedBanner -> {
                    assertNotNull(savedBanner);
                    assertNotNull(savedBanner.getId());
                    assertEquals("Test Banner", savedBanner.getTitle().getValue());
                    assertEquals(BannerType.MAIN, savedBanner.getType());
                    assertTrue(savedBanner.getIsActive());
                })
                .verifyComplete();
    }

    @Test
    void findById_ShouldRetrieveExistingBanner() {
        // Given
        Banner banner = Banner.create(
                BannerTitle.of("Test Banner"),
                BannerType.MAIN,
                BannerPeriod.between(now, now.plusDays(7)),
                BannerImage.of("test.jpg"),
                "http://test.com",
                1,
                "Test content"
        );

        Banner savedBanner = repository.save(banner).block();
        assertNotNull(savedBanner);

        // When & Then
        StepVerifier.create(repository.findById(savedBanner.getId()))
                .assertNext(found -> {
                    assertEquals(savedBanner.getId(), found.getId());
                    assertEquals("Test Banner", found.getTitle().getValue());
                })
                .verifyComplete();
    }

    @Test
    void findById_ShouldReturnEmptyForNonExistentBanner() {
        // Given
        BannerId nonExistentId = BannerId.generate();

        // When & Then
        StepVerifier.create(repository.findById(nonExistentId))
                .verifyComplete();
    }

    @Test
    void update_ShouldUpdateBannerSuccessfully() {
        // Given - Create and save initial banner
        Banner banner = Banner.create(
                BannerTitle.of("Original Title"),
                BannerType.MAIN,
                BannerPeriod.between(now, now.plusDays(7)),
                BannerImage.of("original.jpg"),
                "http://original.com",
                1,
                "Original content"
        );

        Banner savedBanner = repository.save(banner).block();
        assertNotNull(savedBanner);

        // When - Update the banner
        Banner updatedBanner = savedBanner.updateBanner(
                BannerTitle.of("Updated Title"),
                BannerType.MAIN,
                BannerPeriod.between(now, now.plusDays(14)),
                BannerImage.of("updated.jpg"),
                "http://updated.com",
                2,
                "Updated content"
        );

        // Then
        StepVerifier.create(repository.save(updatedBanner))
                .assertNext(saved -> {
                    assertEquals("Updated Title", saved.getTitle().getValue());
                    assertEquals("updated.jpg", saved.getImageUrl().getValue());
                    assertEquals("http://updated.com", saved.getTargetUrl());
                    assertEquals(2, saved.getDisplayOrder());
                })
                .verifyComplete();
    }

    @Test
    void delete_ShouldRemoveBannerSuccessfully() {
        // Given
        Banner banner = Banner.create(
                BannerTitle.of("To Delete"),
                BannerType.MAIN,
                BannerPeriod.between(now, now.plusDays(7)),
                BannerImage.of("delete.jpg"),
                "http://delete.com",
                1,
                "Delete me"
        );

        Banner savedBanner = repository.save(banner).block();
        assertNotNull(savedBanner);

        // When
        StepVerifier.create(repository.deleteById(savedBanner.getId()))
                .verifyComplete();

        // Then - Verify banner is deleted
        StepVerifier.create(repository.findById(savedBanner.getId()))
                .verifyComplete();
    }

    @Test
    void findActiveBanners_ShouldReturnOnlyActiveBannersInPeriod() {
        // Given - Create active banner within period
        Banner activeBanner = Banner.create(
                BannerTitle.of("Active Banner"),
                BannerType.MAIN,
                BannerPeriod.between(now.minusDays(1), now.plusDays(7)),
                BannerImage.of("active.jpg"),
                "http://active.com",
                1,
                "Active"
        );
        repository.save(activeBanner).block();

        // Create inactive banner
        Banner inactiveBanner = Banner.create(
                BannerTitle.of("Inactive Banner"),
                BannerType.MAIN,
                BannerPeriod.between(now.minusDays(1), now.plusDays(7)),
                BannerImage.of("inactive.jpg"),
                "http://inactive.com",
                2,
                "Inactive"
        );
        Banner deactivated = inactiveBanner.deactivate();
        repository.save(deactivated).block();

        // Create future banner
        Banner futureBanner = Banner.create(
                BannerTitle.of("Future Banner"),
                BannerType.MAIN,
                BannerPeriod.between(now.plusDays(10), now.plusDays(17)),
                BannerImage.of("future.jpg"),
                "http://future.com",
                3,
                "Future"
        );
        repository.save(futureBanner).block();

        // When & Then
        StepVerifier.create(repository.findActiveBanners())
                .assertNext(banner -> {
                    assertEquals("Active Banner", banner.getTitle().getValue());
                    assertTrue(banner.getIsActive());
                })
                .verifyComplete();
    }

    @Test
    void countActiveBanners_ShouldReturnCorrectCount() {
        // Given - Create 2 active banners
        Banner banner1 = Banner.create(
                BannerTitle.of("Active 1"),
                BannerType.MAIN,
                BannerPeriod.between(now.minusDays(1), now.plusDays(7)),
                BannerImage.of("active1.jpg"),
                "http://active1.com",
                1,
                "Active 1"
        );
        repository.save(banner1).block();

        Banner banner2 = Banner.create(
                BannerTitle.of("Active 2"),
                BannerType.ROUTES,
                BannerPeriod.between(now.minusDays(1), now.plusDays(7)),
                BannerImage.of("active2.jpg"),
                "http://active2.com",
                1,
                "Active 2"
        );
        repository.save(banner2).block();

        // Create inactive banner
        Banner inactive = Banner.create(
                BannerTitle.of("Inactive"),
                BannerType.MAIN,
                BannerPeriod.between(now.minusDays(1), now.plusDays(7)),
                BannerImage.of("inactive.jpg"),
                "http://inactive.com",
                2,
                "Inactive"
        ).deactivate();
        repository.save(inactive).block();

        // When & Then
        StepVerifier.create(repository.countActiveBanners())
                .assertNext(count -> assertEquals(2L, count))
                .verifyComplete();
    }

    @Test
    void findByTypeAndActive_ShouldFilterByTypeCorrectly() {
        // Given - Create banners of different types
        Banner mainBanner = Banner.create(
                BannerTitle.of("Main Banner"),
                BannerType.MAIN,
                BannerPeriod.between(now.minusDays(1), now.plusDays(7)),
                BannerImage.of("main.jpg"),
                "http://main.com",
                1,
                "Main"
        );
        repository.save(mainBanner).block();

        Banner routesBanner = Banner.create(
                BannerTitle.of("Routes Banner"),
                BannerType.ROUTES,
                BannerPeriod.between(now.minusDays(1), now.plusDays(7)),
                BannerImage.of("routes.jpg"),
                "http://routes.com",
                1,
                "Routes"
        );
        repository.save(routesBanner).block();

        // When & Then - Find MAIN type
        StepVerifier.create(repository.findByTypeAndActive(BannerType.MAIN))
                .assertNext(banner -> {
                    assertEquals("Main Banner", banner.getTitle().getValue());
                    assertEquals(BannerType.MAIN, banner.getType());
                })
                .verifyComplete();

        // Find ROUTES type
        StepVerifier.create(repository.findByTypeAndActive(BannerType.ROUTES))
                .assertNext(banner -> {
                    assertEquals("Routes Banner", banner.getTitle().getValue());
                    assertEquals(BannerType.ROUTES, banner.getType());
                })
                .verifyComplete();
    }

    @Test
    void countByType_ShouldReturnCorrectCountForType() {
        // Given - Create 2 MAIN banners and 1 ROUTES banner
        Banner main1 = Banner.create(
                BannerTitle.of("Main 1"),
                BannerType.MAIN,
                BannerPeriod.between(now.minusDays(1), now.plusDays(7)),
                BannerImage.of("main1.jpg"),
                "http://main1.com",
                1,
                "Main 1"
        );
        repository.save(main1).block();

        Banner main2 = Banner.create(
                BannerTitle.of("Main 2"),
                BannerType.MAIN,
                BannerPeriod.between(now.minusDays(1), now.plusDays(7)),
                BannerImage.of("main2.jpg"),
                "http://main2.com",
                2,
                "Main 2"
        );
        repository.save(main2).block();

        Banner routes1 = Banner.create(
                BannerTitle.of("Routes 1"),
                BannerType.ROUTES,
                BannerPeriod.between(now.minusDays(1), now.plusDays(7)),
                BannerImage.of("routes1.jpg"),
                "http://routes1.com",
                1,
                "Routes 1"
        );
        repository.save(routes1).block();

        // When & Then
        StepVerifier.create(repository.countByType(BannerType.MAIN))
                .assertNext(count -> assertEquals(2L, count))
                .verifyComplete();

        StepVerifier.create(repository.countByType(BannerType.ROUTES))
                .assertNext(count -> assertEquals(1L, count))
                .verifyComplete();
    }

    @Test
    void findAll_ShouldReturnAllBanners() {
        // Given
        Banner banner1 = Banner.create(
                BannerTitle.of("Banner 1"),
                BannerType.MAIN,
                BannerPeriod.between(now, now.plusDays(7)),
                BannerImage.of("banner1.jpg"),
                "http://banner1.com",
                1,
                "Content 1"
        );
        repository.save(banner1).block();

        Banner banner2 = Banner.create(
                BannerTitle.of("Banner 2"),
                BannerType.ROUTES,
                BannerPeriod.between(now, now.plusDays(7)),
                BannerImage.of("banner2.jpg"),
                "http://banner2.com",
                2,
                "Content 2"
        );
        repository.save(banner2).block();

        // When & Then
        StepVerifier.create(repository.findAll())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void saveAndRetrieve_ShouldPreserveAllBannerProperties() {
        // Given
        LocalDateTime startDate = now.minusDays(1);
        LocalDateTime endDate = now.plusDays(7);

        Banner banner = Banner.create(
                BannerTitle.of("Complete Banner"),
                BannerType.MAIN,
                BannerPeriod.between(startDate, endDate),
                BannerImage.of("complete.jpg"),
                "http://complete.com",
                5,
                "Complete content"
        );

        // When
        Banner saved = repository.save(banner).block();
        assertNotNull(saved);

        // Then - Retrieve and verify all properties
        StepVerifier.create(repository.findById(saved.getId()))
                .assertNext(retrieved -> {
                    assertEquals("Complete Banner", retrieved.getTitle().getValue());
                    assertEquals(BannerType.MAIN, retrieved.getType());
                    assertEquals("complete.jpg", retrieved.getImageUrl().getValue());
                    assertEquals("http://complete.com", retrieved.getTargetUrl());
                    assertTrue(retrieved.getIsActive());
                    assertEquals(5, retrieved.getDisplayOrder());
                    assertEquals("Complete content", retrieved.getContent());
                    assertNotNull(retrieved.getPeriod());
                    assertEquals(startDate.withNano(0), retrieved.getPeriod().getStartTime().withNano(0));
                    assertEquals(endDate.withNano(0), retrieved.getPeriod().getEndTime().withNano(0));
                    assertNotNull(retrieved.getCreatedAt());
                    assertNotNull(retrieved.getUpdatedAt());
                    assertEquals(0L, retrieved.getVersion());
                })
                .verifyComplete();
    }
}
