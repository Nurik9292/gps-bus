package biz.ugur.busroutebackend.transport.infrastructure.repository;

import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Primary
@Repository
public class EnhancedBusStopRepository extends R2dbcBusStopRepository {


    public EnhancedBusStopRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Mono<biz.ugur.busroutebackend.transport.domain.model.BusStop> save(biz.ugur.busroutebackend.transport.domain.model.BusStop busStop) {
        if (busStop.getId() == null) {
            return insert(busStop);
        } else {
            return update(busStop);
        }
    }

    private Mono<biz.ugur.busroutebackend.transport.domain.model.BusStop> insert(biz.ugur.busroutebackend.transport.domain.model.BusStop busStop) {
        String sql = """
            INSERT INTO bus_stops (id, stop_name, stop_code, latitude, longitude, 
                                  is_active, is_major_stop, has_shelter, created_at, updated_at, version)
            VALUES (:id, :stopName, :stopCode, :latitude, :longitude, 
                   :isActive, :isMajorStop, :hasShelter, :createdAt, :updatedAt, :version)
            """;

        Instant now = Instant.now();
        return getDatabaseClient().sql(sql)
                .bind("id", busStop.getId().getValue())
                .bind("stopName", busStop.getStopName())
                .bind("stopCode", busStop.getStopCode())
                .bind("latitude", busStop.getLatitude())
                .bind("longitude", busStop.getLongitude())
                .bind("isActive", busStop.getIsActive())
                .bind("isMajorStop", busStop.getIsMajorStop())
                .bind("hasShelter", busStop.getHasShelter())
                .bind("createdAt", now)
                .bind("updatedAt", now)
                .bind("version", 0L)
                .then()
                .thenReturn(busStop);
    }

    private Mono<biz.ugur.busroutebackend.transport.domain.model.BusStop> update(biz.ugur.busroutebackend.transport.domain.model.BusStop busStop) {
        String sql = """
            UPDATE bus_stops 
            SET stop_name = :stopName, stop_code = :stopCode, latitude = :latitude, 
                longitude = :longitude, is_active = :isActive, is_major_stop = :isMajorStop,
                has_shelter = :hasShelter, updated_at = :updatedAt, version = version + 1
            WHERE id = :id
            """;

        return getDatabaseClient().sql(sql)
                .bind("id", busStop.getId().getValue())
                .bind("stopName", busStop.getStopName())
                .bind("stopCode", busStop.getStopCode())
                .bind("latitude", busStop.getLatitude())
                .bind("longitude", busStop.getLongitude())
                .bind("isActive", busStop.getIsActive())
                .bind("isMajorStop", busStop.getIsMajorStop())
                .bind("hasShelter", busStop.getHasShelter())
                .bind("updatedAt", Instant.now())
                .then()
                .thenReturn(busStop);
    }

    private DatabaseClient getDatabaseClient() {
        return this.databaseClient;
    }
}