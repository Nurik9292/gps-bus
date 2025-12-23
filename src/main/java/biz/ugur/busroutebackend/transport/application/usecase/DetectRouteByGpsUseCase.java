package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.geospatial.domain.services.RouteGeometryMatchingService;
import biz.ugur.busroutebackend.geospatial.domain.services.RouteGeometryMatchingService.RouteMatchResult;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.event.VehicleRouteAutoChangedEvent;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.infrastructure.redis.GpsPoint;
import biz.ugur.busroutebackend.transport.infrastructure.redis.VehicleGpsHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * Use case for detecting and auto-assigning routes based on GPS data.
 *
 * Business Logic:
 * 1. Get GPS history for vehicle
 * 2. Check if vehicle is deviating from current route (if assigned)
 * 3. If deviating, search all routes for best match
 * 4. Auto-reassign if confidence > threshold
 */
@Service
@Slf4j
public class DetectRouteByGpsUseCase extends BaseUseCase<DetectRouteByGpsUseCase.Request, DetectRouteByGpsUseCase.Result> {

    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final VehicleGpsHistoryService gpsHistoryService;
    private final RouteGeometryMatchingService matchingService;

    @Value("${business.gps-route-detection.min-gps-points:5}")
    private int minGpsPoints;

    @Value("${business.gps-route-detection.min-confidence-percentage:70.0}")
    private double minConfidencePercentage;

    @Value("${business.gps-route-detection.deviation-threshold-meters:500}")
    private double deviationThreshold;

