package biz.ugur.busroutebackend.shared.base;

import java.time.LocalDateTime;

public interface BaseEntity<ID> {

    ID getId();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    Long getVersion();

    void setCreatedAt(LocalDateTime createdAt);

    void setUpdatedAt(LocalDateTime updatedAt);

    void setVersion(Long version);
}