package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.RouteFavoriteId;
import biz.ugur.busroutebackend.shared.domain.entity.Entity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class RouteFavorite extends Entity<RouteFavoriteId> {

    private RouteFavoriteId id;
    private ClientId clientId;
    private BusRouteId routeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
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

        return builder()
                .id(id)
                .clientId(clientId)
                .routeId(routeId)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version)
                .build();
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