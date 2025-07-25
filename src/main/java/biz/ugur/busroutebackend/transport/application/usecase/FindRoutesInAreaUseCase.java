package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.RouteInAreaDTO;
import biz.ugur.busroutebackend.transport.application.dto.RoutePointDTO;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class FindRoutesInAreaUseCase implements UseCase<FindRoutesInAreaUseCase.Request, Flux<RouteInAreaDTO>> {

    private final BusRouteRepository busRouteRepository;

    public FindRoutesInAreaUseCase(BusRouteRepository busRouteRepository) {
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    public Flux<RouteInAreaDTO> execute(Request request) {
        log.debug("Finding routes in area: lat={}, lon={}, radius={}m",
                request.latitude, request.longitude, request.radiusMeters);

        return busRouteRepository.findRoutesIntersectingArea(
                        request.latitude, request.longitude, request.radiusMeters)
                .map(this::mapToDTO)
                .doOnComplete(() -> log.debug("Routes in area search completed"));
    }

    private RouteInAreaDTO mapToDTO(BusRouteRepository.RouteInAreaResult result) {
        RoutePointDTO nearestPoint = new RoutePointDTO(
                result.nearestPointLat(),
                result.nearestPointLat()
        );

        return new RouteInAreaDTO(
                result.routeId(),
                result.routeNumber(),
                result.routeName(),
                result.routeColor(),
                result.direction(),
                nearestPoint,
                result.distanceToCenterMeters(),
                result.activeVehiclesCount()
        );
    }

    // Request DTO
    public static class Request {
        public final Double latitude;
        public final Double longitude;
        public final Integer radiusMeters;

        public Request(Double latitude, Double longitude, Integer radiusMeters) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.radiusMeters = radiusMeters;
        }
    }
}