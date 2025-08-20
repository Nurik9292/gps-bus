package biz.ugur.busroutebackend.interfaces.rest.mobile.cotroller;

import biz.ugur.busroutebackend.admin.application.dto.banner.BannerListResponse;
import biz.ugur.busroutebackend.admin.application.dto.banner.BannerPaginationQuery;
import biz.ugur.busroutebackend.admin.application.usecase.banner.GetBannersByTypeUseCase;
import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientPrincipal;
import biz.ugur.busroutebackend.interfaces.rest.mobile.response.MobileRouteListResponse;
import biz.ugur.busroutebackend.interfaces.rest.mobile.response.MobileRouteResponse;
import biz.ugur.busroutebackend.transport.application.dto.route.GetAllRoutePaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteDetail;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteList;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteStops;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopDetail;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopList;
import biz.ugur.busroutebackend.transport.application.usecase.route.*;
import biz.ugur.busroutebackend.transport.application.usecase.stop.GetAllBusStopsUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.banner.GetAllBannersUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.banner.GetBannersWithPaginationUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.stop.GetBusStopByIdUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteStopsUseCase;
import biz.ugur.busroutebackend.transport.application.dto.stop.GetAllStopPaginationQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;

@RestController
@RequestMapping("/mobile")
@RequiredArgsConstructor
@Slf4j
public class MobileApiController {

    private final GetAllBusRoutesUseCase getAllRoutesUseCase;
    private final GetAllBusStopsUseCase getAllStopsUseCase;
    private final GetAllBannersUseCase getAllBannersUseCase;
    private final GetAllBusRoutesWithPaginationUseCase  getAllBusRoutesWithPaginationUseCase;
    private final GetBannersWithPaginationUseCase getBannersWithPaginationUseCase;
    private final GetRouteByNumberUseCase getRouteByNumberUseCase;
    private final GetRouteByIdUseCase getRouteByIdUseCase;
    private final GetRouteWithGeometryUseCase getRouteGeometryUseCase;
    private final GetBusStopByIdUseCase getBusStopByIdUseCase;
    private final GetRouteStopsUseCase getRouteStopsUseCase;
    private final GetBannersByTypeUseCase getBannersByTypeUseCase;
    private final RouteIsFavoriteUseCase  routeIsFavoriteUseCase;



