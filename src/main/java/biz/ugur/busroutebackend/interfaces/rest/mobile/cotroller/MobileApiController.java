package biz.ugur.busroutebackend.interfaces.rest.mobile.cotroller;

import biz.ugur.busroutebackend.admin.application.dto.banner.BannerListResponse;
import biz.ugur.busroutebackend.admin.application.dto.banner.BannerPaginationQuery;
import biz.ugur.busroutebackend.admin.application.usecase.banner.GetBannersByTypeUseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.*;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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



    @GetMapping("/routes")
    public Mono<ResponseEntity<RouteList>> getAllRoutes() {
        log.info("Mobile API: Get all routes request");
        return getAllRoutesUseCase.execute(Mono.empty())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/routes/paginated")
    public Mono<ResponseEntity<?>> getRoutesPaginated(
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

        return getAllBusRoutesWithPaginationUseCase.execute(Mono.just(paginationQuery))
                .map(ResponseEntity::ok);

    }

    @GetMapping("/routes/{routeNumber}")
    public Mono<ResponseEntity<RouteResult>> getRouteByNumber(@PathVariable String routeNumber) {
        log.info("Mobile API: Get route by number: {}", routeNumber);

        return Mono.just(new GetRouteByNumberUseCase.Query(routeNumber))
                .as(getRouteByNumberUseCase::execute)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/routes/id/{routeId}")
    public Mono<ResponseEntity<RouteResult>> getRouteById(@PathVariable String routeId) {
        log.info("Mobile API: Get route by id: {}", routeId);

        return Mono.just(new GetRouteByIdUseCase.Query(routeId))
                .as(getRouteByIdUseCase::execute)
                .map(ResponseEntity::ok);

    }

    @GetMapping("/routes/id/{routeId}/geometry")
    public Mono<ResponseEntity<?>> getRouteGeometryBothDirectionsById(@PathVariable String routeId) {
        log.info("Mobile API: Get route geometry both directions by id: {}", routeId);
        return getRouteGeometryUseCase.execute(routeId).map(ResponseEntity::ok);

    }


    @GetMapping("/stops")
    public Mono<ResponseEntity<StopList>> getAllStops() {
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
        return getBusStopByIdUseCase.execute(Mono.just(stopId)).map(ResponseEntity::ok);

    }

    @GetMapping("/routes/{routeId}/stops")
    public Mono<ResponseEntity<RouteStops>> getStopsByRoute(@PathVariable String routeId) {
        return getRouteStopsUseCase.execute(Mono.just(routeId)).map(ResponseEntity::ok);
    }



    @GetMapping("/banners")
    public Mono<ResponseEntity<BannerListResponse>> getAllBanners() {
        return Mono.just(true)
                .as(getAllBannersUseCase::execute)
                .map(ResponseEntity::ok);

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

        return Mono.just(query).as(getBannersWithPaginationUseCase::execute).map(ResponseEntity::ok);

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





}