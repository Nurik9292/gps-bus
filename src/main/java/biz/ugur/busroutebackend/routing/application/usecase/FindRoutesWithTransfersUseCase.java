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
public class FindRoutesWithTransfersUseCase implements UseCase<FindRoutesWithTransfersUseCase.Command, Mono<TripPlan>> {

    private final RouteCalculationService routeCalculationService;
    private final ETACalculationService etaCalculationService;
    private final BusStopRepository busStopRepository;
    private final EventBus eventBus;

    public FindRoutesWithTransfersUseCase(RouteCalculationService routeCalculationService,
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
        log.info("Finding routes with transfers from {} to {}",
                command.fromLocation.getDescription(), command.toLocation.getDescription());

        Location fromLocation = command.fromLocation;
        Location toLocation = command.toLocation;

        // Создаем новый план поездки
        return Mono.fromCallable(() -> new TripPlan(TripPlanId.generate(), fromLocation, toLocation))
                .flatMap(tripPlan -> {
                    // Найти ближайшие остановки
                    return Mono.zip(
                            findNearbyStops(fromLocation, 0.8),
                            findNearbyStops(toLocation, 0.8)
                    ).flatMap(tuple -> {
                        List<BusStop> fromStops = tuple.getT1();
                        List<BusStop> toStops = tuple.getT2();

                        log.debug("Searching transfer routes between {} origin and {} destination stops",
                                fromStops.size(), toStops.size());

                        if (fromStops.isEmpty() || toStops.isEmpty()) {
                            log.warn("Insufficient stops for transfer route planning");
                            return Mono.just(tripPlan);
                        }

                        // Поиск маршрутов с одной пересадкой
                        return findRoutesWithOneTransfer(tripPlan, fromStops, toStops, fromLocation, toLocation)
                                .flatMap(planWithOneTransfer -> {
                                    // Если нужно больше вариантов, ищем с двумя пересадками
                                    if (planWithOneTransfer.getTripOptions().size() < 2) {
                                        return findRoutesWithTwoTransfers(planWithOneTransfer, fromStops, toStops, fromLocation, toLocation);
                                    }
                                    return Mono.just(planWithOneTransfer);
                                });
                    });
                })
                .doOnSuccess(plan -> log.info("Found {} transfer route options", plan.getTripOptions().size()))
                .doOnError(error -> log.error("Error finding transfer routes", error));
    }

    // Поиск маршрутов с одной пересадкой
    private Mono<TripPlan> findRoutesWithOneTransfer(TripPlan tripPlan, List<BusStop> fromStops,
                                                     List<BusStop> toStops, Location fromLocation, Location toLocation) {

        return routeCalculationService.findRoutesWithOneTransfer(fromStops, toStops, 0.4) // 400м макс расстояние пересадки
                .flatMap(transferRoute -> createOneTransferTripOption(transferRoute, fromLocation, toLocation))
                .collectList()
                .map(tripOptions -> {
                    tripOptions.forEach(tripPlan::addTripOption);

                    // Публикуем events
                    tripPlan.getUncommittedEvents().forEach(eventBus::publish);
                    tripPlan.markEventsAsCommitted();

                    return tripPlan;
                })
                .doOnNext(plan -> log.debug("Added {} one-transfer options",
                        plan.getTripOptions().stream().filter(o -> o.getTripType() == TripType.ONE_TRANSFER).count()));
    }

    // Поиск маршрутов с двумя пересадками (если очень нужно)
    private Mono<TripPlan> findRoutesWithTwoTransfers(TripPlan tripPlan, List<BusStop> fromStops,
                                                      List<BusStop> toStops, Location fromLocation, Location toLocation) {

        return routeCalculationService.findRoutesWithTwoTransfers(fromStops, toStops, 0.3) // 300м макс расстояние
                .flatMap(twoTransferRoute -> createTwoTransferTripOption(twoTransferRoute, fromLocation, toLocation))
                .collectList()
                .map(tripOptions -> {
                    tripOptions.forEach(tripPlan::addTripOption);

                    tripPlan.getUncommittedEvents().forEach(eventBus::publish);
                    tripPlan.markEventsAsCommitted();

                    return tripPlan;
                })
                .doOnNext(plan -> log.debug("Added {} two-transfer options",
                        plan.getTripOptions().stream().filter(o -> o.getTripType() == TripType.TWO_TRANSFERS).count()));
    }

    // Создать TripOption с одной пересадкой
    private Mono<TripOption> createOneTransferTripOption(RouteCalculationService.TransferRouteResult transferRoute,
                                                         Location originalFrom, Location originalTo) {

        return Mono.fromCallable(() -> {
                    List<RouteSegment> segments = new ArrayList<>();

                    // 1. Пешком до первой остановки
                    Location firstStopLocation = new Location(
                            transferRoute.fromStop().getLatitude().doubleValue(),
                            transferRoute.fromStop().getLongitude().doubleValue(),
                            transferRoute.fromStop().getStopName()
                    );

                    int walkingToFirstStop = etaCalculationService.calculateWalkingTimeMinutes(originalFrom, firstStopLocation);
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
                    segments.add(RouteSegment.transferSegment(transferStopLocation, transferTime));

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
                    int walkingFromLastStop = etaCalculationService.calculateWalkingTimeMinutes(lastStopLocation, originalTo);
                    segments.add(RouteSegment.walkingSegment(lastStopLocation, originalTo, walkingFromLastStop));

                    return new TripOption(TripType.ONE_TRANSFER, segments);
                })
                .doOnNext(option -> log.debug("Created one-transfer trip option: {} minutes total, {} transfers",
                        option.getTotalTravelMinutes(), option.getTransfersCount()));
    }

    // Создать TripOption с двумя пересадками
    private Mono<TripOption> createTwoTransferTripOption(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
                                                         Location originalFrom, Location originalTo) {

        return Mono.fromCallable(() -> {
                    List<RouteSegment> segments = new ArrayList<>();

                    // 1. Пешком до первой остановки
                    Location firstStopLocation = new Location(
                            twoTransferRoute.fromStop().getLatitude().doubleValue(),
                            twoTransferRoute.fromStop().getLongitude().doubleValue(),
                            twoTransferRoute.fromStop().getStopName()
                    );

                    int walkingToFirstStop = etaCalculationService.calculateWalkingTimeMinutes(originalFrom, firstStopLocation);
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
                    segments.add(RouteSegment.transferSegment(firstTransferLocation, firstTransferTime));

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
                    segments.add(RouteSegment.transferSegment(secondTransferLocation, secondTransferTime));

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
                    int walkingToDestination = etaCalculationService.calculateWalkingTimeMinutes(finalStopLocation, originalTo);
                    segments.add(RouteSegment.walkingSegment(finalStopLocation, originalTo, walkingToDestination));

                    return new TripOption(TripType.TWO_TRANSFERS, segments);
                })
                .doOnNext(option -> log.debug("Created two-transfer trip option: {} minutes total, {} transfers",
                        option.getTotalTravelMinutes(), option.getTransfersCount()));
    }

    // Найти остановки в радиусе от точки
    private Mono<List<BusStop>> findNearbyStops(Location location, double radiusKm) {
        return busStopRepository.findStopsWithinRadius(
                location.getLatitude(),
                location.getLongitude(),
                radiusKm
        ).collectList();
    }

    // Command class
        public record Command(Location fromLocation, Location toLocation) {
    }
}