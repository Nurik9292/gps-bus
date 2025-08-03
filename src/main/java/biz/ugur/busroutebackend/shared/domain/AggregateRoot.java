package biz.ugur.busroutebackend.shared.domain;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Getter
public abstract class AggregateRoot<T extends AggregateRoot<T, ID>, ID> extends AbstractAggregateRoot<T> {

    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    @CreatedDate
    @Column("created_at")
    protected Instant createdAt;

    @LastModifiedDate
    @Column("updated_at")
    protected Instant updatedAt;

    @Version
    @Column("version")
    private Long version;

    public abstract ID getId();

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

    protected void registerEvent(DomainEvent event) {
        if (event != null) {
            uncommittedEvents.add(event);
        }
    }

    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    public boolean hasUncommittedEvents() {
        return !uncommittedEvents.isEmpty();
    }
}
