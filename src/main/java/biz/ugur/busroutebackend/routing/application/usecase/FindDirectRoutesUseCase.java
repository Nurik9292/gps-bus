package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.routing.domain.volumeojects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripOption;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripPlanId;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class FindDirectRoutesUseCase implements UseCase<FindDirectRoutesUseCase.Command, Mono<TripPlan>> {

    private final RouteCalculationService routeCalculationService;
    private final ETACalculationService etaCalculationService;
    private final BusStopRepository busStopRepository;
    private final EventBus eventBus;

    public FindDirectRoutesUseCase(RouteCalculationService routeCalculationService,
                                   ETACalculationService etaCalculationService,
                                   BusStopRepository busStopRepository,
                                   EventBus eventBus) {
        this.routeCalculationService = routeCalculationService;
        this.etaCalculationService = etaCalculationService;
        this.busStopRepository = busStopRepository;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<TripPlan> execute(Command command) {
        log.info("Finding direct routes from {} to {}",
                command.fromLocation.getDescription(), command.toLocation.getDescription());

        Location fromLocation = command.fromLocation;
        Location toLocation = command.toLocation;

        // Создаем новый план поездки
        return Mono.fromCallable(() -> new TripPlan(TripPlanId.generate(), fromLocation, toLocation))
                .flatMap(tripPlan -> {
                    // Шаг 1: Найти ближайшие остановки для обеих точек
                    return Mono.zip(
                            findNearbyStops(fromLocation, 0.8), // 800м радиус
                            findNearbyStops(toLocation, 0.8)
                    ).flatMap(tuple -> {
                        List<BusStop> fromStops = tuple.getT1();
                        List<BusStop> toStops = tuple.getT2();

                        log.debug("Found {} origin stops and {} destination stops",
                                fromStops.size(), toStops.size());

                        if (fromStops.isEmpty()) {
                            log.warn("No bus stops found near origin location");
                            return Mono.just(tripPlan);
                        }

                        if (toStops.isEmpty()) {
                            log.warn("No bus stops found near destination location");
                            return Mono.just(tripPlan);
                        }

                        // Шаг 2: Найти прямые маршруты между парами остановок
                        return routeCalculationService.findDirectRoutes(fromStops, toStops)
                                .flatMap(directRoute -> createDirectTripOption(directRoute, fromLocation, toLocation))
                                .collectList()
                                .map(tripOptions -> {
                                    // Шаг 3: Добавить все найденные варианты в план
                                    tripOptions.forEach(tripPlan::addTripOption);

                                    // Публикуем domain events
                                    tripPlan.getUncommittedEvents().forEach(eventBus::publish);
                                    tripPlan.markEventsAsCommitted();

                                    return tripPlan;
                                });
                    });
                })
                .doOnSuccess(plan -> log.info("Found {} direct route options", plan.getTripOptions().size()))
                .doOnError(error -> log.error("Error finding direct routes", error));
    }

    // Найти остановки в радиусе от точки
    private Mono<List<BusStop>> findNearbyStops(Location location, double radiusKm) {
        return busStopRepository.findStopsWithinRadius(
                location.getLatitude(),
                location.getLongitude(),
                radiusKm
        ).collectList();
    }

    // Создать TripOption для прямого маршрута
    private Mono<TripOption> createDirectTripOption(RouteCalculationService.DirectRouteResult directRoute,
                                                    Location originalFrom, Location originalTo) {

        return Mono.fromCallable(() -> {
                    List<RouteSegment> segments = new ArrayList<>();

                    // 1. Пешеходный сегмент: от начальной точки до остановки
                    Location busStopLocation = new Location(
                            directRoute.fromStop().getLatitude().doubleValue(),
                            directRoute.fromStop().getLongitude().doubleValue(),
                            directRoute.fromStop().getStopName()
                    );

                    int walkingToStopMinutes = etaCalculationService.calculateWalkingTimeMinutes(originalFrom, busStopLocation);
                    segments.add(RouteSegment.walkingSegment(originalFrom, busStopLocation, walkingToStopMinutes));

                    // 2. Сегмент поездки на автобусе
                    Location destinationStopLocation = new Location(
                            directRoute.toStop().getLatitude().doubleValue(),
                            directRoute.toStop().getLongitude().doubleValue(),
                            directRoute.toStop().getStopName()
                    );

                    segments.add(RouteSegment.busRideSegment(
                            busStopLocation,
                            destinationStopLocation,
                            directRoute.estimatedTravelMinutes(),
                            directRoute.route().getRouteNumber()
                    ));

                    // 3. Пешеходный сегмент: от остановки до конечной точки
                    int walkingFromStopMinutes = etaCalculationService.calculateWalkingTimeMinutes(destinationStopLocation, originalTo);
                    segments.add(RouteSegment.walkingSegment(destinationStopLocation, originalTo, walkingFromStopMinutes));

                    return new TripOption(TripType.DIRECT, segments);
                })
                .doOnNext(option -> log.debug("Created direct trip option: {} minutes total",
                        option.getTotalTravelMinutes()));
    }

    // Command class
        public record Command(Location fromLocation, Location toLocation) {
    }
}