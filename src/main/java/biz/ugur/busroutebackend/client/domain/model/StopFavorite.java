package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.StopFavoriteId;
import biz.ugur.busroutebackend.shared.domain.entity.Entity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class StopFavorite extends Entity<StopFavoriteId> {

    private StopFavoriteId id;
    private ClientId clientId;
    private BusStopId stopId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;


    public static StopFavorite create(ClientId clientId, BusStopId stopId) {
        return builder().id(StopFavoriteId.generate()).clientId(clientId).stopId(stopId).build();
    }


    public static StopFavorite fromDatabase(StopFavoriteId id,
                                               ClientId clientId,
                                               BusStopId stopId,
                                               LocalDateTime createdAt,
                                               LocalDateTime updatedAt,
                                               Long version) {
        return builder()
                .id(id)
                .clientId(clientId)
                .stopId(stopId)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version)
                .build();
    }


    @Override
    public StopFavoriteId getId() {
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