    public DetectRouteByGpsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleRepository vehicleRepository,
            BusRouteRepository busRouteRepository,
            VehicleGpsHistoryService gpsHistoryService,
            RouteGeometryMatchingService matchingService) {
        super(correlationService, eventBus);
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.gpsHistoryService = gpsHistoryService;
        this.matchingService = matchingService;
    }

    @Override
    protected Mono<Result> process(Request request) {
        return gpsHistoryService.hasEnoughPoints(request.vehicleId(), minGpsPoints)
                .flatMap(hasEnough -> {
                    if (!hasEnough) {
                        return Mono.just(Result.insufficientData("Not enough GPS points"));
                    }
                    return detectAndAssign(request.vehicle());
                });
    }

    private Mono<Result> detectAndAssign(Vehicle vehicle) {
        return gpsHistoryService.getHistoryList(vehicle.getId().getValue(), 50)
                .flatMap(gpsPoints -> {
                    if (gpsPoints.isEmpty()) {
                        return Mono.just(Result.insufficientData("No GPS history"));
                    }

                    if (vehicle.hasAssignedRoute()) {
                        return checkDeviationAndReassign(vehicle, gpsPoints);
                    } else {
                        return findBestMatchingRoute(vehicle, gpsPoints);
                    }
                });
    }

    private Mono<Result> checkDeviationAndReassign(Vehicle vehicle, List<GpsPoint> gpsPoints) {
        return busRouteRepository.findById(vehicle.getAssignedRouteId())
                .flatMap(currentRoute -> {
                    boolean isDeviating = matchingService.isDeviatingFromRoute(gpsPoints, currentRoute);

                    if (!isDeviating) {
                        return Mono.just(Result.noChange("Vehicle following assigned route"));
                    }

                    log.info("Vehicle {} deviating from route {}, searching for better match",
                            vehicle.getLicensePlate(), currentRoute.getRouteNumber());

                    return findBestMatchingRoute(vehicle, gpsPoints);
                })
                .switchIfEmpty(findBestMatchingRoute(vehicle, gpsPoints));
    }

    private Mono<Result> findBestMatchingRoute(Vehicle vehicle, List<GpsPoint> gpsPoints) {
        return busRouteRepository.findActiveRoutes()
                .filter(BusRoute::hasGeometry)
                .flatMap(route -> Mono.just(matchingService.calculateMatchScore(gpsPoints, route)))
                .filter(RouteMatchResult::isMatch)
                .filter(result -> result.confidence() >= minConfidencePercentage)
                .collectList()
                .flatMap(matches -> {
                    if (matches.isEmpty()) {
                        log.debug("No matching routes found for vehicle {}",
                                vehicle.getLicensePlate());
                        return Mono.just(Result.noMatch("No routes matched GPS track"));
                    }

                    RouteMatchResult bestMatch = matches.stream()
                            .max(Comparator.comparingInt(RouteMatchResult::confidence))
                            .orElse(null);

                    if (bestMatch == null) {
                        return Mono.just(Result.noMatch("No valid match found"));
                    }

                    if (vehicle.hasAssignedRoute()
                            && vehicle.getAssignedRouteId().getValue().equals(bestMatch.routeId())) {
                        return Mono.just(Result.noChange("Best match is current route"));
                    }

                    return assignRouteToVehicle(vehicle, bestMatch);
                });
    }

    private Mono<Result> assignRouteToVehicle(Vehicle vehicle, RouteMatchResult match) {
        return busRouteRepository.findById(new BusRouteId(match.routeId()))
                .flatMap(route -> {
                    String previousRouteId = vehicle.getAssignedRouteId() != null
                            ? vehicle.getAssignedRouteId().getValue() : null;
                    String previousRouteNumber = vehicle.getRouteNumber();

                    Vehicle updatedVehicle = vehicle.toBuilder()
                            .assignedRouteId(route.getId())
                            .routeNumber(route.getRouteNumber())
                            .routeSource(RouteSource.GPS_DETECTED)
                            .routeConfidence(match.confidence())
                            .build();

                    return vehicleRepository.save(updatedVehicle)
                            .doOnSuccess(saved -> {
                                VehicleRouteAutoChangedEvent event = new VehicleRouteAutoChangedEvent(
                                        saved.getId().getValue(),
                                        saved.getLicensePlate(),
                                        previousRouteId,
                                        previousRouteNumber,
                                        route.getId().getValue(),
                                        route.getRouteNumber(),
                                        RouteSource.GPS_DETECTED,
                                        match.confidence(),
                                        "Auto-detected from GPS track"
                                );
                                eventBus.publish(event);

                                log.info("Vehicle {} auto-assigned to route {} (confidence: {}%) from GPS detection",
                                        saved.getLicensePlate(),
                                        route.getRouteNumber(),
                                        match.confidence());
                            })
                            .map(saved -> Result.routeChanged(
                                    route.getId().getValue(),
                                    route.getRouteNumber(),
                                    match.confidence(),
                                    previousRouteNumber
                            ));
                })
                .switchIfEmpty(Mono.just(Result.noMatch("Route not found: " + match.routeId())));
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    public record Request(Vehicle vehicle) {
        public String vehicleId() {
            return vehicle.getId().getValue();
        }
    }

    public record Result(
            ResultType type,
            String newRouteId,
            String newRouteNumber,
            int confidence,
            String previousRouteNumber,
            String reason
    ) {
        public enum ResultType {
            ROUTE_CHANGED,
            NO_CHANGE,
            NO_MATCH,
            INSUFFICIENT_DATA
        }

        public static Result routeChanged(String routeId, String routeNumber, int confidence, String previousRoute) {
            return new Result(ResultType.ROUTE_CHANGED, routeId, routeNumber, confidence, previousRoute, null);
        }

        public static Result noChange(String reason) {
            return new Result(ResultType.NO_CHANGE, null, null, 0, null, reason);
        }

        public static Result noMatch(String reason) {
            return new Result(ResultType.NO_MATCH, null, null, 0, null, reason);
        }

        public static Result insufficientData(String reason) {
            return new Result(ResultType.INSUFFICIENT_DATA, null, null, 0, null, reason);
        }

        public boolean isRouteChanged() {
            return type == ResultType.ROUTE_CHANGED;
        }
    }
}
