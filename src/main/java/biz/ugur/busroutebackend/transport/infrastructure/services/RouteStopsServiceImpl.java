package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopDetail;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopsData;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Log4j2
@Service
public class RouteStopsServiceImpl implements RouteStopsService {

    private final RouteStopRepository routeStopRepository;

    public RouteStopsServiceImpl(RouteStopRepository routeStopRepository) {
        this.routeStopRepository = routeStopRepository;
    }


    @Override
    public Mono<Void> saveRouteStops(String routeId, List<String> forwardStops, List<String> backwardStops) {
        log.info("Saving route stops for route: {} (forward: {}, backward: {})",
                routeId, forwardStops.size(), backwardStops.size());

        return routeStopRepository.deleteExistingStops(routeId)
                .then(saveForwardStops(routeId, forwardStops))
                .then(saveBackwardStops(routeId, backwardStops))
                .then(routeStopRepository.resequenceStopsByDistance(routeId))
                .doOnSuccess(v -> log.info("Route stops saved successfully for route: {}", routeId))
                .doOnError(error -> log.error("Failed to save route stops for route: {}", routeId, error));
    }

    @Override
    public Mono<Void> updateRouteStops(String routeId, List<String> forwardStops, List<String> backwardStops) {
        return saveRouteStops(routeId, forwardStops, backwardStops);
    }

    @Override
    public Mono<Void> deleteRouteStops(String routeId) {
        return routeStopRepository.deleteExistingStops(routeId);
    }

    @Override
    public Flux<RouteStopInfo> getRouteStops(String routeId, int direction) {
        return routeStopRepository.getRouteStops(routeId, direction);
    }


    @Override
    public Mono<List<RouteStopDTO>> getForwardStopsDTO(String routeId) {
        return routeStopRepository.getForwardStopsDetail(routeId)
                .map(this::mapDomainListToDTO);
    }

    @Override
    public Mono<List<RouteStopDTO>> getBackwardStopsDTO(String routeId) {
        return routeStopRepository.getBackwardStopsDetail(routeId)
                .map(this::mapDomainListToDTO);
    }

    @Override
    public Mono<RouteStopsDataDTO> getAllRouteStopsDTO(String routeId) {
        return routeStopRepository.getAllRouteStopsData(routeId)
                .map(this::mapDomainDataToDTO)
                .doOnSuccess(data -> log.debug("Retrieved all DTO stops for route {}: {} forward, {} backward",
                        routeId, data.forwardStops().size(), data.backwardStops().size()));
    }


    private Mono<Void> saveForwardStops(String routeId, List<String> forwardStops) {
        if (forwardStops == null || forwardStops.isEmpty()) {
            log.debug("No forward stops to save for route: {}", routeId);
            return Mono.empty();
        }

        return saveStopsForDirection(routeId, forwardStops, 0)
                .doOnSuccess(v -> log.debug("Saved {} forward stops for route: {}", forwardStops.size(), routeId));
    }

    private Mono<Void> saveBackwardStops(String routeId, List<String> backwardStops) {
        if (backwardStops == null || backwardStops.isEmpty()) {
            log.debug("No backward stops to save for route: {}", routeId);
            return Mono.empty();
        }

        return saveStopsForDirection(routeId, backwardStops, 1)
                .doOnSuccess(v -> log.debug("Saved {} backward stops for route: {}", backwardStops.size(), routeId));
    }

    private Mono<Void> saveStopsForDirection(String routeId, List<String> stopIds, int direction) {
        return Flux.fromIterable(stopIds)
                .index()
                .flatMap(tuple -> {
                    Long index = tuple.getT1();
                    String stopId = tuple.getT2();
                    int sequence = index.intValue() + 1;

                    return routeStopRepository.insertRouteStop(routeId, stopId, sequence, direction);
                })
                .then()
                .doOnSuccess(v -> log.debug("Saved {} stops for route {} direction {}",
                        stopIds.size(), routeId, direction));
    }


    private RouteStopDTO mapDomainToDTO(RouteStopDetail domain) {
        return new RouteStopDTO(
                domain.getStopId(),
                domain.getStopName(),
                domain.getStopCode(),
                domain.getSequence(),
                domain.getDirection(),
                domain.getEstimatedTravelTimeMinutes(),
                domain.getDistanceFromStartMeters(),
                domain.getLatitude(),
                domain.getLongitude(),
                domain.isMajorStop()
        );
    }

    private List<RouteStopDTO> mapDomainListToDTO(List<RouteStopDetail> domainList) {
        return domainList.stream()
                .map(this::mapDomainToDTO)
                .toList();
    }

    private RouteStopsDataDTO mapDomainDataToDTO(RouteStopsData domainData) {
        return new RouteStopsDataDTO(
                mapDomainListToDTO(domainData.getForwardStops()),
                mapDomainListToDTO(domainData.getBackwardStops())
        );
    }

}
