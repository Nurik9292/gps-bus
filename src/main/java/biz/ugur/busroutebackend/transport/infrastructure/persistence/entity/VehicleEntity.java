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
@Table("vehicles")
public class VehicleEntity {

    @Id
    @Column("id")
    private String id;

    @Column("device_id")
    private String deviceId;

    @Column("license_plate")
    private String licensePlate;

    @Column("current_latitude")
    private Double currentLatitude;

    @Column("current_longitude")
    private Double currentLongitude;

    @Column("speed_kmh")
    private Double speedKmh;

    @Column("is_in_motion")
    private Boolean isInMotion;

    @Column("last_position_update")
    private LocalDateTime lastPositionUpdate;

    @Column("assigned_route_id")
    private String assignedRouteId;

    @Column("route_number")
    private String routeNumber;

    @Column("is_active")
    private Boolean isActive;

    @Column("course")
    private Double course;

    @Column("current_direction")
    private Integer currentDirection;

    @Column("last_stop_sequence")
    private Integer lastStopSequence;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
