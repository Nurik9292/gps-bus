package biz.ugur.busroutebackend.geospatial.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("garages")
public class GarageEntity {

    @Id
    @Column("id")
    private String id;

    @Column("name")
    private String name;

    @Column("name_tm")
    private String nameTm;

    @Column("name_en")
    private String nameEn;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("radius_meters")
    private Integer radiusMeters;

    @Column("boundary")
    private String boundaryWkt;

    @Column("city_id")
    private String cityId;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
