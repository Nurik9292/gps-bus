package biz.ugur.busroutebackend.place.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Builder
@Table("street_aliases")
@Getter
@ToString
@EqualsAndHashCode
public class StreetAliasEntity {

    @Id
    @Column("id")
    private String id;

    @Column("street_id")
    private String streetId;

    @Column("alias")
    private String alias;

    @Column("language")
    private String language;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
