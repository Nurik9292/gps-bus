package biz.ugur.busroutebackend.shared.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {

    default String getEventId() {
        return UUID.randomUUID().toString();
    }

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    default Instant getOccurredAt() {
        return Instant.now();
    }

    default String getEventType() {
        return this.getClass().getSimpleName();
    }

    default String getVersion() {
        return "1.0";
    }
}