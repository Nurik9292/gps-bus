package biz.ugur.busroutebackend.client.infrastructure.persistence.entity;

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
@Table("route_favorites")
@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class RouteFavoriteEntity {

    @Id
    @Column("id")
    private String id;

    @Column("client_id")
    private String clientId;

    @Column("route_id")
    private String routeId;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
