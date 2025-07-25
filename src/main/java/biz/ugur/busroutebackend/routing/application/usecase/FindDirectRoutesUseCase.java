package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.routing.domain.volumeojects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripOption;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripPlanId;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripSearchCriteria;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * FindDirectRoutesUseCase - поиск прямых маршрутов без пересадок
 *
 * Алгоритм:
 * 1. Найти ближайшие остановки к точке отправления (радиус 800м)
 * 2. Найти ближайшие остановки к пункту назначения (радиус 800м)
 * 3. Для каждой пары остановок найти прямые автобусные соединения
 * 4. Рассчитать время: ходьба до остановки + поездка + ходьба от остановки
 * 5. Отсортировать по общему времени поездки
 * 6. Вернуть лучшие варианты
 */
@Service
@Slf4j
public class FindDirectRoutesUseCase implements UseCase<FindDirectRoutesUseCase.Command, Mono<TripPlan>> {

    private final RouteCalculationService routeCalculationService;
    private final ETACalculationService etaCalculationService;
    private final EventBus eventBus;

    public FindDirectRoutesUseCase(RouteCalculationService routeCalculationService,
                                   ETACalculationService etaCalculationService,
                                   EventBus eventBus) {
        this.routeCalculationService = routeCalculationService;
        this.etaCalculationService = etaCalculationService;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<TripPlan> execute(Command command) {
        log.info("Finding direct routes from {} to {}",
                command.fromLocation.getDescription(), command.toLocation.getDescription());

        Location fromLocation = command.fromLocation;
        Location toLocation = command.toLocation;
        TripSearchCriteria criteria = command.searchCriteria != null ?
                command.searchCriteria : TripSearchCriteria.defaultCriteria();

        // Создаем новый план поездки
        return Mono.fromCallable(() -> new TripPlan(TripPlanId.generate(), fromLocation, toLocation, criteria))
                .flatMap(tripPlan -> {

                    // Проверяем можно ли дойти пешком
                    if (tripPlan.isWalkable()) {
                        log.debug("Destination is within walking distance: {}m",
                                fromLocation.distanceTo(toLocation));
                        addWalkingOption(tripPlan, fromLocation, toLocation);
                    }

                    // Параллельно ищем ближайшие остановки для обеих точек
                    return Mono.zip(
                            findNearbyStopsWithLimit(fromLocation, 0.8, 8), // Максимум 8 остановок отправления
                            findNearbyStopsWithLimit(toLocation, 0.8, 8)   // Максимум 8 остановок назначения
                    ).flatMap(tuple -> {
                        List<BusStop> fromStops = tuple.getT1();
                        List<BusStop> toStops = tuple.getT2();

                        log.debug("Found {} origin stops and {} destination stops",
                                fromStops.size(), toStops.size());

                        if (fromStops.isEmpty()) {
                            log.warn("No bus stops found near origin location: {}", fromLocation);
                            return Mono.just(tripPlan);
                        }

                        if (toStops.isEmpty()) {
                            log.warn("No bus stops found near destination location: {}", toLocation);
                            return Mono.just(tripPlan);
                        }

                        // Ищем прямые маршруты между парами остановок
                        return routeCalculationService.findDirectRoutes(fromStops, toStops)
                                .filter(directRoute -> isRouteReasonable(directRoute, criteria))
                                .flatMap(directRoute -> createDirectTripOption(directRoute, fromLocation, toLocation))
                                .filter(option -> option != null) // Убираем null варианты
                                .take(10) // Ограничиваем количество для производительности
                                .collectList()
                                .map(tripOptions -> {
                                    // Добавляем все найденные варианты в план
                                    tripOptions.forEach(tripPlan::addTripOption);

                                    // Публикуем domain events
                                    tripPlan.getUncommittedEvents().forEach(eventBus::publish);
                                    tripPlan.markEventsAsCommitted();

                                    return tripPlan;
                                });
                    });
                })
                .doOnSuccess(plan -> {
                    int optionsCount = plan.getTripOptions().size();
                    if (optionsCount > 0) {
                        TripOption fastest = plan.getFastestOption();
                        log.info("Found {} direct route options. Fastest: {} minutes",
                                optionsCount, fastest != null ? fastest.getTotalTravelMinutes() : "N/A");
                    } else {
                        log.info("No direct routes found between the locations");
                    }
                })
                .doOnError(error -> log.error("Error finding direct routes", error));
    }

    /**
     * Найти ближайшие остановки с ограничением количества
     */
    private Mono<List<BusStop>> findNearbyStopsWithLimit(Location location, double radiusKm, int maxStops) {
        return routeCalculationService.findNearbyStops(location, radiusKm)
                .take(maxStops) // Ограничиваем количество остановок
                .filter(stop -> {
                    double distance = location.distanceTo(
                            stop.getLatitude().doubleValue(),
                            stop.getLongitude().doubleValue()
                    );
                    // Фильтруем слишком далекие остановки (больше 1км)
                    return distance <= 1000;
                })
                .sort((stop1, stop2) -> {
                    // Сортируем по расстоянию от искомой точки
                    double dist1 = location.distanceTo(
                            stop1.getLatitude().doubleValue(),
                            stop1.getLongitude().doubleValue()
                    );
                    double dist2 = location.distanceTo(
                            stop2.getLatitude().doubleValue(),
                            stop2.getLongitude().doubleValue()
                    );
                    return Double.compare(dist1, dist2);
                })
                .collectList();
    }

    /**
     * Добавить опцию пешком для коротких расстояний
     */
    private void addWalkingOption(TripPlan tripPlan, Location from, Location to) {
        try {
            int walkingMinutes = tripPlan.getWalkingTimeMinutes();

            List<RouteSegment> segments = List.of(
                    RouteSegment.walkingSegment(from, to, walkingMinutes)
            );

            TripOption walkingOption = new TripOption(TripType.DIRECT, segments);
            tripPlan.addTripOption(walkingOption);

            log.debug("Added walking option: {} minutes", walkingMinutes);
        } catch (Exception e) {
            log.warn("Failed to add walking option: {}", e.getMessage());
        }
    }

    /**
     * Проверить разумность маршрута
     */
    private boolean isRouteReasonable(RouteCalculationService.DirectRouteResult directRoute,
                                      TripSearchCriteria criteria) {

        // Слишком долгая поездка (больше 2 часов)
        if (directRoute.estimatedTravelMinutes() > 120) {
            return false;
        }

        // Слишком короткая поездка (меньше 2 минут) - лучше пешком
        if (directRoute.estimatedTravelMinutes() < 2) {
            return false;
        }

        return true;
    }

    /**
     * Создать TripOption для прямого маршрута
     */
    private Mono<TripOption> createDirectTripOption(RouteCalculationService.DirectRouteResult directRoute,
                                                    Location originalFrom, Location originalTo) {

        Location fromStopLocation = new Location(
                directRoute.fromStop().getLatitude().doubleValue(),
                directRoute.fromStop().getLongitude().doubleValue(),
                directRoute.fromStop().getStopName()
        );

        Location toStopLocation = new Location(
                directRoute.toStop().getLatitude().doubleValue(),
                directRoute.toStop().getLongitude().doubleValue(),
                directRoute.toStop().getStopName()
        );

        Mono<Integer> walkingToStopMono = Mono.fromCallable(() ->
                etaCalculationService.calculateWalkingTimeMinutes(originalFrom, fromStopLocation));

        Mono<Integer> walkingFromStopMono = Mono.fromCallable(() ->
                etaCalculationService.calculateWalkingTimeMinutes(toStopLocation, originalTo));

        Mono<Integer> busRideTimeMono = etaCalculationService.calculateTravelTimeMinutes(
                directRoute.route().getRouteNumber(),
                directRoute.fromStop().getStopName(),
                directRoute.toStop().getStopName()
        );

        return Mono.zip(walkingToStopMono, busRideTimeMono, walkingFromStopMono)
                .mapNotNull(tuple -> {
                    int walkingToStop = tuple.getT1();
                    int busRideTime = Objects.nonNull(tuple.getT2())  ? tuple.getT2() : directRoute.estimatedTravelMinutes();
                    int walkingFromStop = tuple.getT3();

                    if (walkingToStop > 15 || walkingFromStop > 15) {
                        log.debug("Walking too long for route {}: toStop={} min, fromStop={} min",
                                directRoute.route().getRouteNumber(), walkingToStop, walkingFromStop);
                        return null;
                    }

                    List<RouteSegment> segments = List.of(
                            RouteSegment.walkingSegment(originalFrom, fromStopLocation, walkingToStop),
                            RouteSegment.busRideSegment(fromStopLocation, toStopLocation, busRideTime,
                                    directRoute.route().getRouteNumber()),
                            RouteSegment.walkingSegment(toStopLocation, originalTo, walkingFromStop)
                    );

                    return new TripOption(TripType.DIRECT, segments);
                })
                .filter(Objects::nonNull)
                .doOnNext(option -> log.debug("Created direct trip option: route {}, {} minutes",
                        directRoute.route().getRouteNumber(), option.getTotalTravelMinutes()));
    }

    /**
     * Улучшенная версия создания TripOption с асинхронным расчетом ETA
     */
    private Mono<TripOption> createDirectTripOptionAsync(RouteCalculationService.DirectRouteResult directRoute,
                                                         Location originalFrom, Location originalTo) {

        // Создаем локации остановок
        Location busStopLocation = new Location(
                directRoute.fromStop().getLatitude().doubleValue(),
                directRoute.fromStop().getLongitude().doubleValue(),
                directRoute.fromStop().getStopName()
        );

        Location destinationStopLocation = new Location(
                directRoute.toStop().getLatitude().doubleValue(),
                directRoute.toStop().getLongitude().doubleValue(),
                directRoute.toStop().getStopName()
        );

        // Параллельно рассчитываем время
        Mono<Integer> walkingToStopMono = Mono.fromCallable(() ->
                etaCalculationService.calculateWalkingTimeMinutes(originalFrom, busStopLocation));

        Mono<Integer> walkingFromStopMono = Mono.fromCallable(() ->
                etaCalculationService.calculateWalkingTimeMinutes(destinationStopLocation, originalTo));

        Mono<Integer> busRideMono = etaCalculationService.calculateTravelTimeMinutes(
                directRoute.route().getRouteNumber(),
                directRoute.fromStop().getStopName(),
                directRoute.toStop().getStopName()
        );

        return Mono.zip(walkingToStopMono, busRideMono, walkingFromStopMono)
                .mapNotNull(tuple -> {
                    int walkingToStop = tuple.getT1();
                    int busRideTime = tuple.getT2();
                    int walkingFromStop = tuple.getT3();

                    // Проверяем разумность времени ходьбы
                    if (walkingToStop > 15 || walkingFromStop > 15) {
                        log.debug("Walking time too long for route {}: {}+{} minutes",
                                directRoute.route().getRouteNumber(), walkingToStop, walkingFromStop);
                        return null;
                    }

                    List<RouteSegment> segments = List.of(
                            RouteSegment.walkingSegment(originalFrom, busStopLocation, walkingToStop),
                            RouteSegment.busRideSegment(busStopLocation, destinationStopLocation,
                                    busRideTime, directRoute.route().getRouteNumber()),
                            RouteSegment.walkingSegment(destinationStopLocation, originalTo, walkingFromStop)
                    );

                    return new TripOption(TripType.DIRECT, segments);
                })
                .doOnNext(option -> {
                    if (option != null) {
                        log.debug("Created async direct trip option: route {}, {} minutes total",
                                directRoute.route().getRouteNumber(), option.getTotalTravelMinutes());
                    }
                });
    }

    // Command class
    public record Command(Location fromLocation, Location toLocation, TripSearchCriteria searchCriteria) {

        // Convenience constructor с дефолтными критериями
        public Command(Location fromLocation, Location toLocation) {
            this(fromLocation, toLocation, TripSearchCriteria.defaultCriteria());
        }
    }

}
