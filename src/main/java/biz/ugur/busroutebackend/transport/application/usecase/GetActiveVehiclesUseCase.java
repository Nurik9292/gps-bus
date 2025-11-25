package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseFluxUseCase;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.application.mapper.VehicleResponseMapper;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.infrastructure.cache.ActiveVehicleCacheRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
public class GetActiveVehiclesUseCase extends BaseFluxUseCase<GetActiveVehiclesUseCase.Query, VehiclePositionDTO> {

    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final ActiveVehicleCacheRepository cacheRepository;
    private final VehicleResponseMapper responseMapper;

    public GetActiveVehiclesUseCase(VehicleRepository vehicleRepository,
                                    BusRouteRepository busRouteRepository,
                                    ActiveVehicleCacheRepository cacheRepository,
                                    VehicleResponseMapper responseMapper,
                                    CorrelationContextService  correlationContextService,
                                    EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.cacheRepository = cacheRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    public Flux<VehiclePositionDTO> execute(Query query) {
        log.debug("Getting active vehicles with query: {}", query);

        if (query == null) {
            query = Query.all();
        }

        return switch (query.getType()) {
            case ALL -> getAllActiveVehicles();
            case BY_ROUTE -> getVehiclesByRoute(query.getRouteNumber());
            case BY_AREA -> getVehiclesInArea(query.getMinLat(), query.getMinLon(),
                    query.getMaxLat(), query.getMaxLon());
            case BY_RADIUS -> getVehiclesInRadius(query.getCenterLat(), query.getCenterLon(),
                    query.getRadiusMeters());
        };
    }

    @Override
    protected Flux<VehiclePositionDTO> process(Query request) {
        return processVehicles(request);
    }

    @Override
    protected String getBoundedContext() {
        return "transport";
    }


    private Flux<VehiclePositionDTO> processVehicles(Query query) {
        return correlationService.getCurrentCorrelationId().flatMapMany(correlationId -> {
            log.debug("Getting active vehicles with query: {} - CorrelationId: {}", query, correlationId);

            Query effectiveQuery = (query != null) ? query : Query.all();

            return switch (effectiveQuery.getType()) {
                case ALL -> getAllActiveVehicles();
                case BY_ROUTE -> getVehiclesByRoute(effectiveQuery.getRouteNumber());
                case BY_AREA -> getVehiclesInArea(effectiveQuery.getMinLat(), effectiveQuery.getMinLon(),
                        effectiveQuery.getMaxLat(), effectiveQuery.getMaxLon());
                case BY_RADIUS -> getVehiclesInRadius(effectiveQuery.getCenterLat(), effectiveQuery.getCenterLon(),
                        effectiveQuery.getRadiusMeters());
            };
        });
    }


    private Flux<VehiclePositionDTO> getAllActiveVehicles() {
        String cacheKey = "active_vehicles:all";

        return cacheRepository.getCached(cacheKey)
                .switchIfEmpty(
                        vehicleRepository.findActiveVehicles()
                                .filter(this::isVehicleOnLine)
                                .flatMap(this::enrichVehicleWithRouteInfo)
                                .collectList()
                                .flatMapMany(vehicles ->
                                        cacheRepository.cache(cacheKey, Flux.fromIterable(vehicles), Duration.ofMinutes(2))
                                                .thenMany(Flux.fromIterable(vehicles))
                                )
                )
                .doOnNext(vehicle -> log.trace("Active vehicle: {}", vehicle.getLicensePlate()));
    }

    private Flux<VehiclePositionDTO> getVehiclesByRoute(String routeNumber) {
        if (routeNumber == null || routeNumber.trim().isEmpty()) {
            return Flux.empty();
        }

        String cacheKey = "active_vehicles:route:" + routeNumber;

        return cacheRepository.getCached(cacheKey)
                .switchIfEmpty(
                        busRouteRepository.findByRouteNumber(routeNumber)
                                .flatMapMany(route ->
                                        vehicleRepository.findByAssignedRouteId(route.getId())
                                                .filter(Vehicle::getIsActive)
                                                .filter(v -> v.hasPosition() && v.hasRecentPosition())
                                                .flatMap(vehicle -> enrichVehicleWithRouteInfo(vehicle, route))
                                )
                                .collectList()
                                .flatMapMany(vehicles ->
                                        cacheRepository.cache(cacheKey, Flux.fromIterable(vehicles), Duration.ofMinutes(1))
                                                .thenMany(Flux.fromIterable(vehicles))
                                )
                )
                .doOnNext(vehicle -> log.trace("Route {} vehicle: {}", routeNumber, vehicle.getLicensePlate()));
    }

    private Flux<VehiclePositionDTO> getVehiclesInArea(Double minLat, Double minLon,
                                                       Double maxLat, Double maxLon) {
        if (minLat == null || minLon == null || maxLat == null || maxLon == null) {
            return Flux.empty();
        }

        return vehicleRepository.findActiveVehicles()
                .filter(this::isVehicleOnLine)
                .filter(vehicle -> isVehicleInArea(vehicle, minLat, minLon, maxLat, maxLon))
                .flatMap(this::enrichVehicleWithRouteInfo)
                .doOnNext(vehicle -> log.trace("Vehicle in area: {}", vehicle.getLicensePlate()));
    }

    private Flux<VehiclePositionDTO> getVehiclesInRadius(Double centerLat, Double centerLon,
                                                         Integer radiusMeters) {
        if (centerLat == null || centerLon == null || radiusMeters == null) {
            return Flux.empty();
        }

        return vehicleRepository.findVehiclesWithinRadius(centerLat, centerLon, radiusMeters)
                .filter(Vehicle::getIsActive)
                .filter(this::isVehicleOnLine)
                .flatMap(this::enrichVehicleWithRouteInfo)
                .doOnNext(vehicle -> log.trace("Vehicle in radius: {}", vehicle.getLicensePlate()));
    }

    private Mono<VehiclePositionDTO> enrichVehicleWithRouteInfo(Vehicle vehicle) {
        if (vehicle.getAssignedRouteId() == null) {
            return responseMapper.toPositionDTO(vehicle);
        }

        return busRouteRepository.findById(vehicle.getAssignedRouteId())
                .flatMap(route -> responseMapper.toPositionDTO(vehicle, route))
                .switchIfEmpty(responseMapper.toPositionDTO(vehicle));
    }

    private Mono<VehiclePositionDTO> enrichVehicleWithRouteInfo(Vehicle vehicle,
                                                                biz.ugur.busroutebackend.transport.domain.model.BusRoute route) {
        return responseMapper.toPositionDTO(vehicle, route);
    }



    private boolean isVehicleInArea(Vehicle vehicle, Double minLat, Double minLon,
                                    Double maxLat, Double maxLon) {
        Double lat = vehicle.getCurrentLatitude();
        Double lon = vehicle.getCurrentLongitude();

        if (lat == null || lon == null) {
            return false;
        }

        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }


    private boolean isVehicleOnLine(Vehicle vehicle) {
        if (!vehicle.hasAssignedRoute()) {
            return false;
        }

        if (!vehicle.hasPosition()) {
            return false;
        }

        return vehicle.hasRecentPosition();
    }



    @Getter
    public static class Query {
        private QueryType type;
        private String routeNumber;
        private Double minLat, minLon, maxLat, maxLon;
        private Double centerLat, centerLon;
        private Integer radiusMeters;

        public enum QueryType {
            ALL, BY_ROUTE, BY_AREA, BY_RADIUS
        }

        public static Query all() {
            Query query = new Query();
            query.type = QueryType.ALL;
            return query;
        }

        public static Query byRoute(String routeNumber) {
            Query query = new Query();
            query.type = QueryType.BY_ROUTE;
            query.routeNumber = routeNumber;
            return query;
        }

        public static Query byArea(Double minLat, Double minLon, Double maxLat, Double maxLon) {
            Query query = new Query();
            query.type = QueryType.BY_AREA;
            query.minLat = minLat;
            query.minLon = minLon;
            query.maxLat = maxLat;
            query.maxLon = maxLon;
            return query;
        }

        public static Query byRadius(Double centerLat, Double centerLon, Integer radiusMeters) {
            Query query = new Query();
            query.type = QueryType.BY_RADIUS;
            query.centerLat = centerLat;
            query.centerLon = centerLon;
            query.radiusMeters = radiusMeters;
            return query;
        }

        @Override
        public String toString() {
            return switch (type) {
                case ALL -> "Query{type=ALL}";
                case BY_ROUTE -> String.format("Query{type=BY_ROUTE, route='%s'}", routeNumber);
                case BY_AREA -> String.format("Query{type=BY_AREA, area=(%.4f,%.4f)-(%.4f,%.4f)}",
                        minLat, minLon, maxLat, maxLon);
                case BY_RADIUS -> String.format("Query{type=BY_RADIUS, center=(%.4f,%.4f), radius=%dm}",
                        centerLat, centerLon, radiusMeters);
            };
        }
    }
}