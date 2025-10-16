package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;


import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileStopListResponse;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileStopResponse;
import biz.ugur.busroutebackend.interfaces.rest.transport.V1.response.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.application.dto.stop.GetAllStopPaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopList;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRoutesByStopIdUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.stop.GetAllBusStopsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.stop.GetBusStopByIdUseCase;
import biz.ugur.busroutebackend.transport.infrastructure.services.BusStopRealTimeServiceImpl;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_STOPS;

@RestController
@RequestMapping(V1_MOBILE_STOPS)
public class MobileStopApiController extends BaseMobileController {

    private final GetAllBusStopsUseCase getAllStopsUseCase;
    private final GetRoutesByStopIdUseCase  getRoutesByStopIdUseCase;
    private final GetBusStopByIdUseCase getBusStopByIdUseCase;
    private final BusStopRealTimeServiceImpl busStopRealTimeService;

    public MobileStopApiController(MessageSource messageSource,
                                   RequestedContentTypeResolver requestedContentTypeResolver,
                                   RouteIsFavoriteUseCase routeIsFavoriteUseCase,
                                   GetAllBusStopsUseCase getAllStopsUseCase,
                                   GetRoutesByStopIdUseCase getRoutesByStopIdUseCase,
                                   GetBusStopByIdUseCase getBusStopByIdUseCase,
                                   BusStopRealTimeServiceImpl busStopRealTimeService) {
        super(messageSource, requestedContentTypeResolver, routeIsFavoriteUseCase);
        this.getAllStopsUseCase = getAllStopsUseCase;
        this.getRoutesByStopIdUseCase = getRoutesByStopIdUseCase;
        this.getBusStopByIdUseCase = getBusStopByIdUseCase;
        this.busStopRealTimeService = busStopRealTimeService;
    }

    @Override
    protected String getControllerName() {
        return MobileStopApiController.class.getSimpleName();
    }


    @GetMapping()
    public Mono<ResponseEntity<ApiResponse<MobileStopListResponse>>> getAllStops() {

        return ok(getCurrentPrincipal()
                .flatMap(principal -> {
                    return getAllStopsUseCase.execute(Mono.just(createDefaultStopPaginationQuery()))
                            .flatMap(stopList ->
                                    Flux.fromIterable(stopList.getStops())
                                            .flatMap(stopData -> {
                                                Mono<Boolean> isFavoriteMono = routeIsFavoriteUseCase
                                                        .execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), stopData.id()));

                                                Mono<List<String>> forwardRoutesMono = getRoutesByStopIdUseCase
                                                        .execute(Mono.just(new GetRoutesByStopIdUseCase.Query(stopData.id(), 0)))
                                                        .map(routes -> routes.stream()
                                                                .map(GetRoutesByStopIdUseCase.Response::routeId)
                                                                .collect(Collectors.toList()));

                                                Mono<List<String>> backwardRoutesMono = getRoutesByStopIdUseCase
                                                        .execute(Mono.just(new GetRoutesByStopIdUseCase.Query(stopData.id(), 1)))
                                                        .map(routes -> routes.stream()
                                                                .map(GetRoutesByStopIdUseCase.Response::routeId)
                                                                .collect(Collectors.toList()));

                                                return Mono.zip(isFavoriteMono, forwardRoutesMono, backwardRoutesMono)
                                                        .map(tuple ->
                                                                MobileStopResponse.from(
                                                                        stopData,
                                                                        tuple.getT1(),
                                                                        tuple.getT2(),
                                                                        tuple.getT3())
                                                        );
                                            })
                                            .collectList()
                                            .map(mobileStops -> {
                                                return MobileStopListResponse.builder()
                                                        .stops(mobileStops)
                                                        .totalCount(stopList.getTotalCount())
                                                        .activeCount(stopList.getActiveCount())
                                                        .build();
                                            })
                            );
                }));
    }

    @GetMapping("/paginated")
    public Mono<ResponseEntity<ApiResponse<StopList>>> getStopsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "stopName") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder) {

        GetAllStopPaginationQuery query = new GetAllStopPaginationQuery(
                page + 1,
                size,
                sortField,
                sortOrder,
                true
        );

        return ok(getAllStopsUseCase.execute(Mono.just(query)));
    }

    @GetMapping("/{stopId}")
    public Mono<ResponseEntity<ApiResponse<MobileStopResponse>>> getStopById(@PathVariable String stopId) {

        return ok(getCurrentPrincipal().flatMap(principal ->
                getBusStopByIdUseCase.execute(Mono.just(new GetBusStopByIdUseCase.Query(stopId)))
                        .flatMap(stopData -> {
                            Mono<Boolean> isFavoriteMono = routeIsFavoriteUseCase
                                    .execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), stopData.id()))
                                    .defaultIfEmpty(false);

                            Mono<List<String>> forwardRoutesMono = getRoutesByStopIdUseCase
                                    .execute(Mono.just(new GetRoutesByStopIdUseCase.Query(stopData.id(), 0)))
                                    .map(routes -> routes.stream()
                                            .map(GetRoutesByStopIdUseCase.Response::routeId)
                                            .collect(Collectors.toList()))
                                    .defaultIfEmpty(List.of());

                            Mono<List<String>> backwardRoutesMono = getRoutesByStopIdUseCase
                                    .execute(Mono.just(new GetRoutesByStopIdUseCase.Query(stopData.id(), 1)))
                                    .map(routes -> routes.stream()
                                            .map(GetRoutesByStopIdUseCase.Response::routeId)
                                            .collect(Collectors.toList()))
                                    .defaultIfEmpty(List.of());

                            Mono<BusStopArrivalsResponse> busStopArrivals = busStopRealTimeService.getStopArrivals(stopId);

                            return Mono.zip(isFavoriteMono, forwardRoutesMono, backwardRoutesMono, busStopArrivals)
                                    .map(tuple -> MobileStopResponse.from(
                                            stopData,
                                            tuple.getT1(),
                                            tuple.getT2(),
                                            tuple.getT3(),
                                            tuple.getT4().getArrivals()
                                    ));
                        })));

    }


    private GetAllStopPaginationQuery createDefaultStopPaginationQuery() {
        return new GetAllStopPaginationQuery(
                1,
                1500,
                "stop_name",
                "asc",
                true
        );
    }
}
