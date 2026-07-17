package biz.ugur.busroutebackend.transport.domain.valueobject;

import java.time.Instant;

public record NameChangeRecord(String entityKind, String entityId, String field,
                               String oldValue, String newValue,
                               String changedBy, Instant changedAt) {
}
