package biz.ugur.busroutebackend.interfaces.rest.mobile.controller;

import biz.ugur.busroutebackend.banner.appication.dto.admin.BannerListResponse;
import biz.ugur.busroutebackend.banner.appication.dto.admin.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.appication.usecase.admin.GetAllBannersUseCase;
import biz.ugur.busroutebackend.banner.appication.usecase.admin.GetBannersByTypeUseCase;
import biz.ugur.busroutebackend.banner.appication.usecase.admin.GetBannersWithPaginationUseCase;
import biz.ugur.busroutebackend.banner.appication.usecase.client.GetBannersWithPaginationByTypeUseCase;
import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientPrincipal;
import biz.ugur.busroutebackend.interfaces.rest.mobile.response.*;
import biz.ugur.busroutebackend.interfaces.rest.transport.dto.response.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.dto.route.GetAllRoutePaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteStops;
import biz.ugur.busroutebackend.transport.application.dto.stop.GetAllStopPaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopList;
import biz.ugur.busroutebackend.transport.application.usecase.ActiveCountVehicleUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.CountVehicleUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.route.*;
import biz.ugur.busroutebackend.transport.application.usecase.stop.GetAllBusStopsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.stop.GetBusStopByIdUseCase;
import biz.ugur.busroutebackend.transport.infrastructure.services.BusStopRealTimeServiceImpl;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/mobile")
public class MobileApiController extends BaseController {

    private final GetAllBusRoutesUseCase getAllRoutesUseCase;
    private final GetAllBusStopsUseCase getAllStopsUseCase;
    private final GetAllBannersUseCase getAllBannersUseCase;
    private final GetAllBusRoutesWithPaginationUseCase  getAllBusRoutesWithPaginationUseCase;
    private final GetBannersWithPaginationByTypeUseCase getBannersWithPaginationUseCase;
    private final GetRouteByNumberUseCase getRouteByNumberUseCase;
    private final GetRouteByIdUseCase getRouteByIdUseCase;
    private final GetBusStopByIdUseCase getBusStopByIdUseCase;
    private final GetRouteStopsUseCase getRouteStopsUseCase;
    private final GetBannersByTypeUseCase getBannersByTypeUseCase;
    private final RouteIsFavoriteUseCase  routeIsFavoriteUseCase;
    private final GetRoutesByStopIdUseCase  getRoutesByStopIdUseCase;
    private final CountVehicleUseCase countVehicleUseCase;
    private final ActiveCountVehicleUseCase activeCountVehicleUseCase;
    private final BusStopRealTimeServiceImpl busStopRealTimeService;
    private final RequestedContentTypeResolver requestedContentTypeResolver;

    public MobileApiController(GetAllBusRoutesUseCase getAllRoutesUseCase,
                               GetAllBusStopsUseCase getAllStopsUseCase,
                               GetAllBannersUseCase getAllBannersUseCase,
                               GetAllBusRoutesWithPaginationUseCase getAllBusRoutesWithPaginationUseCase,
                               GetBannersWithPaginationByTypeUseCase getBannersWithPaginationUseCase,
                               GetRouteByNumberUseCase getRouteByNumberUseCase,
                               GetRouteByIdUseCase getRouteByIdUseCase,
                               GetBusStopByIdUseCase getBusStopByIdUseCase,
                               GetRouteStopsUseCase getRouteStopsUseCase,
                               GetBannersByTypeUseCase getBannersByTypeUseCase,
                               RouteIsFavoriteUseCase routeIsFavoriteUseCase,
                               GetRoutesByStopIdUseCase getRoutesByStopIdUseCase,
                               CountVehicleUseCase countVehicleUseCase,
                               ActiveCountVehicleUseCase activeCountVehicleUseCase,
                               BusStopRealTimeServiceImpl busStopRealTimeService,
                               MessageSource messageSource,
                               RequestedContentTypeResolver requestedContentTypeResolver) {
        super(messageSource);
        this.getAllRoutesUseCase = getAllRoutesUseCase;
        this.getAllStopsUseCase = getAllStopsUseCase;
        this.getAllBannersUseCase = getAllBannersUseCase;
        this.getAllBusRoutesWithPaginationUseCase = getAllBusRoutesWithPaginationUseCase;
        this.getBannersWithPaginationUseCase = getBannersWithPaginationUseCase;
        this.getRouteByNumberUseCase = getRouteByNumberUseCase;
        this.getRouteByIdUseCase = getRouteByIdUseCase;
        this.getBusStopByIdUseCase = getBusStopByIdUseCase;
        this.getRouteStopsUseCase = getRouteStopsUseCase;
        this.getBannersByTypeUseCase = getBannersByTypeUseCase;
        this.routeIsFavoriteUseCase = routeIsFavoriteUseCase;
        this.getRoutesByStopIdUseCase = getRoutesByStopIdUseCase;
        this.countVehicleUseCase = countVehicleUseCase;
        this.activeCountVehicleUseCase = activeCountVehicleUseCase;
        this.busStopRealTimeService = busStopRealTimeService;
        this.requestedContentTypeResolver = requestedContentTypeResolver;
    }

