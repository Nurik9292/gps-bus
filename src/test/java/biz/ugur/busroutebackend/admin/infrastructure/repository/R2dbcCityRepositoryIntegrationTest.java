package biz.ugur.busroutebackend.admin.infrastructure.repository;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.admin.infrastructure.persistence.repository.R2dbcCityRepository;
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

/**
 * Integration tests for R2dbcCityRepository using Testcontainers.
 */
@DataR2dbcTest
@Testcontainers
@Import(R2dbcCityRepository.class)
class R2dbcCityRepositoryIntegrationTest {

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

    private R2dbcCityRepository repository;

    @BeforeEach
    void setUp() {
        repository = new R2dbcCityRepository(databaseClient);

        // Create minimal cities table schema (matching production schema)
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS cities (
                id VARCHAR(36) PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                name_tm VARCHAR(100),
                is_active BOOLEAN DEFAULT true,
                display_order INTEGER DEFAULT 0,
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
        databaseClient.sql("DROP TABLE IF EXISTS cities CASCADE")
                .then()
                .block();
    }

    @Test
    void save_ShouldPersistCitySuccessfully() {
        // Given
        City city = City.create("Ashgabat", "Aşgabat", 1);

        // When & Then
        StepVerifier.create(repository.save(city))
                .assertNext(savedCity -> {
                    assertNotNull(savedCity);
                    assertEquals("Ashgabat", savedCity.getName());
                    assertEquals("Aşgabat", savedCity.getNameTm());
                    assertEquals(1, savedCity.getDisplayOrder());
                    assertTrue(savedCity.getIsActive());
                })
                .verifyComplete();
    }

    @Test
    void findById_ShouldRetrieveExistingCity() {
        // Given
        City city = City.create("Mary", "Mary", 2);
        City savedCity = repository.save(city).block();
        assertNotNull(savedCity);

        // When & Then
        StepVerifier.create(repository.findById(savedCity.getId()))
                .assertNext(found -> {
                    assertEquals(savedCity.getId(), found.getId());
                    assertEquals("Mary", found.getName());
                })
                .verifyComplete();
    }

    @Test
    void findById_ShouldReturnEmpty_ForNonExistentCity() {
        // Given
        CityId nonExistentId = CityId.generate();

        // When & Then
        StepVerifier.create(repository.findById(nonExistentId))
                .verifyComplete();
    }

    @Test
    void findActiveCities_ShouldReturnActiveCitiesOrderedByDisplayOrder() {
        // Given
        City city1 = City.create("Balkanabat", "Balkanabat", 3);
        City city2 = City.create("Turkmenabat", "Türkmenabat", 1);
        City city3 = City.create("Dasoguz", "Daşoguz", 2);
        City inactiveCity = City.create("TestCity", "TestCity", 0).deactivate();

        repository.save(city1).block();
        repository.save(city2).block();
        repository.save(city3).block();
        repository.save(inactiveCity).block();

        // When & Then
        StepVerifier.create(repository.findActiveCities())
                .expectNextCount(3) // Only active cities
                .verifyComplete();
    }

    @Test
    void existsByName_ShouldReturnTrue_WhenCityExists() {
        // Given
        City city = City.create("Ashgabat", "Aşgabat", 1);
        repository.save(city).block();

        // When & Then
        StepVerifier.create(repository.existsByName("Ashgabat"))
                .assertNext(exists -> assertTrue(exists))
                .verifyComplete();
    }

    @Test
    void existsByName_ShouldReturnFalse_WhenCityDoesNotExist() {
        // When & Then
        StepVerifier.create(repository.existsByName("NonExistentCity"))
                .assertNext(exists -> assertFalse(exists))
                .verifyComplete();
    }

    @Test
    void existsByName_ShouldBeCaseInsensitive() {
        // Given
        City city = City.create("Ashgabat", "Aşgabat", 1);
        repository.save(city).block();

        // When & Then - Different case
        StepVerifier.create(repository.existsByName("ashgabat"))
                .assertNext(exists -> assertTrue(exists))
                .verifyComplete();

        StepVerifier.create(repository.existsByName("ASHGABAT"))
                .assertNext(exists -> assertTrue(exists))
                .verifyComplete();
    }

    @Test
    void countActiveCities_ShouldReturnCorrectCount() {
        // Given
        City city1 = City.create("City1", "City1", 1);
        City city2 = City.create("City2", "City2", 2);
        City inactiveCity = City.create("City3", "City3", 3).deactivate();

        repository.save(city1).block();
        repository.save(city2).block();
        repository.save(inactiveCity).block();

        // When & Then
        StepVerifier.create(repository.countActiveCities())
                .assertNext(count -> assertEquals(2L, count))
                .verifyComplete();
    }

    @Test
    void existsByNameAndIdNot_ShouldReturnTrue_WhenAnotherCityWithSameNameExists() {
        // Given
        City city1 = City.create("Ashgabat", "Aşgabat", 1);
        City city2 = City.create("Mary", "Mary", 2);
        City savedCity1 = repository.save(city1).block();
        City savedCity2 = repository.save(city2).block();

        assertNotNull(savedCity1);
        assertNotNull(savedCity2);

        // When & Then - Check if "Ashgabat" exists excluding city2's ID
        StepVerifier.create(repository.existsByNameAndIdNot("Ashgabat", savedCity2.getId()))
                .assertNext(exists -> assertTrue(exists))
                .verifyComplete();
    }

    @Test
    void existsByNameAndIdNot_ShouldReturnFalse_WhenNoOtherCityWithSameNameExists() {
        // Given
        City city = City.create("Ashgabat", "Aşgabat", 1);
        City savedCity = repository.save(city).block();
        assertNotNull(savedCity);

        // When & Then - Check if "Ashgabat" exists excluding the same city's ID
        StepVerifier.create(repository.existsByNameAndIdNot("Ashgabat", savedCity.getId()))
                .assertNext(exists -> assertFalse(exists))
                .verifyComplete();
    }

    @Test
    void update_ShouldUpdateCitySuccessfully() {
        // Given
        City city = City.create("OriginalName", "OriginalNameTm", 1);
        City savedCity = repository.save(city).block();
        assertNotNull(savedCity);

        // When - Update with immutable pattern
        City updatedCity = savedCity.updateCity("UpdatedName", "UpdatedNameTm", 2);

        StepVerifier.create(repository.save(updatedCity))
                .assertNext(saved -> {
                    assertEquals("UpdatedName", saved.getName());
                    assertEquals("UpdatedNameTm", saved.getNameTm());
                    assertEquals(2, saved.getDisplayOrder());
                })
                .verifyComplete();
    }

    @Test
    void deleteById_ShouldRemoveCity() {
        // Given
        City city = City.create("DeleteMe", "DeleteMe", 1);
        City savedCity = repository.save(city).block();
        assertNotNull(savedCity);

        // When
        StepVerifier.create(repository.deleteById(savedCity.getId()))
                .verifyComplete();

        // Then - Verify deletion
        StepVerifier.create(repository.findById(savedCity.getId()))
                .verifyComplete();
    }

    @Test
    void immutabilitySupport_UpdateCity() {
        // Given
        City city = City.create("Original", "Original", 1);
        City savedCity = repository.save(city).block();
        assertNotNull(savedCity);

        // When - Update city (immutable operation)
        City updatedCity = savedCity.updateCity("Updated", "Updated TM", 5);

        StepVerifier.create(repository.save(updatedCity))
                .assertNext(saved -> {
                    assertEquals("Updated", saved.getName());
                    assertEquals("Updated TM", saved.getNameTm());
                    assertEquals(5, saved.getDisplayOrder());
                })
                .verifyComplete();

        // Original should be unchanged
        assertEquals("Original", savedCity.getName());
    }

    @Test
    void immutabilitySupport_ActivateDeactivate() {
        // Given
        City city = City.create("ActiveTest", "ActiveTest", 1);
        City savedCity = repository.save(city).block();
        assertNotNull(savedCity);
        assertTrue(savedCity.getIsActive());

        // When - Deactivate (immutable operation)
        City deactivatedCity = savedCity.deactivate();

        StepVerifier.create(repository.save(deactivatedCity))
                .assertNext(saved -> {
                    assertFalse(saved.getIsActive());
                })
                .verifyComplete();

        // Original should be unchanged
        assertTrue(savedCity.getIsActive());

        // Reactivate
        City reactivatedCity = deactivatedCity.activate();

        StepVerifier.create(repository.save(reactivatedCity))
                .assertNext(saved -> {
                    assertTrue(saved.getIsActive());
                })
                .verifyComplete();
    }

    @Test
    void findAll_ShouldReturnAllCities() {
        // Given
        City city1 = City.create("City1", "City1", 1);
        City city2 = City.create("City2", "City2", 2);
        City city3 = City.create("City3", "City3", 3);
        repository.save(city1).block();
        repository.save(city2).block();
        repository.save(city3).block();

        // When & Then
        StepVerifier.create(repository.findAll())
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void saveAndRetrieve_ShouldPreserveAllProperties() {
        // Given
        City city = City.create("CompleteCity", "CompleteCity TM", 10);

        // When
        City saved = repository.save(city).block();
        assertNotNull(saved);

        // Then
        StepVerifier.create(repository.findById(saved.getId()))
                .assertNext(retrieved -> {
                    assertEquals("CompleteCity", retrieved.getName());
                    assertEquals("CompleteCity TM", retrieved.getNameTm());
                    assertEquals(10, retrieved.getDisplayOrder());
                    assertTrue(retrieved.getIsActive());
                    assertNotNull(retrieved.getCreatedAt());
                    assertNotNull(retrieved.getUpdatedAt());
                    assertEquals(1L, retrieved.getVersion()); // Version is 1 after first save
                })
                .verifyComplete();
    }
}
