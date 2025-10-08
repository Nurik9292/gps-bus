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

import java.time.Instant;

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
            Instant createdAt,
            Instant updatedAt,
            Long version) {
        RouteFavorite routeFavorite = builder()
                .id(id)
                .clientId(clientId)
                .routeId(routeId)
                .build();
        routeFavorite.createdAt = createdAt;
        routeFavorite.updatedAt = updatedAt;
        routeFavorite.version = version;

        return routeFavorite;
    }

    @Override
    public RouteFavoriteId getId() {
        return id;
    }
}