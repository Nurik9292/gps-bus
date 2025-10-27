package biz.ugur.busroutebackend.admin.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Persistence entity for City.
 * This is a simple data holder for R2DBC mapping, separate from the domain model.
 */
@Builder
@Table("cities")
@Getter
@EqualsAndHashCode(callSuper = false)
public class CityEntity {

    @Id
    @Column("id")
    private String id;

    @Column("name")
    private String name;

    @Column("name_tm")
    private String nameTm;

    @Column("is_active")
    private Boolean isActive;

    @Column("display_order")
    private Integer displayOrder;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
