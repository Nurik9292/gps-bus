package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.StopFavoriteId;
import biz.ugur.busroutebackend.shared.domain.entity.Entity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("stop_favorites")
@Getter
public class StopFavorite extends Entity<StopFavoriteId> {

    @Id
    @Column("id")
    private StopFavoriteId id;

    @Column("client_id")
    private String clientId;

    @Column("stop_id")
    private String stopId;

    public StopFavorite() {}

    public StopFavorite(ClientId clientId, BusStopId stopId) {
        this.id = StopFavoriteId.generate();
        this.clientId = clientId.getValue();
        this.stopId = stopId.getValue();
    }

    @Override
    public StopFavoriteId getId() {
        return id;
    }
}
