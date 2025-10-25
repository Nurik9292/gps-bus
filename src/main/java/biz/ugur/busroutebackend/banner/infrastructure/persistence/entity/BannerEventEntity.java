package biz.ugur.busroutebackend.banner.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerEventEntity {
    private UUID eventId;
    private UUID bannerId;
    private String eventType;
    private Integer eventVersion;
    private String payload;
    private String metadata;
    private Instant occurredAt;
    private Instant recordedAt;
}
