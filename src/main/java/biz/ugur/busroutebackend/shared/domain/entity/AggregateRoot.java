package biz.ugur.busroutebackend.shared.domain.entity;

import biz.ugur.busroutebackend.shared.base.BaseEntity;
import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Setter
@Getter
public abstract class AggregateRoot<T extends AggregateRoot<T, ID>, ID> implements BaseEntity<ID> {

    private List<DomainEvent> domainEvents = new ArrayList<>();

    @CreatedDate
    @Column("created_at")
    protected Instant createdAt;

    @LastModifiedDate
    @Column("updated_at")
    protected Instant updatedAt;

    @Version
    @Column("version")
    protected Long version = 0L;

    public abstract ID getId();


    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearEvents() {
        domainEvents.clear();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AggregateRoot<?, ?> that = (AggregateRoot<?, ?>) obj;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}