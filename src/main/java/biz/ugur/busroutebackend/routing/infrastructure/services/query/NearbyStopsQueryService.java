package biz.ugur.busroutebackend.routing.infrastructure.services.query;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
@RequiredArgsConstructor
public class NearbyStopsQueryService {

    private final BusStopRepository busStopRepository;

    public Flux<BusStop> findStopsWithinRadius(Coordinates location, double radiusKm) {
        log.info(">>> R2dbc executing nearby stops SQL");
        log.info("Finding stops within {}km of ({}, {})",
                radiusKm,
                location.getLatitudeAsDouble(),
                location.getLongitudeAsDouble());

        return busStopRepository.findStopsWithinRadius(
                location.getLatitudeAsDouble(),
                location.getLongitudeAsDouble(),
                radiusKm
        ).doOnComplete(() ->
                log.info("Completed nearby stops search for location ({}, {})",
                        location.getLatitudeAsDouble(),
                        location.getLongitudeAsDouble())
        );
    }
}
