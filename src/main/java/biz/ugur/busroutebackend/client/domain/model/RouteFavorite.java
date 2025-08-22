package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.RouteFavoriteId;
import biz.ugur.busroutebackend.shared.domain.entity.Entity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

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

    public RouteFavorite() {}

    public static RouteFavorite create(ClientId clientId, BusRouteId routeId) {
        RouteFavorite routeFavorite = new RouteFavorite();
        routeFavorite.id = RouteFavoriteId.generate();
        routeFavorite.clientId = clientId;
        routeFavorite.routeId = routeId;

        return routeFavorite;
    }

    public static RouteFavorite fromDatabase(
            RouteFavoriteId id,
            ClientId clientId,
            BusRouteId routeId,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
        RouteFavorite routeFavorite = new RouteFavorite();
        routeFavorite.id = id;
        routeFavorite.clientId = clientId;
        routeFavorite.routeId = routeId;
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