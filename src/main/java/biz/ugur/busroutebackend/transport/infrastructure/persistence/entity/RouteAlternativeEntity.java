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
@Table("route_alternatives")
public class RouteAlternativeEntity {

    @Id
    @Column("id")
    private String id;

    @Column("primary_route_id")
    private String primaryRouteId;

    @Column("alternative_route_id")
    private String alternativeRouteId;

    @Column("priority")
    private Integer priority;

    @Column("description")
    private String description;

    @Column("description_tm")
    private String descriptionTm;

    @Column("description_en")
    private String descriptionEn;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
