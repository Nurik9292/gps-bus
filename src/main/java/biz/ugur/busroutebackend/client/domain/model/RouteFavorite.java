package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.RouteFavoriteId;
import biz.ugur.busroutebackend.shared.domain.entity.Entity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Builder
@Table("route_favorites")
@Getter
public class RouteFavorite extends Entity<RouteFavoriteId> {

    @Id
    @Column("id")
    private RouteFavoriteId id;

    @Column("client_id")
    private ClientId clientId;

    @Column("route_id")
    private BusRouteId routeId;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;


    public static RouteFavorite create(ClientId clientId, BusRouteId routeId) {
        return builder()
                .id(RouteFavoriteId.generate())
                .clientId(clientId)
                .routeId(routeId)
                .build();
    }

    public static RouteFavorite fromDatabase(
            RouteFavoriteId id,
            ClientId clientId,
            BusRouteId routeId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {
        RouteFavorite routeFavorite = builder()
                .id(id)
                .clientId(clientId)
                .routeId(routeId)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version)
                .build();

        return routeFavorite;
    }

    @Override
    public RouteFavoriteId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
}