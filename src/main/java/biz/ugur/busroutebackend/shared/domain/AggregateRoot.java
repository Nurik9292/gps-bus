package biz.ugur.busroutebackend.shared.domain;

import lombok.Getter;
import lombok.Setter;
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

@Setter
@Getter
public abstract class AggregateRoot<T extends AggregateRoot<T, ID>, ID> extends AbstractAggregateRoot<T> {

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



}
