package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.StopFavoriteId;
import biz.ugur.busroutebackend.shared.domain.entity.Entity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import io.r2dbc.spi.RowMetadata;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("stop_favorites")
@Getter
public class StopFavorite extends Entity<StopFavoriteId> {

    @Id
    @Column("id")
    private StopFavoriteId id;

    @Column("client_id")
    private ClientId clientId;

    @Column("stop_id")
    private BusStopId stopId;

    public StopFavorite() {}

    public static StopFavorite create(ClientId clientId, BusStopId stopId) {
        StopFavorite stopFavorite = new StopFavorite();
        stopFavorite.id = StopFavoriteId.generate();
        stopFavorite.clientId = clientId;
        stopFavorite.stopId = stopId;
        return stopFavorite;
    }


    public static StopFavorite fromDatabase(StopFavoriteId id,
                                               ClientId clientId,
                                               BusStopId stopId,
                                               Instant createdAt,
                                               Instant updatedAt,
                                               Long version) {
        StopFavorite entity = new StopFavorite();
        entity.clientId = clientId;
        entity.stopId = stopId;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.version = version;
        entity.id = id;

        return entity;
    }


    @Override
    public StopFavoriteId getId() {
        return id;
    }
}
