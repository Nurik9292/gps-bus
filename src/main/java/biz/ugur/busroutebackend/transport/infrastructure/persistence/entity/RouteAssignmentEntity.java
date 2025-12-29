package biz.ugur.busroutebackend.transport.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("route_assignments")
public class RouteAssignmentEntity {

    @Id
    private String id;

    @Column("vehicle_id")
    private String vehicleId;

    @Column("route_id")
    private String routeId;

    @Column("effective_date")
    private LocalDate effectiveDate;

    @Column("shift_type")
    private String shiftType;

    @Column("assigned_by")
    private String assignedBy;

    @Column("reason")
    private String reason;

    @Column("expires_at")
    private Instant expiresAt;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
