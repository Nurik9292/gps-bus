package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.*;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;


@Service
@Slf4j
public class FindRoutesWithTransfersUseCase extends BaseRouteUseCase implements UseCase<FindRoutesWithTransfersUseCase.Command, Mono<TripPlan>> {

    public FindRoutesWithTransfersUseCase(RouteCalculationService routeCalculationService,
                                          ETACalculationService etaCalculationService,
                                          EventBus eventBus) {
        super(routeCalculationService, etaCalculationService, eventBus);
    }

    @Override
    public Mono<TripPlan> execute(Command command) {
        log.info("Finding routes with transfers from {} to {}",
                command.fromLocation.getDescription(), command.toLocation.getDescription());

        TripSearchCriteria criteria = command.searchCriteria != null ?
                command.searchCriteria : TripSearchCriteria.defaultCriteria();

        TripPlan tripPlan = command.existingPlan != null ?
                command.existingPlan :
                new TripPlan(TripPlanId.generate(), command.fromLocation, command.toLocation, criteria);

        return findTransferRoutes(tripPlan, command.fromLocation, command.toLocation, criteria)
                .doOnSuccess(this::logTransferSearchResults)
                .doOnError(error -> log.error("Error finding transfer routes", error));
    }

    private Mono<TripPlan> findTransferRoutes(TripPlan tripPlan, Location fromLocation, Location toLocation, TripSearchCriteria criteria) {
        return Mono.zip(
                findNearbyStopsWithLimit(fromLocation, RoutingConstants.DEFAULT_NEARBY_RADIUS_KM, 6),
                findNearbyStopsWithLimit(toLocation, RoutingConstants.DEFAULT_NEARBY_RADIUS_KM, 6)
        ).flatMap(tuple -> {
            List<BusStop> fromStops = tuple.getT1();
            List<BusStop> toStops = tuple.getT2();

            if (fromStops.isEmpty() || toStops.isEmpty()) {
                log.warn("Insufficient stops for transfer route planning");
                return Mono.just(tripPlan);
            }

            // Сначала ищем маршруты с одной пересадкой
            return findOneTransferRoutes(tripPlan, fromStops, toStops, fromLocation, toLocation, criteria)
                    .flatMap(planWithOneTransfer -> {
                        // Если нужно, ищем маршруты с двумя пересадками
                        if (shouldSearchTwoTransfers(planWithOneTransfer, criteria)) {
                            return findTwoTransferRoutes(planWithOneTransfer, fromStops, toStops, fromLocation, toLocation, criteria);
                        }
                        return Mono.just(planWithOneTransfer);
                    });
        });
    }

    private boolean shouldSearchTwoTransfers(TripPlan plan, TripSearchCriteria criteria) {
        return plan.getTripOptions().size() < 3 && criteria.getMaxTransfers() >= 2;
    }

    private Mono<TripPlan> findOneTransferRoutes(TripPlan tripPlan, List<BusStop> fromStops, List<BusStop> toStops,
                                                 Location fromLocation, Location toLocation, TripSearchCriteria criteria) {
        return routeCalculationService.findRoutesWithOneTransfer(fromStops, toStops, 0.5)
                .filter(this::isTransferRouteViable)
                .flatMap(route -> createOneTransferTripOption(route, fromLocation, toLocation))
                .filter(Objects::nonNull)
                .take(8)
                .collectList()
                .map(tripOptions -> {
                    tripOptions.forEach(tripPlan::addTripOption);
                    publishTripPlanEvents(tripPlan);
                    return tripPlan;
                })
                .onErrorResume(e -> {
                    log.error("Error finding one-transfer routes", e);
                    return Mono.just(tripPlan);
                });
    }

