package biz.ugur.busroutebackend.banner.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.banner.domain.events.*;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for R2dbcBannerEventStore using Testcontainers.
 * Tests actual database interactions with a real PostgreSQL instance.
 */
@DataR2dbcTest
@Testcontainers
class R2dbcBannerEventStoreIntegrationTest {

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

    private R2dbcBannerEventStore eventStore;
    private String bannerId;

    @BeforeEach
    void setUp() {
        eventStore = new R2dbcBannerEventStore(databaseClient);
        bannerId = BannerId.generate().getValue();

        // Create the banner_events table
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS banner_events (
                id BIGSERIAL PRIMARY KEY,
                event_id UUID NOT NULL UNIQUE,
                banner_id UUID NOT NULL,
                event_type VARCHAR(100) NOT NULL,
                event_version INTEGER NOT NULL DEFAULT 1,
                payload JSONB NOT NULL,
                metadata JSONB,
                occurred_at TIMESTAMP NOT NULL,
                recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT fk_banner_events_banner_id FOREIGN KEY (banner_id) REFERENCES banners(id) ON DELETE CASCADE
            )
            """;

        databaseClient.sql(createTableSql)
                .then()
                .block();
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        databaseClient.sql("DROP TABLE IF EXISTS banner_events CASCADE")
                .then()
                .block();
    }

    @Test
    void saveBannerCreatedEvent_ShouldPersistSuccessfully() {
        // Given
        BannerCreatedEvent event = new BannerCreatedEvent(
                bannerId,
                "Test Banner",
                "MAIN",
                "image.jpg",
                "http://test.com",
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7),
                1,
                "Test content"
        );

        // When & Then
        StepVerifier.create(eventStore.save(event))
                .assertNext(savedEvent -> {
                    assertNotNull(savedEvent);
                    assertEquals(event.getEventId(), savedEvent.getEventId());
                    assertEquals(event.getBannerId(), savedEvent.getBannerId());
                    assertEquals("BannerCreatedEvent", savedEvent.getEventType());
                    assertEquals(BannerCreatedEvent.CURRENT_VERSION, savedEvent.getEventVersion());
                })
                .verifyComplete();
    }

    @Test
    void saveBannerUpdatedEvent_ShouldPersistSuccessfully() {
        // Given
        Map<String, Object> changes = new HashMap<>();
        changes.put("title", "Updated Title");
        changes.put("displayOrder", 2);

        BannerUpdatedEvent event = new BannerUpdatedEvent(bannerId, changes);

        // When & Then
        StepVerifier.create(eventStore.save(event))
                .assertNext(savedEvent -> {
                    assertNotNull(savedEvent);
                    assertEquals(event.getEventId(), savedEvent.getEventId());
                    assertEquals(event.getBannerId(), savedEvent.getBannerId());
                    assertEquals("BannerUpdatedEvent", savedEvent.getEventType());
                    assertEquals(BannerUpdatedEvent.CURRENT_VERSION, savedEvent.getEventVersion());
                    assertTrue(savedEvent instanceof BannerUpdatedEvent);
                    assertEquals(changes, ((BannerUpdatedEvent) savedEvent).getChanges());
                })
                .verifyComplete();
    }

    @Test
    void saveBannerActivatedEvent_ShouldPersistSuccessfully() {
        // Given
        BannerActivatedEvent event = new BannerActivatedEvent(bannerId);

        // When & Then
        StepVerifier.create(eventStore.save(event))
                .assertNext(savedEvent -> {
                    assertNotNull(savedEvent);
                    assertEquals(event.getEventId(), savedEvent.getEventId());
                    assertEquals(event.getBannerId(), savedEvent.getBannerId());
                    assertEquals("BannerActivatedEvent", savedEvent.getEventType());
                    assertEquals(BannerActivatedEvent.CURRENT_VERSION, savedEvent.getEventVersion());
                })
                .verifyComplete();
    }

    @Test
    void saveBannerDeactivatedEvent_ShouldPersistSuccessfully() {
        // Given
        BannerDeactivatedEvent event = new BannerDeactivatedEvent(bannerId);

        // When & Then
        StepVerifier.create(eventStore.save(event))
                .assertNext(savedEvent -> {
                    assertNotNull(savedEvent);
                    assertEquals(event.getEventId(), savedEvent.getEventId());
                    assertEquals(event.getBannerId(), savedEvent.getBannerId());
                    assertEquals("BannerDeactivatedEvent", savedEvent.getEventType());
                    assertEquals(BannerDeactivatedEvent.CURRENT_VERSION, savedEvent.getEventVersion());
                })
                .verifyComplete();
    }

    @Test
    void saveBannerDeletedEvent_ShouldPersistSuccessfully() {
        // Given
        BannerDeletedEvent event = new BannerDeletedEvent(bannerId);

        // When & Then
        StepVerifier.create(eventStore.save(event))
                .assertNext(savedEvent -> {
                    assertNotNull(savedEvent);
                    assertEquals(event.getEventId(), savedEvent.getEventId());
                    assertEquals(event.getBannerId(), savedEvent.getBannerId());
                    assertEquals("BannerDeletedEvent", savedEvent.getEventType());
                    assertEquals(BannerDeletedEvent.CURRENT_VERSION, savedEvent.getEventVersion());
                })
                .verifyComplete();
    }

    @Test
    void findByBannerId_ShouldReturnEventsInOrder() {
        // Given - Save multiple events
        BannerCreatedEvent createdEvent = new BannerCreatedEvent(
                bannerId, "Test", "MAIN", "img.jpg", "http://test.com",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1, "content"
        );
        BannerActivatedEvent activatedEvent = new BannerActivatedEvent(bannerId);
        BannerDeactivatedEvent deactivatedEvent = new BannerDeactivatedEvent(bannerId);

        eventStore.save(createdEvent).block();
        eventStore.save(activatedEvent).block();
        eventStore.save(deactivatedEvent).block();

        // When & Then
        StepVerifier.create(eventStore.findByBannerId(bannerId))
                .assertNext(event -> assertEquals("BannerCreatedEvent", event.getEventType()))
                .assertNext(event -> assertEquals("BannerActivatedEvent", event.getEventType()))
                .assertNext(event -> assertEquals("BannerDeactivatedEvent", event.getEventType()))
                .verifyComplete();
    }

    @Test
    void findByBannerId_ShouldReturnEmptyForNonExistentBanner() {
        // Given
        String nonExistentBannerId = BannerId.generate().getValue();

        // When & Then
        StepVerifier.create(eventStore.findByBannerId(nonExistentBannerId))
                .verifyComplete();
    }

    @Test
    void findByBannerIdAfter_ShouldReturnOnlyEventsAfterTimestamp() {
        // Given
        Instant now = Instant.now();

        BannerCreatedEvent createdEvent = new BannerCreatedEvent(
                bannerId, "Test", "MAIN", "img.jpg", "http://test.com",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1, "content"
        );

        eventStore.save(createdEvent).block();

        // Sleep briefly to ensure time difference
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Instant afterFirst = Instant.now();

        BannerActivatedEvent activatedEvent = new BannerActivatedEvent(bannerId);
        eventStore.save(activatedEvent).block();

        // When & Then - Should only return the activated event
        StepVerifier.create(eventStore.findByBannerIdAfter(bannerId, afterFirst))
                .assertNext(event -> assertEquals("BannerActivatedEvent", event.getEventType()))
                .verifyComplete();
    }

    @Test
    void findAll_ShouldReturnAllEvents() {
        // Given
        String bannerId1 = BannerId.generate().getValue();
        String bannerId2 = BannerId.generate().getValue();

        BannerCreatedEvent event1 = new BannerCreatedEvent(
                bannerId1, "Banner 1", "MAIN", "img1.jpg", "http://test1.com",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1, "content 1"
        );
        BannerCreatedEvent event2 = new BannerCreatedEvent(
                bannerId2, "Banner 2", "ROUTES", "img2.jpg", "http://test2.com",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1, "content 2"
        );

        eventStore.save(event1).block();
        eventStore.save(event2).block();

        // When & Then
        StepVerifier.create(eventStore.findAll())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void saveAndRetrieve_ShouldPreserveEventVersions() {
        // Given - Save events with different versions
        BannerCreatedEvent createdEvent = new BannerCreatedEvent(
                bannerId, "Test", "MAIN", "img.jpg", "http://test.com",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1, "content"
        );

        // When
        eventStore.save(createdEvent).block();

        // Then - Verify version is preserved
        StepVerifier.create(eventStore.findByBannerId(bannerId))
                .assertNext(event -> {
                    assertEquals(BannerCreatedEvent.CURRENT_VERSION, event.getEventVersion());
                })
                .verifyComplete();
    }

    @Test
    void saveMultipleEventsForSameBanner_ShouldMaintainOrder() {
        // Given
        BannerCreatedEvent created = new BannerCreatedEvent(
                bannerId, "Test", "MAIN", "img.jpg", "http://test.com",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1, "content"
        );

        Map<String, Object> changes = new HashMap<>();
        changes.put("title", "Updated");
        BannerUpdatedEvent updated = new BannerUpdatedEvent(bannerId, changes);

        BannerActivatedEvent activated = new BannerActivatedEvent(bannerId);
        BannerDeactivatedEvent deactivated = new BannerDeactivatedEvent(bannerId);
        BannerDeletedEvent deleted = new BannerDeletedEvent(bannerId);

        // When - Save in sequence
        eventStore.save(created).block();
        eventStore.save(updated).block();
        eventStore.save(activated).block();
        eventStore.save(deactivated).block();
        eventStore.save(deleted).block();

        // Then - Verify order
        StepVerifier.create(eventStore.findByBannerId(bannerId))
                .assertNext(e -> assertEquals("BannerCreatedEvent", e.getEventType()))
                .assertNext(e -> assertEquals("BannerUpdatedEvent", e.getEventType()))
                .assertNext(e -> assertEquals("BannerActivatedEvent", e.getEventType()))
                .assertNext(e -> assertEquals("BannerDeactivatedEvent", e.getEventType()))
                .assertNext(e -> assertEquals("BannerDeletedEvent", e.getEventType()))
                .verifyComplete();
    }
}
