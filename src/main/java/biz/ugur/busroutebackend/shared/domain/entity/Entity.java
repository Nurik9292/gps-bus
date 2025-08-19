package biz.ugur.busroutebackend.shared.domain.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.Objects;

@Setter
@Getter
public abstract class Entity<ID> {

    @CreatedDate
    @Column("created_at")
    protected Instant createdAt;

    @LastModifiedDate
    @Column("updated_at")
    protected Instant updatedAt;

    @Version
    protected Long version;

    public abstract ID getId();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Entity<?> that = (Entity<?>) obj;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}