    private Mono<TripPlan> findTwoTransferRoutes(TripPlan tripPlan, List<BusStop> fromStops, List<BusStop> toStops,
                                                 Location fromLocation, Location toLocation, TripSearchCriteria criteria) {
        log.info("🔍 STARTING two-transfer search with {} from stops, {} to stops",
                fromStops.size(), toStops.size());

        return routeCalculationService.findRoutesWithTwoTransfers(fromStops, toStops, 0.3)
                .doOnNext(route -> {
                    int totalTime = route.firstRouteTravelMinutes() + route.firstTransferWaitMinutes() +
                            route.secondRouteTravelMinutes() + route.secondTransferWaitMinutes() +
                            route.thirdRouteTravelMinutes();

                    log.info("🎯 SQL found two-transfer route: {}-{}-{} " +
                                    "(segments: {}+{}+{} min, waits: {}+{} min, total: {} min)",
                            route.firstRoute().getRouteNumber(),
                            route.secondRoute().getRouteNumber(),
                            route.thirdRoute().getRouteNumber(),
                            route.firstRouteTravelMinutes(),
                            route.secondRouteTravelMinutes(),
                            route.thirdRouteTravelMinutes(),
                            route.firstTransferWaitMinutes(),
                            route.secondTransferWaitMinutes(),
                            totalTime);
                })
                .filter(twoTransferRoute -> {
                    boolean viable = isTwoTransferRouteViable(twoTransferRoute);
                    if (viable) {
                        log.info("✅ Two-transfer route ACCEPTED: {}-{}-{}",
                                twoTransferRoute.firstRoute().getRouteNumber(),
                                twoTransferRoute.secondRoute().getRouteNumber(),
                                twoTransferRoute.thirdRoute().getRouteNumber());
                    } else {
                        log.warn("❌ Two-transfer route REJECTED: {}-{}-{}",
                                twoTransferRoute.firstRoute().getRouteNumber(),
                                twoTransferRoute.secondRoute().getRouteNumber(),
                                twoTransferRoute.thirdRoute().getRouteNumber());
                    }
                    return viable;
                })
                .flatMap(twoTransferRoute -> {
                    log.info("🏗️ Creating TripOption for: {}-{}-{}",
                            twoTransferRoute.firstRoute().getRouteNumber(),
                            twoTransferRoute.secondRoute().getRouteNumber(),
                            twoTransferRoute.thirdRoute().getRouteNumber());

                    return createTwoTransferTripOption(twoTransferRoute, fromLocation, toLocation);
                })
                .filter(Objects::nonNull)
                .take(4)
                .collectList()
                .flatMap(tripOptions -> {
                    log.info("📊 FINAL: {} two-transfer options created successfully", tripOptions.size());

                    tripOptions.forEach(option -> {
                        log.info("➕ Adding option to TripPlan: {} transfers, {} min, routes: {}",
                                option.getTransfersCount(),
                                option.getTotalTravelMinutes(),
                                option.getUsedRouteNumbers());
                        tripPlan.addTripOption(option);
                    });

                    tripPlan.getUncommittedEvents().forEach(eventBus::publish);
                    tripPlan.markEventsAsCommitted();

                    log.info("✅ TripPlan now has {} total options", tripPlan.getTripOptions().size());

                    return Mono.just(tripPlan)
                            .doOnNext(plan -> log.debug("Added {} two-transfer options", tripOptions.size()));
                })
                .onErrorResume(throwable -> {
                    log.error("❌ Error in two-transfer search: {}", throwable.getMessage(), throwable);
                    return Mono.just(tripPlan);
                });
    }
    private boolean isTransferRouteViable(RouteCalculationService.TransferRouteResult transferRoute) {
        int totalTime = transferRoute.firstRouteTravelMinutes() +
                transferRoute.transferWaitMinutes() +
                transferRoute.secondRouteTravelMinutes();

        // Увеличиваем лимиты для одной пересадки тоже
        if (totalTime > 100) {        // Увеличено с 90 до 100
            log.debug("One-transfer route rejected: total time {} minutes > 100", totalTime);
            return false;
        }

        if (transferRoute.transferWaitMinutes() > 30) {  // Увеличено с 20 до 30
            log.debug("One-transfer route rejected: wait time {} minutes > 30",
                    transferRoute.transferWaitMinutes());
            return false;
        }

        if (transferRoute.firstRouteTravelMinutes() < 1 ||   // Уменьшено с 2 до 1
                transferRoute.secondRouteTravelMinutes() < 1) {  // Уменьшено с 2 до 1
            log.debug("One-transfer route rejected: segments too short ({}, {})",
                    transferRoute.firstRouteTravelMinutes(),
                    transferRoute.secondRouteTravelMinutes());
            return false;
        }

        log.debug("✅ One-transfer route accepted: {}-{} (total: {} min)",
                transferRoute.firstRoute().getRouteNumber(),
                transferRoute.secondRoute().getRouteNumber(),
                totalTime);

        return true;
    }

