package biz.ugur.busroutebackend.banner.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.banner.domain.events.*;
import biz.ugur.busroutebackend.banner.domain.repository.BannerEventStore;
import biz.ugur.busroutebackend.banner.infrastructure.persistence.entity.BannerEventEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;


@Slf4j
@Repository
public class R2dbcBannerEventStore implements BannerEventStore {

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public R2dbcBannerEventStore(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Mono<BannerDomainEvent> save(BannerDomainEvent event) {
        return Mono.defer(() -> {
            try {
                String payload = serializeEventPayload(event);
                String metadata = "{}"; // Можно расширить метаданными

                String sql = """
                    INSERT INTO banner_events (event_id, banner_id, event_type, event_version, payload, metadata, occurred_at, recorded_at)
                    VALUES (:eventId, :bannerId, :eventType, :eventVersion, :payload::jsonb, :metadata::jsonb, :occurredAt, :recordedAt)
                    RETURNING *
                    """;

                return databaseClient.sql(sql)
                        .bind("eventId", UUID.fromString(event.getEventId()))
                        .bind("bannerId", UUID.fromString(event.getBannerId()))
                        .bind("eventType", event.getEventType())
                        .bind("eventVersion", 1)
                        .bind("payload", payload)
                        .bind("metadata", metadata)
                        .bind("occurredAt", event.getOccurredAt())
                        .bind("recordedAt", Instant.now())
                        .map(this::mapRowToEvent)
                        .one()
                        .doOnSuccess(savedEvent -> log.debug("Saved event: {} for banner: {}",
                                event.getEventType(), event.getBannerId()))
                        .doOnError(error -> log.error("Failed to save event: {} for banner: {}",
                                event.getEventType(), event.getBannerId(), error));
            } catch (Exception e) {
                return Mono.error(new RuntimeException("Failed to serialize event", e));
            }
        });
    }

    @Override
    public Flux<BannerDomainEvent> findByBannerId(String bannerId) {
        String sql = """
            SELECT * FROM banner_events
            WHERE banner_id = :bannerId
            ORDER BY occurred_at ASC
            """;

        return databaseClient.sql(sql)
                .bind("bannerId", UUID.fromString(bannerId))
                .map(this::mapRowToEvent)
                .all()
                .doOnComplete(() -> log.debug("Retrieved events for banner: {}", bannerId));
    }

    @Override
    public Flux<BannerDomainEvent> findByBannerIdAfter(String bannerId, Instant since) {
        String sql = """
            SELECT * FROM banner_events
            WHERE banner_id = :bannerId AND occurred_at > :since
            ORDER BY occurred_at ASC
            """;

        return databaseClient.sql(sql)
                .bind("bannerId", UUID.fromString(bannerId))
                .bind("since", since)
                .map(this::mapRowToEvent)
                .all();
    }

    @Override
    public Flux<BannerDomainEvent> findByEventType(String eventType) {
        String sql = """
            SELECT * FROM banner_events
            WHERE event_type = :eventType
            ORDER BY occurred_at DESC
            """;

        return databaseClient.sql(sql)
                .bind("eventType", eventType)
                .map(this::mapRowToEvent)
                .all();
    }

    @Override
    public Mono<Long> countByBannerId(String bannerId) {
        String sql = """
            SELECT COUNT(*) FROM banner_events
            WHERE banner_id = :bannerId
            """;

        return databaseClient.sql(sql)
                .bind("bannerId", UUID.fromString(bannerId))
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<BannerDomainEvent> findAll() {
        String sql = "SELECT * FROM banner_events ORDER BY occurred_at DESC LIMIT 1000";

        return databaseClient.sql(sql)
                .map(this::mapRowToEvent)
                .all();
    }


    private String serializeEventPayload(BannerDomainEvent event) throws JsonProcessingException {
        return switch (event) {
            case BannerCreatedEvent e -> objectMapper.writeValueAsString(Map.of(
                    "title", e.getTitle(),
                    "type", e.getType(),
                    "imageUrl", e.getImageUrl(),
                    "targetUrl", e.getTargetUrl(),
                    "startDate", e.getStartDate(),
                    "endDate", e.getEndDate(),
                    "displayOrder", e.getDisplayOrder(),
                    "content", e.getContent() != null ? e.getContent() : ""
            ));
            case BannerUpdatedEvent e -> objectMapper.writeValueAsString(Map.of(
                    "changes", e.getChanges()
            ));
            case BannerActivatedEvent e -> objectMapper.writeValueAsString(Map.of());
            case BannerDeactivatedEvent e -> objectMapper.writeValueAsString(Map.of());
            case BannerDeletedEvent e -> objectMapper.writeValueAsString(Map.of(
                    "reason", e.getReason() != null ? e.getReason() : ""
            ));
            default -> throw new IllegalArgumentException("Unknown event type: " + event.getClass());
        };
    }


    private BannerDomainEvent mapRowToEvent(Row row, RowMetadata metadata) {
        String eventId = row.get("event_id", UUID.class).toString();
        String bannerId = row.get("banner_id", UUID.class).toString();
        String eventType = row.get("event_type", String.class);
        String payload = row.get("payload", String.class);
        Instant occurredAt = row.get("occurred_at", Instant.class);

        try {
            JsonNode payloadNode = objectMapper.readTree(payload);

            return switch (eventType) {
                case "BannerCreatedEvent" -> new BannerCreatedEvent(
                        eventId,
                        bannerId,
                        occurredAt,
                        payloadNode.get("title").asText(),
                        payloadNode.get("type").asText(),
                        payloadNode.get("imageUrl").asText(),
                        payloadNode.get("targetUrl").asText(),
                        LocalDateTime.parse(payloadNode.get("startDate").asText()),
                        LocalDateTime.parse(payloadNode.get("endDate").asText()),
                        payloadNode.get("displayOrder").asInt(),
                        payloadNode.has("content") ? payloadNode.get("content").asText() : null
                );
                case "BannerUpdatedEvent" -> {
                    Map<String, Object> changes = objectMapper.convertValue(
                            payloadNode.get("changes"),
                            Map.class
                    );
                    yield new BannerUpdatedEvent(eventId, bannerId, occurredAt, changes);
                }
                case "BannerActivatedEvent" -> new BannerActivatedEvent(eventId, bannerId, occurredAt);
                case "BannerDeactivatedEvent" -> new BannerDeactivatedEvent(eventId, bannerId, occurredAt);
                case "BannerDeletedEvent" -> {
                    String reason = payloadNode.has("reason") ? payloadNode.get("reason").asText() : null;
                    yield new BannerDeletedEvent(eventId, bannerId, occurredAt, reason);
                }
                case null -> null;
                default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
            };
        } catch (Exception e) {
            log.error("Failed to deserialize event: {}", eventType, e);
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }
}