    @GetMapping("/routes")
    public Mono<ResponseEntity<MobileRouteListResponse>> getAllRoutes() {
        log.info("Мобильное API: Запрос на получение всех маршрутов");

        return getCurrentPrincipal()
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
                })
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Ошибка при получении маршрутов: {}", throwable.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @GetMapping("/routes/paginated")
    public Mono<ResponseEntity<MobileRouteListResponse>> getRoutesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "routeNumber") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder) {

        log.info("Mobile API: Get routes paginated - page={}, size={}, sort={}, order={}",
                page, size, sortField, sortOrder);

        GetAllRoutePaginationQuery paginationQuery = new GetAllRoutePaginationQuery(
                page + 1,
                size,
                sortField,
                sortOrder,
                true
        );

        return getCurrentPrincipal().flatMap(principal ->
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
                )
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Ошибка при получении маршрутов с пагинацией: {}", throwable.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }


    @GetMapping("/routes/{routeNumber}")
    public Mono<ResponseEntity<MobileRouteResponse>> getRouteByNumber(@PathVariable String routeNumber) {
        log.info("Mobile API: Get route by number: {}", routeNumber);

        return getCurrentPrincipal().flatMap(principal -> {
            return Mono.just(new GetRouteByNumberUseCase.Query(routeNumber))
                    .as(getRouteByNumberUseCase::execute)
                    .flatMap(routeData ->
                            routeIsFavoriteUseCase.execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), routeData.id()))
                                    .map(isFavorite -> MobileRouteResponse.from(routeData, isFavorite))
                    );
        }).map(ResponseEntity::ok);


    }

    @GetMapping("/routes/id/{routeId}")
    public Mono<ResponseEntity<MobileRouteResponse>> getRouteById(@PathVariable String routeId) {
        log.info("Mobile API: Get route by id: {}", routeId);

        return getCurrentPrincipal().flatMap(principal -> {
           return   Mono.just(new GetRouteByIdUseCase.Query(routeId))
                   .as(getRouteByIdUseCase::execute)
                   .flatMap(routeData    ->
                           routeIsFavoriteUseCase.execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), routeId))
                                   .map(isFavorite -> MobileRouteResponse.from(routeData, isFavorite))
                   );
        })
                .map(ResponseEntity::ok);
    }

    @GetMapping("/routes/id/{routeId}/geometry")
    public Mono<ResponseEntity<MobileRouteResponse>> getRouteGeometryBothDirectionsById(@PathVariable String routeId) {
        log.info("Mobile API: Get route geometry both directions by id: {}", routeId);

        return getCurrentPrincipal().flatMap(principal -> {
                    return   getRouteGeometryUseCase.execute(routeId)
                            .flatMap(routeData    ->
                                    routeIsFavoriteUseCase.execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), routeId))
                                            .map(isFavorite -> MobileRouteResponse.from(routeData, isFavorite))
                            );
                })
                .map(ResponseEntity::ok);


    }


    @GetMapping("/stops")
    public Mono<ResponseEntity<StopList>> getAllStops() {
        log.info("Mobile API: Get all stops request");
        return getAllStopsUseCase.execute(Mono.just(createDefaultStopPaginationQuery())).map(ResponseEntity::ok);

    }

    @GetMapping("/stops/paginated")
    public Mono<ResponseEntity<StopList>> getStopsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "stopName") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        log.info("Mobile API: Get stops paginated - page={}, size={}, sort={}, order={}",
                page, size, sortField, sortOrder);

        GetAllStopPaginationQuery query = new GetAllStopPaginationQuery(
                page + 1,
                size,
                sortField,
                sortOrder,
                true
        );

        return getAllStopsUseCase.execute(Mono.just(query)).map(ResponseEntity::ok);

    }

    @GetMapping("/stops/{stopId}")
    public Mono<ResponseEntity<StopDetail>> getStopById(@PathVariable String stopId) {
        log.info("Mobile API: Get stop by id: {}", stopId);

        return getBusStopByIdUseCase.execute(Mono.just(stopId)).map(ResponseEntity::ok);

    }

    @GetMapping("/routes/{routeId}/stops")
    public Mono<ResponseEntity<RouteStops>> getStopsByRoute(@PathVariable String routeId) {
        log.info("Mobile API: Get stops by route: {}", routeId);
        return getRouteStopsUseCase.execute(Mono.just(routeId)).map(ResponseEntity::ok);
    }



    @GetMapping("/banners")
    public Mono<ResponseEntity<BannerListResponse>> getAllBanners() {
        log.info("Mobile API: Get all banners request");

        return getAllBannersUseCase.execute(true).map(ResponseEntity::ok);

    }

    @GetMapping("/banners/paginated")
    public Mono<ResponseEntity<BannerListResponse>> getBannersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "displayOrder") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        log.info("Mobile API: Get banners paginated - page={}, size={}, sort={}, order={}",
                page, size, sortField, sortOrder);

        BannerPaginationQuery query = new BannerPaginationQuery(
                page,
                size,
                sortField,
                sortOrder,
                true
        );

        return getBannersWithPaginationUseCase.execute(query).map(ResponseEntity::ok);

    }

    @GetMapping("/banners/type/{type}")
    public Mono<ResponseEntity<BannerListResponse>> getBannersByType(@PathVariable String type) {
        log.info("Mobile API: Get banners by type: {}", type);

        return getBannersByTypeUseCase.execute(Mono.just(type)).map(ResponseEntity::ok);
    }


    private GetAllStopPaginationQuery createDefaultStopPaginationQuery() {
        return new GetAllStopPaginationQuery(
                1,
                100,
                "stopName",
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