    private boolean isTwoTransferRouteViable(RouteCalculationService.TwoTransferRouteResult twoTransferRoute) {
        // СТАРАЯ (слишком строгая) логика:
    /*
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
    */

        int totalTime = twoTransferRoute.firstRouteTravelMinutes() +
                twoTransferRoute.firstTransferWaitMinutes() +
                twoTransferRoute.secondRouteTravelMinutes() +
                twoTransferRoute.secondTransferWaitMinutes() +
                twoTransferRoute.thirdRouteTravelMinutes();

        log.debug("🔍 Validating route {}-{}-{}: total={}min, segments=[{}+{}+{}], waits=[{}+{}]",
                twoTransferRoute.firstRoute().getRouteNumber(),
                twoTransferRoute.secondRoute().getRouteNumber(),
                twoTransferRoute.thirdRoute().getRouteNumber(),
                totalTime,
                twoTransferRoute.firstRouteTravelMinutes(),
                twoTransferRoute.secondRouteTravelMinutes(),
                twoTransferRoute.thirdRouteTravelMinutes(),
                twoTransferRoute.firstTransferWaitMinutes(),
                twoTransferRoute.secondTransferWaitMinutes());

        // 1. Общее время поездки
        if (totalTime > 150) {
            log.debug("❌ Route rejected: total time {} > 150 minutes", totalTime);
            return false;
        }

        // 2. Время ожидания на пересадках
        if (twoTransferRoute.firstTransferWaitMinutes() > 25 ||
                twoTransferRoute.secondTransferWaitMinutes() > 25) {
            log.debug("❌ Route rejected: wait times too long ({}, {})",
                    twoTransferRoute.firstTransferWaitMinutes(),
                    twoTransferRoute.secondTransferWaitMinutes());
            return false;
        }

        // 3. Минимальное время сегментов
        if (twoTransferRoute.firstRouteTravelMinutes() < 1 ||
                twoTransferRoute.secondRouteTravelMinutes() < 1 ||
                twoTransferRoute.thirdRouteTravelMinutes() < 1) {
            log.debug("❌ Route rejected: segments too short ({}, {}, {})",
                    twoTransferRoute.firstRouteTravelMinutes(),
                    twoTransferRoute.secondRouteTravelMinutes(),
                    twoTransferRoute.thirdRouteTravelMinutes());
            return false;
        }

        // 4. Проверка уникальности маршрутов
        if (twoTransferRoute.firstRoute().getRouteNumber().equals(twoTransferRoute.secondRoute().getRouteNumber()) ||
                twoTransferRoute.secondRoute().getRouteNumber().equals(twoTransferRoute.thirdRoute().getRouteNumber()) ||
                twoTransferRoute.firstRoute().getRouteNumber().equals(twoTransferRoute.thirdRoute().getRouteNumber())) {
            log.debug("❌ Route rejected: duplicate routes {}-{}-{}",
                    twoTransferRoute.firstRoute().getRouteNumber(),
                    twoTransferRoute.secondRoute().getRouteNumber(),
                    twoTransferRoute.thirdRoute().getRouteNumber());
            return false;
        }

        log.debug("✅ Route accepted: {}-{}-{} (total: {} min)",
                twoTransferRoute.firstRoute().getRouteNumber(),
                twoTransferRoute.secondRoute().getRouteNumber(),
                twoTransferRoute.thirdRoute().getRouteNumber(),
                totalTime);

        return true;
    }


    private Mono<TripOption> createOneTransferTripOption(RouteCalculationService.TransferRouteResult transferRoute,
                                                         Location originalFrom, Location originalTo) {
        return createTransferTripOption(
                transferRoute.fromStop(), transferRoute.transferStop(), transferRoute.toStop(),
                transferRoute.firstRoute().getRouteNumber(), transferRoute.secondRoute().getRouteNumber(),
                transferRoute.firstRouteTravelMinutes(), transferRoute.secondRouteTravelMinutes(),
                transferRoute.transferWaitMinutes(), originalFrom, originalTo, TripType.ONE_TRANSFER
        );
    }

    private Mono<TripOption> createTwoTransferTripOption(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
                                                         Location originalFrom, Location originalTo) {
        return Mono.fromCallable(() -> {
            List<RouteSegment> segments = createTwoTransferSegments(twoTransferRoute, originalFrom, originalTo);
            return new TripOption(TripType.TWO_TRANSFERS, segments);
        }).onErrorResume(e -> {
            log.warn("Failed to create two-transfer trip option: {}", e.getMessage());
            return Mono.empty();
        });
    }

    // РЕФАКТОРИНГ: Общий метод для создания transfer trip options
    private Mono<TripOption> createTransferTripOption(BusStop fromStop, BusStop transferStop, BusStop toStop,
                                                      String firstRouteNumber, String secondRouteNumber,
                                                      int firstRouteTime, int secondRouteTime, int transferTime,
                                                      Location originalFrom, Location originalTo, TripType tripType) {
        return Mono.fromCallable(() -> {
            List<RouteSegment> segments = createTransferSegments(
                    fromStop, transferStop, toStop, firstRouteNumber, secondRouteNumber,
                    firstRouteTime, secondRouteTime, transferTime, originalFrom, originalTo
            );
            return new TripOption(tripType, segments);
        }).onErrorResume(e -> {
            log.warn("Failed to create transfer trip option: {}", e.getMessage());
            return Mono.empty();
        });
    }

