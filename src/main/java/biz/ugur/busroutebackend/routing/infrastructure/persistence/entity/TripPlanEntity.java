package biz.ugur.busroutebackend.routing.infrastructure.persistence.entity;

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
@Table("trip_plans")
public class TripPlanEntity {

    @Id
    private String id;

    @Column("origin_latitude")
    private Double originLatitude;

    @Column("origin_longitude")
    private Double originLongitude;

    @Column("destination_latitude")
    private Double destinationLatitude;

    @Column("destination_longitude")
    private Double destinationLongitude;

    @Column("search_time")
    private LocalDateTime searchTime;

    @Column("options_count")
    private Integer optionsCount;

    @Column("max_transfers")
    private Integer maxTransfers;

    @Column("max_walking_distance_meters")
    private Integer maxWalkingDistanceMeters;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
