package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.StopFavoriteId;
import biz.ugur.busroutebackend.shared.domain.entity.Entity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import io.r2dbc.spi.RowMetadata;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Builder
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

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
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
