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
 * FindRoutesWithTransfersUseCase - поиск маршрутов с пересадками (1-2 пересадки)
 *
 * Алгоритм:
 * 1. Найти ближайшие остановки к точкам отправления и назначения
 * 2. Поиск маршрутов с одной пересадкой:
 *    - Найти промежуточные остановки-пересадки
 *    - Проверить возможность пересадки (время, расстояние)
 *    - Рассчитать общее время с учетом ожидания
 * 3. Если недостаточно вариантов, искать с двумя пересадками
 * 4. Фильтрация и сортировка по качеству
 *
 * Business Rules:
 * - Максимум 2 пересадки для практичности
 * - Время пересадки: 3-10 минут в зависимости от остановки
 * - Приоритет крупным остановкам для пересадок
 * - Максимальное время ожидания: 20 минут
 */
@Service
@Slf4j
public class FindRoutesWithTransfersUseCase implements UseCase<FindRoutesWithTransfersUseCase.Command, Mono<TripPlan>> {

    private final RouteCalculationService routeCalculationService;
    private final ETACalculationService etaCalculationService;
    private final EventBus eventBus;

    public FindRoutesWithTransfersUseCase(RouteCalculationService routeCalculationService,
                                          ETACalculationService etaCalculationService,
                                          EventBus eventBus) {
        this.routeCalculationService = routeCalculationService;
        this.etaCalculationService = etaCalculationService;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<TripPlan> execute(Command command) {
        log.info("Finding routes with transfers from {} to {}",
                command.fromLocation.getDescription(), command.toLocation.getDescription());

        Location fromLocation = command.fromLocation;
        Location toLocation = command.toLocation;
        TripSearchCriteria criteria = command.searchCriteria != null ?
                command.searchCriteria : TripSearchCriteria.defaultCriteria();

        // Создаем новый план поездки или используем существующий
        TripPlan tripPlan = command.existingPlan != null ?
                command.existingPlan :
                new TripPlan(TripPlanId.generate(), fromLocation, toLocation, criteria);

        return Mono.just(tripPlan)
                .flatMap(plan -> {
                    // Параллельно находим ближайшие остановки
                    return Mono.zip(
                            findNearbyStopsWithQuality(fromLocation, 0.8, 6),
                            findNearbyStopsWithQuality(toLocation, 0.8, 6)
                    ).flatMap(tuple -> {
                        List<BusStop> fromStops = tuple.getT1();
                        List<BusStop> toStops = tuple.getT2();

                        log.debug("Searching transfer routes between {} origin and {} destination stops",
                                fromStops.size(), toStops.size());

                        if (fromStops.isEmpty() || toStops.isEmpty()) {
                            log.warn("Insufficient stops for transfer route planning");
                            return Mono.just(plan);
                        }

                        // Сначала ищем маршруты с одной пересадкой
                        return findRoutesWithOneTransfer(plan, fromStops, toStops, fromLocation, toLocation, criteria)
                                .flatMap(planWithOneTransfer -> {
                                    // Если недостаточно вариантов и разрешены 2 пересадки, ищем их
                                    int currentOptionsCount = planWithOneTransfer.getTripOptions().size();
                                    boolean needMoreOptions = currentOptionsCount < 3;
                                    boolean allowTwoTransfers = criteria.getMaxTransfers() >= 2;

                                    if (needMoreOptions && allowTwoTransfers) {
                                        log.debug("Found only {} options with one transfer, searching for two-transfer routes",
                                                currentOptionsCount);
                                        return findRoutesWithTwoTransfers(planWithOneTransfer, fromStops, toStops,
                                                fromLocation, toLocation, criteria);
                                    }
                                    return Mono.just(planWithOneTransfer);
                                });
                    });
                })
                .doOnSuccess(plan -> {
                    int totalOptions = plan.getTripOptions().size();
                    long transferOptions = plan.getTransferOptions().size();

                    if (transferOptions > 0) {
                        TripOption bestTransfer = plan.getOptionWithFewestTransfers();
                        log.info("Found {} transfer route options (total: {}). Best: {} transfers, {} minutes",
                                transferOptions, totalOptions,
                                bestTransfer != null ? bestTransfer.getTransfersCount() : "N/A",
                                bestTransfer != null ? bestTransfer.getTotalTravelMinutes() : "N/A");
                    } else {
                        log.info("No viable transfer routes found");
                    }
                })
                .doOnError(error -> log.error("Error finding transfer routes", error));
    }

    /**
     * Найти остановки с учетом их качества для пересадок
     */
    private Mono<List<BusStop>> findNearbyStopsWithQuality(Location location, double radiusKm, int maxStops) {
        return routeCalculationService.findNearbyStops(location, radiusKm)
                .filter(stop -> {
                    double distance = location.distanceTo(
                            stop.getLatitude().doubleValue(),
                            stop.getLongitude().doubleValue()
                    );
                    return distance <= 1000; // Максимум 1км
                })
                .sort((stop1, stop2) -> {
                    // Сортируем по качеству остановки: сначала крупные, потом по расстоянию
                    int qualityCompare = Boolean.compare(stop2.getIsMajorStop(), stop1.getIsMajorStop());
                    if (qualityCompare != 0) return qualityCompare;

                    double dist1 = location.distanceTo(
                            stop1.getLatitude().doubleValue(), stop1.getLongitude().doubleValue());
                    double dist2 = location.distanceTo(
                            stop2.getLatitude().doubleValue(), stop2.getLongitude().doubleValue());
                    return Double.compare(dist1, dist2);
                })
                .take(maxStops)
                .collectList();
    }

    /**
     * Поиск маршрутов с одной пересадкой
     */
    private Mono<TripPlan> findRoutesWithOneTransfer(TripPlan tripPlan, List<BusStop> fromStops,
                                                     List<BusStop> toStops, Location fromLocation,
                                                     Location toLocation, TripSearchCriteria criteria) {

        double maxTransferDistance = 0.5; // 500м максимальное расстояние пересадки

        return routeCalculationService.findRoutesWithOneTransfer(fromStops, toStops, maxTransferDistance)
                .filter(transferRoute -> isTransferRouteViable(transferRoute, criteria))
                .flatMap(transferRoute -> createOneTransferTripOption(transferRoute, fromLocation, toLocation))
                .filter(Objects::nonNull)
                .take(8) // Ограничиваем количество для производительности
                .collectList()
                .map(tripOptions -> {
                    tripOptions.forEach(tripPlan::addTripOption);

                    // Публикуем events
                    tripPlan.getUncommittedEvents().forEach(eventBus::publish);
                    tripPlan.markEventsAsCommitted();

                    return tripPlan;
                })
                .doOnNext(plan -> log.debug("Added {} one-transfer options",
                        tripOptions.size()));
    }

    /**
     * Поиск маршрутов с двумя пересадками (более сложный алгоритм)
     */
    private Mono<TripPlan> findRoutesWithTwoTransfers(TripPlan tripPlan, List<BusStop> fromStops,
                                                      List<BusStop> toStops, Location fromLocation,
                                                      Location toLocation, TripSearchCriteria criteria) {

        double maxTransferDistance = 0.3; // 300м для двух пересадок - более строгое требование

        return routeCalculationService.findRoutesWithTwoTransfers(fromStops, toStops, maxTransferDistance)
                .filter(twoTransferRoute -> isTwoTransferRouteViable(twoTransferRoute, criteria))
                .flatMap(twoTransferRoute -> createTwoTransferTripOption(twoTransferRoute, fromLocation, toLocation))
                .filter(Objects::nonNull)
                .take(4) // Меньше вариантов для двух пересадок
                .collectList()
                .map(tripOptions -> {
                    tripOptions.forEach(tripPlan::addTripOption);

                    tripPlan.getUncommittedEvents().forEach(eventBus::publish);
                    tripPlan.markEventsAsCommitted();

                    return tripPlan;
                })
                .doOnNext(plan -> log.debug("Added {} two-transfer options",
                        tripOptions.size()));
    }

    /**
     * Проверить жизнеспособность маршрута с одной пересадкой
     */
    private boolean isTransferRouteViable(RouteCalculationService.TransferRouteResult transferRoute,
                                          TripSearchCriteria criteria) {

        // Общее время поездки не должно превышать 90 минут
        int totalTime = transferRoute.firstRouteTravelMinutes() +
                transferRoute.transferWaitMinutes() +
                transferRoute.secondRouteTravelMinutes();

        if (totalTime > 90) {
            return false;
        }

        // Время ожидания на пересадке не должно быть слишком долгим
        if (transferRoute.transferWaitMinutes() > 20) {
            return false;
        }

        // Каждый сегмент поездки должен быть разумным (не менее 2 минут)
        if (transferRoute.firstRouteTravelMinutes() < 2 || transferRoute.secondRouteTravelMinutes() < 2) {
            return false;
        }

        return true;
    }

    /**
     * Проверить жизнеспособность маршрута с двумя пересадками
     */
    private boolean isTwoTransferRouteViable(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
                                             TripSearchCriteria criteria) {

        // Более строгие требования для двух пересадок
        int totalTime = twoTransferRoute.firstRouteTravelMinutes() +
                twoTransferRoute.firstTransferWaitMinutes() +
                twoTransferRoute.secondRouteTravelMinutes() +
                twoTransferRoute.secondTransferWaitMinutes() +
                twoTransferRoute.thirdRouteTravelMinutes();

        // Максимум 120 минут для двух пересадок
        if (totalTime > 120) {
            return false;
        }

        // Каждое ожидание не более 15 минут
        if (twoTransferRoute.firstTransferWaitMinutes() > 15 ||
                twoTransferRoute.secondTransferWaitMinutes() > 15) {
            return false;
        }

        // Каждый сегмент поездки должен быть значимым (не менее 3 минут)
        if (twoTransferRoute.firstRouteTravelMinutes() < 3 ||
                twoTransferRoute.secondRouteTravelMinutes() < 3 ||
                twoTransferRoute.thirdRouteTravelMinutes() < 3) {
            return false;
        }

        return true;
    }

    /**
     * Создать TripOption с одной пересадкой
     */
    private Mono<TripOption> createOneTransferTripOption(RouteCalculationService.TransferRouteResult transferRoute,
                                                         Location originalFrom, Location originalTo) {

        return Mono.fromCallable(() -> {
                    try {
                        List<RouteSegment> segments = new ArrayList<>();

                        // 1. Пешком до первой остановки
                        Location firstStopLocation = new Location(
                                transferRoute.fromStop().getLatitude().doubleValue(),
                                transferRoute.fromStop().getLongitude().doubleValue(),
                                transferRoute.fromStop().getStopName()
                        );

                        int walkingToFirstStop = etaCalculationService.calculateWalkingTimeMinutes(
                                originalFrom, firstStopLocation);

                        if (walkingToFirstStop > 15) {
                            log.debug("Walking to first stop too long: {} minutes", walkingToFirstStop);
                            return null;
                        }

                        segments.add(RouteSegment.walkingSegment(originalFrom, firstStopLocation, walkingToFirstStop));

                        // 2. Первая поездка на автобусе
                        Location transferStopLocation = new Location(
                                transferRoute.transferStop().getLatitude().doubleValue(),
                                transferRoute.transferStop().getLongitude().doubleValue(),
                                transferRoute.transferStop().getStopName()
                        );

                        segments.add(RouteSegment.busRideSegment(
                                firstStopLocation,
                                transferStopLocation,
                                transferRoute.firstRouteTravelMinutes(),
                                transferRoute.firstRoute().getRouteNumber()
                        ));

                        // 3. Пересадка (ожидание)
                        int transferTime = etaCalculationService.calculateTransferTimeMinutes(
                                transferRoute.transferStop().getStopName(),
                                transferRoute.transferStop().getIsMajorStop()
                        );

                        // Используем реальное время ожидания или рассчитанное
                        int actualTransferTime = Math.max(transferTime, transferRoute.transferWaitMinutes());
                        segments.add(RouteSegment.transferSegment(transferStopLocation, actualTransferTime));

                        // 4. Вторая поездка на автобусе
                        Location lastStopLocation = new Location(
                                transferRoute.toStop().getLatitude().doubleValue(),
                                transferRoute.toStop().getLongitude().doubleValue(),
                                transferRoute.toStop().getStopName()
                        );

                        segments.add(RouteSegment.busRideSegment(
                                transferStopLocation,
                                lastStopLocation,
                                transferRoute.secondRouteTravelMinutes(),
                                transferRoute.secondRoute().getRouteNumber()
                        ));

                        // 5. Пешком от последней остановки до пункта назначения
                        int walkingFromLastStop = etaCalculationService.calculateWalkingTimeMinutes(
                                lastStopLocation, originalTo);

                        if (walkingFromLastStop > 15) {
                            log.debug("Walking from last stop too long: {} minutes", walkingFromLastStop);
                            return null;
                        }

                        segments.add(RouteSegment.walkingSegment(lastStopLocation, originalTo, walkingFromLastStop));

                        return new TripOption(TripType.ONE_TRANSFER, segments);

                    } catch (Exception e) {
                        log.warn("Failed to create one-transfer trip option: {}", e.getMessage());
                        return null;
                    }
                })
                .doOnNext(option -> {
                    if (option != null) {
                        log.debug("Created one-transfer trip option: routes {}-{}, {} minutes total, {} transfers",
                                transferRoute.firstRoute().getRouteNumber(),
                                transferRoute.secondRoute().getRouteNumber(),
                                option.getTotalTravelMinutes(),
                                option.getTransfersCount());
                    }
                });
    }

    /**
     * Создать TripOption с двумя пересадками
     */
    private Mono<TripOption> createTwoTransferTripOption(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
                                                         Location originalFrom, Location originalTo) {

        return Mono.fromCallable(() -> {
                    try {
                        List<RouteSegment> segments = new ArrayList<>();

                        // 1. Пешком до первой остановки
                        Location firstStopLocation = new Location(
                                twoTransferRoute.fromStop().getLatitude().doubleValue(),
                                twoTransferRoute.fromStop().getLongitude().doubleValue(),
                                twoTransferRoute.fromStop().getStopName()
                        );

                        int walkingToFirstStop = etaCalculationService.calculateWalkingTimeMinutes(
                                originalFrom, firstStopLocation);

                        if (walkingToFirstStop > 12) { // Более строгие требования для двух пересадок
                            return null;
                        }

                        segments.add(RouteSegment.walkingSegment(originalFrom, firstStopLocation, walkingToFirstStop));

                        // 2. Первая поездка
                        Location firstTransferLocation = new Location(
                                twoTransferRoute.firstTransferStop().getLatitude().doubleValue(),
                                twoTransferRoute.firstTransferStop().getLongitude().doubleValue(),
                                twoTransferRoute.firstTransferStop().getStopName()
                        );

                        segments.add(RouteSegment.busRideSegment(
                                firstStopLocation,
                                firstTransferLocation,
                                twoTransferRoute.firstRouteTravelMinutes(),
                                twoTransferRoute.firstRoute().getRouteNumber()
                        ));

                        // 3. Первая пересадка
                        int firstTransferTime = etaCalculationService.calculateTransferTimeMinutes(
                                twoTransferRoute.firstTransferStop().getStopName(),
                                twoTransferRoute.firstTransferStop().getIsMajorStop()
                        );

                        int actualFirstTransferTime = Math.max(firstTransferTime, twoTransferRoute.firstTransferWaitMinutes());
                        segments.add(RouteSegment.transferSegment(firstTransferLocation, actualFirstTransferTime));

                        // 4. Вторая поездка
                        Location secondTransferLocation = new Location(
                                twoTransferRoute.secondTransferStop().getLatitude().doubleValue(),
                                twoTransferRoute.secondTransferStop().getLongitude().doubleValue(),
                                twoTransferRoute.secondTransferStop().getStopName()
                        );

                        segments.add(RouteSegment.busRideSegment(
                                firstTransferLocation,
                                secondTransferLocation,
                                twoTransferRoute.secondRouteTravelMinutes(),
                                twoTransferRoute.secondRoute().getRouteNumber()
                        ));

                        // 5. Вторая пересадка
                        int secondTransferTime = etaCalculationService.calculateTransferTimeMinutes(
                                twoTransferRoute.secondTransferStop().getStopName(),
                                twoTransferRoute.secondTransferStop().getIsMajorStop()
                        );

                        int actualSecondTransferTime = Math.max(secondTransferTime, twoTransferRoute.secondTransferWaitMinutes());
                        segments.add(RouteSegment.transferSegment(secondTransferLocation, actualSecondTransferTime));

                        // 6. Третья поездка
                        Location finalStopLocation = new Location(
                                twoTransferRoute.toStop().getLatitude().doubleValue(),
                                twoTransferRoute.toStop().getLongitude().doubleValue(),
                                twoTransferRoute.toStop().getStopName()
                        );

                        segments.add(RouteSegment.busRideSegment(
                                secondTransferLocation,
                                finalStopLocation,
                                twoTransferRoute.thirdRouteTravelMinutes(),
                                twoTransferRoute.thirdRoute().getRouteNumber()
                        ));

                        // 7. Пешком до пункта назначения
                        int walkingToDestination = etaCalculationService.calculateWalkingTimeMinutes(
                                finalStopLocation, originalTo);

                        if (walkingToDestination > 12) {
                            return null;
                        }

                        segments.add(RouteSegment.walkingSegment(finalStopLocation, originalTo, walkingToDestination));

                        return new TripOption(TripType.TWO_TRANSFERS, segments);

                    } catch (Exception e) {
                        log.warn("Failed to create two-transfer trip option: {}", e.getMessage());
                        return null;
                    }
                })
                .doOnNext(option -> {
                    if (option != null) {
                        log.debug("Created two-transfer trip option: routes {}-{}-{}, {} minutes total, {} transfers",
                                twoTransferRoute.firstRoute().getRouteNumber(),
                                twoTransferRoute.secondRoute().getRouteNumber(),
                                twoTransferRoute.thirdRoute().getRouteNumber(),
                                option.getTotalTravelMinutes(),
                                option.getTransfersCount());
                    }
                });
    }

    public record Command(Location fromLocation, Location toLocation,
                          TripSearchCriteria searchCriteria, TripPlan existingPlan) {

        // Convenience constructors
        public Command(Location fromLocation, Location toLocation) {
            this(fromLocation, toLocation, TripSearchCriteria.defaultCriteria(), null);
        }

        public Command(Location fromLocation, Location toLocation, TripSearchCriteria searchCriteria) {
            this(fromLocation, toLocation, searchCriteria, null);
        }

        public Command(Location fromLocation, Location toLocation, TripPlan existingPlan) {
            this(fromLocation, toLocation, TripSearchCriteria.defaultCriteria(), existingPlan);
        }
    }
}