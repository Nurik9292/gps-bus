package biz.ugur.busroutebackend.shared.domain.entity;

import biz.ugur.busroutebackend.shared.base.BaseEntity;
import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public abstract class AggregateRoot<T extends AggregateRoot<T, ID>, ID> implements BaseEntity<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public abstract ID getId();

    public void registerEvent(DomainEvent event) {
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