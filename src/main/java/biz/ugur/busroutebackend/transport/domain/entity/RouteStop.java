package biz.ugur.busroutebackend.transport.domain.entity;

import biz.ugur.busroutebackend.shared.domain.entity.Entity;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopId;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("route_stops")
public class RouteStop extends Entity<RouteStopId> {

    @Id
    private RouteStopId routeStopId;

    @Column("stop_id")
    private String stopId;

    @Column("route_id")
    private String routeId;


    @Override
    public RouteStopId getId() {
        return routeStopId;
    }
}