    private List<RouteSegment> createTransferSegments(BusStop fromStop, BusStop transferStop, BusStop toStop,
                                                      String firstRouteNumber, String secondRouteNumber,
                                                      int firstRouteTime, int secondRouteTime, int transferTime,
                                                      Location originalFrom, Location originalTo) {
        Location firstStopLocation = createLocationFromStop(fromStop);
        Location transferStopLocation = createLocationFromStop(transferStop);
        Location lastStopLocation = createLocationFromStop(toStop);

        int walkingToFirst = etaCalculationService.calculateWalkingTimeMinutes(originalFrom, firstStopLocation);
        int walkingFromLast = etaCalculationService.calculateWalkingTimeMinutes(lastStopLocation, originalTo);

        if (walkingToFirst > RoutingConstants.MAX_WALKING_TIME_MINUTES ||
                walkingFromLast > RoutingConstants.MAX_WALKING_TIME_MINUTES) {
            throw new IllegalArgumentException("Walking time too long");
        }

        return List.of(
                RouteSegment.walkingSegment(originalFrom, firstStopLocation, walkingToFirst),
                RouteSegment.busRideSegment(firstStopLocation, transferStopLocation, firstRouteTime, firstRouteNumber),
                RouteSegment.transferSegment(transferStopLocation, transferTime),
                RouteSegment.busRideSegment(transferStopLocation, lastStopLocation, secondRouteTime, secondRouteNumber),
                RouteSegment.walkingSegment(lastStopLocation, originalTo, walkingFromLast)
        );
    }

    private List<RouteSegment> createTwoTransferSegments(RouteCalculationService.TwoTransferRouteResult route,
                                                         Location originalFrom, Location originalTo) {
        Location firstStopLocation = createLocationFromStop(route.fromStop());
        Location firstTransferLocation = createLocationFromStop(route.firstTransferStop());
        Location secondTransferLocation = createLocationFromStop(route.secondTransferStop());
        Location finalStopLocation = createLocationFromStop(route.toStop());

        int walkingToFirst = etaCalculationService.calculateWalkingTimeMinutes(originalFrom, firstStopLocation);
        int walkingFromFinal = etaCalculationService.calculateWalkingTimeMinutes(finalStopLocation, originalTo);

        if (walkingToFirst > 12 || walkingFromFinal > 12) {
            throw new IllegalArgumentException("Walking time too long for two transfers");
        }

        return List.of(
                RouteSegment.walkingSegment(originalFrom, firstStopLocation, walkingToFirst),
                RouteSegment.busRideSegment(firstStopLocation, firstTransferLocation,
                        route.firstRouteTravelMinutes(), route.firstRoute().getRouteNumber()),
                RouteSegment.transferSegment(firstTransferLocation, route.firstTransferWaitMinutes()),
                RouteSegment.busRideSegment(firstTransferLocation, secondTransferLocation,
                        route.secondRouteTravelMinutes(), route.secondRoute().getRouteNumber()),
                RouteSegment.transferSegment(secondTransferLocation, route.secondTransferWaitMinutes()),
                RouteSegment.busRideSegment(secondTransferLocation, finalStopLocation,
                        route.thirdRouteTravelMinutes(), route.thirdRoute().getRouteNumber()),
                RouteSegment.walkingSegment(finalStopLocation, originalTo, walkingFromFinal)
        );
    }

    private void logTransferSearchResults(TripPlan plan) {
        int totalOptions = plan.getTripOptions().size();

        // Правильный подсчет опций по типам
        long directOptions = plan.getTripOptions().stream()
                .filter(option -> option.getTripType() == TripType.DIRECT)
                .count();

        long oneTransferOptions = plan.getTripOptions().stream()
                .filter(option -> option.getTripType() == TripType.ONE_TRANSFER)
                .count();

        long twoTransferOptions = plan.getTripOptions().stream()
                .filter(option -> option.getTripType() == TripType.TWO_TRANSFERS)
                .count();

        long totalTransferOptions = oneTransferOptions + twoTransferOptions;

        if (totalOptions > 0) {
            TripOption bestTransfer = plan.getOptionWithFewestTransfers();
            log.info("Found {} transfer route options (direct: {}, 1-transfer: {}, 2-transfer: {}, total: {}). Best: {} transfers, {} minutes",
                    totalTransferOptions, directOptions, oneTransferOptions, twoTransferOptions, totalOptions,
                    bestTransfer != null ? bestTransfer.getTransfersCount() : "N/A",
                    bestTransfer != null ? bestTransfer.getTotalTravelMinutes() : "N/A");
        } else {
            log.info("No viable transfer routes found");
        }
    }


    public record Command(Location fromLocation, Location toLocation,
                          TripSearchCriteria searchCriteria, TripPlan existingPlan) {
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