    @Override
    protected String getControllerName() {
        return MobileApiController.class.getSimpleName();
    }


    @GetMapping("/vehicle/info")
    public Mono<ResponseEntity<ApiResponse<MobileVehicleInfoResponse>>> busesInfo() {

        return ok(Mono.zip(countVehicleUseCase.execute(Mono.empty()), activeCountVehicleUseCase.execute(Mono.empty()))
                .map(tuple ->
                        MobileVehicleInfoResponse.builder()
                                .vehicleCount(tuple.getT1().count())
                                .activeVehicleCount(tuple.getT2().count())
                                .build()

                ));
    }


    @GetMapping("/routes")
    public Mono<ResponseEntity<ApiResponse<MobileRouteListResponse>>> getAllRoutes() {

        return ok(getCurrentPrincipal()
                .flatMap(principal -> {
                    return getAllRoutesUseCase.execute(Mono.empty())
                            .flatMap(routeList ->
                                    Flux.fromIterable(routeList.getRoutes())
                                            .flatMap(routeData ->
                                                    routeIsFavoriteUseCase
                                                            .execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), routeData.id()))
                                                            .map(isFavorite -> MobileRouteResponse.from(routeData, isFavorite))
                                            )
                                            .collectList()
                                            .map(mobileRoutes ->
                                                    MobileRouteListResponse.builder()
                                                            .routes(mobileRoutes)
                                                            .totalCount(routeList.getTotalCount())
                                                            .activeCount(routeList.getActiveCount())
                                                            .build()
                                            )
                            );
                }));

    }

    @GetMapping("/routes/paginated")
    public Mono<ResponseEntity<ApiResponse<MobileRouteListResponse>>> getRoutesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "routeNumber") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder) {


        GetAllRoutePaginationQuery paginationQuery = new GetAllRoutePaginationQuery(
                page + 1,
                size,
                sortField,
                sortOrder,
                true
        );

        return ok(getCurrentPrincipal().flatMap(principal ->
                getAllBusRoutesWithPaginationUseCase.execute(Mono.just(paginationQuery))
                        .flatMap(routeList ->
                                Flux.fromIterable(routeList.getRoutes())
                                        .flatMap(routeData ->
                                                routeIsFavoriteUseCase
                                                        .execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), routeData.id()))
                                                        .map(isFavorite -> MobileRouteResponse.from(routeData, isFavorite))
                                        )
                                        .collectList()
                                        .map(mobileRoutes ->
                                                MobileRouteListResponse.builder()
                                                        .routes(mobileRoutes)
                                                        .totalCount(routeList.getTotalCount())
                                                        .activeCount(routeList.getActiveCount())
                                                        .build()
                                        )
                        )
        ));
    }


    @GetMapping("/routes/{routeNumber}")
    public Mono<ResponseEntity<ApiResponse<MobileRouteResponse>>> getRouteByNumber(@PathVariable String routeNumber) {

        return ok(getCurrentPrincipal().flatMap(principal -> {
            return Mono.just(new GetRouteByNumberUseCase.Query(routeNumber))
                    .as(getRouteByNumberUseCase::execute)
                    .flatMap(routeData ->
                            routeIsFavoriteUseCase.execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), routeData.id()))
                                    .map(isFavorite -> MobileRouteResponse.from(routeData, isFavorite))
                    );
        }));
    }

    @GetMapping("/routes/id/{routeId}")
    public Mono<ResponseEntity<ApiResponse<MobileRouteResponse>>> getRouteById(@PathVariable String routeId) {

        return ok( getCurrentPrincipal().flatMap(principal -> {
            return  Mono.just(new GetRouteByIdUseCase.Query(routeId))
                    .as(getRouteByIdUseCase::execute)
                    .flatMap(routeData    ->
                            routeIsFavoriteUseCase.execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), routeId))
                                    .map(isFavorite -> MobileRouteResponse.from(routeData, isFavorite))
                    );
        }));
    }




    @GetMapping("/stops")
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

    @GetMapping("/stops/paginated")
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

    @GetMapping("/stops/{stopId}")
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


    @GetMapping("/routes/{routeId}/stops")
    public Mono<ResponseEntity<ApiResponse<RouteStops>>> getStopsByRoute(@PathVariable String routeId) {
        return ok(getRouteStopsUseCase.execute(Mono.just(routeId)));
    }


    @GetMapping("/banners")
    public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getAllBanners() {
        return ok(Mono.just(true).as(getAllBannersUseCase::execute));

    }

    @GetMapping("/banners/paginated")
    public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getBannersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "displayOrder") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder,
            @RequestParam(defaultValue = "main") String type
    ) {


        BannerPaginationQuery query =  BannerPaginationQuery.createWithType(
                page,
                size,
                sortField,
                sortOrder,
                true,
                type
        );

        return ok(Mono.just(query).as(getBannersWithPaginationUseCase::execute));
    }

    @GetMapping("/banners/type/{type}")
    public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getBannersByType(@PathVariable String type) {
        return ok(getBannersByTypeUseCase.execute(Mono.just(type)));
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

    private Mono<ClientPrincipal> getCurrentPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (ClientPrincipal) auth.getPrincipal());
    }

}