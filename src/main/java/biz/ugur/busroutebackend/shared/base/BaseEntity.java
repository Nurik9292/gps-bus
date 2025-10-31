package biz.ugur.busroutebackend.shared.base;

import java.time.LocalDateTime;

public interface BaseEntity<ID> {

    ID getId();

    LocalDateTime getCreatedAt();
    void setCreatedAt(LocalDateTime createdAt);

    LocalDateTime getUpdatedAt();
    void setUpdatedAt(LocalDateTime updatedAt);

    Long getVersion();
    void setVersion(Long version);
}