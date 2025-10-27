package biz.ugur.busroutebackend.shared.domain.entity;

import biz.ugur.busroutebackend.shared.base.BaseEntity;
import lombok.Getter;

import java.util.Objects;

@Getter
public abstract class Entity<ID> implements BaseEntity<ID> {

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