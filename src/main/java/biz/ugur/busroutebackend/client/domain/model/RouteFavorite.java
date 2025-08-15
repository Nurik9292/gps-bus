package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.RouteFavoriteId;
import biz.ugur.busroutebackend.shared.domain.Entity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("route_favorites")
@Getter
public class RouteFavorite extends Entity<RouteFavoriteId> {

    @Id
    @Column("id")
    private RouteFavoriteId id;

    @Column("client_id")
    private String clientId;

    @Column("route_id")
    private String routeId;

    public RouteFavorite() {}

    public RouteFavorite(ClientId clientId, BusRouteId routeId) {
        this.id = RouteFavoriteId.generate();
        this.clientId = clientId.getValue();
        this.routeId = routeId.getValue();
    }

    @Override
    public RouteFavoriteId getId() {
        return id;
    }
}