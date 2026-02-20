package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.services.WalkingRouteService;
import biz.ugur.busroutebackend.shared.infrastructure.external.osrm.OsrmApiClient;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "external.api.osrm", name = "enabled", havingValue = "true")
@Slf4j
public class OsrmWalkingRouteService implements WalkingRouteService {

    private final OsrmApiClient osrmApiClient;

    public OsrmWalkingRouteService(OsrmApiClient osrmApiClient) {
        this.osrmApiClient = osrmApiClient;
    }

    @Override
    public Mono<WalkingRouteResult> getWalkingRoute(Coordinates from, Coordinates to) {
        return osrmApiClient.getRoute(
                        from.getLongitudeAsDouble(), from.getLatitudeAsDouble(),
                        to.getLongitudeAsDouble(), to.getLatitudeAsDouble()
                )
                .map(response -> {
                    if (response == null || response.routes().isEmpty()) {
                        return WalkingRouteResult.EMPTY;
                    }

                    OsrmApiClient.OsrmRoute route = response.routes().get(0);
                    if (route.geometry() == null || route.geometry().coordinates() == null) {
                        return WalkingRouteResult.EMPTY;
                    }

                    // OSRM возвращает [lon, lat] (GeoJSON стандарт),
                    // конвертируем в [lat, lon] согласно внутреннему соглашению проекта
                    List<List<Double>> coordinates = route.geometry().coordinates().stream()
                            .filter(c -> c.size() == 2)
                            .map(c -> List.of(c.get(1), c.get(0)))
                            .toList();

                    int distanceMeters = (int) Math.round(route.distance());
                    log.debug("OSRM walking route built: {} points, {}m", coordinates.size(), distanceMeters);
                    return new WalkingRouteResult(coordinates, distanceMeters);
                })
                .onErrorReturn(WalkingRouteResult.EMPTY)
                .defaultIfEmpty(WalkingRouteResult.EMPTY);
    }
}
