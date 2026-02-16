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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@Table("places")
@Getter
@ToString
@EqualsAndHashCode
public class PlaceEntity {

    @Id
    @Column("id")
    private String id;

    @Column("name")
    private String name;

    @Column("name_en")
    private String nameEn;

    @Column("name_tm")
    private String nameTm;

    @Column("description")
    private String description;

    @Column("address")
    private String address;

    @Column("category")
    private String category;

    @Column("city_id")
    private String cityId;

    @Column("latitude")
    private BigDecimal latitude;

    @Column("longitude")
    private BigDecimal longitude;

    @Column("is_active")
    private Boolean isActive;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
