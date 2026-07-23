package biz.ugur.busroutebackend.transport.infrastructure.persistence.entity;

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
@Table("bus_routes")
public class BusRouteEntity {

    @Id
    @Column("id")
    private String id;

    @Column("route_number")
    private String routeNumber;

    @Column("route_name")
    private String routeName;

    @Column("name_tm")
    private String nameTm;

    @Column("name_en")
    private String nameEn;

    @Column("route_color")
    private String routeColor;

    @Column("is_active")
    private Boolean isActive;

    @Column("city_id")
    private String cityId;

    @Column("estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column("route_geometry_forward")
    private String routeGeometryForward;

    @Column("route_geometry_backward")
    private String routeGeometryBackward;

    @Column("total_distance_forward_meters")
    private Integer totalDistanceForwardMeters;

    @Column("total_distance_backward_meters")
    private Integer totalDistanceBackwardMeters;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
    private String updatedBy;

    @Column("version")
    private Long version;
}
