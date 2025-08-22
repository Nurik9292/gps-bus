package biz.ugur.busroutebackend.shared.base;

import java.time.Instant;

public interface BaseEntity<ID> {

    ID getId();

    Instant getCreatedAt();

    Instant getUpdatedAt();

    Long getVersion();

    void setCreatedAt(Instant createdAt);

    void setUpdatedAt(Instant updatedAt);

    void setVersion(Long version);
}