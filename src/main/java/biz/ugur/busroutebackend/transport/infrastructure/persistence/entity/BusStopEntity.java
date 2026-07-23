package biz.ugur.busroutebackend.transport.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("bus_stops")
public class BusStopEntity {

    @Id
    @Column("id")
    private String id;

    @Column("stop_name")
    private String stopName;

    @Column("name_en")
    private String nameEn;

    @Column("name_tm")
    private String nameTm;

    @Column("city_id")
    private String cityId;

    @Column("stop_code")
    private String stopCode;

    @Column("latitude")
    private BigDecimal latitude;

    @Column("longitude")
    private BigDecimal longitude;

    @Column("is_active")
    private Boolean isActive;

    @Column("is_major_stop")
    private Boolean isMajorStop;

    @Column("serving_routes_count")
    private Integer servingRoutesCount;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
    private String updatedBy;

    @Column("version")
    private Long version;
}
