package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileRouteAlternativesResponse;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileRouteListResponse;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileRouteResponse;
import biz.ugur.busroutebackend.transport.application.dto.route.GetAllRoutePaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteStops;
import biz.ugur.busroutebackend.transport.application.usecase.route.*;
import biz.ugur.busroutebackend.transport.application.usecase.routealternative.GetActiveAlternativesForRouteUseCase;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_ROUTES;

@RestController
@RequestMapping(V1_MOBILE_ROUTES)
public class MobileRouteApiController extends BaseMobileController {

    private final GetAllBusRoutesUseCase getAllRoutesUseCase;
    private final GetRouteByNumberUseCase getRouteByNumberUseCase;
    private final GetRouteByIdUseCase getRouteByIdUseCase;
    private final GetAllBusRoutesWithPaginationUseCase getAllBusRoutesWithPaginationUseCase;
    private final GetRouteStopsUseCase getRouteStopsUseCase;
    private final GetActiveAlternativesForRouteUseCase getActiveAlternativesForRouteUseCase;

    protected MobileRouteApiController(MessageSource messageSource,
                                       RequestedContentTypeResolver requestedContentTypeResolver,
                                       GetAllBusRoutesUseCase getAllRoutesUseCase,
                                       GetRouteByNumberUseCase getRouteByNumberUseCase,
                                       GetRouteByIdUseCase getRouteByIdUseCase,
                                       RouteIsFavoriteUseCase routeIsFavoriteUseCase,
                                       GetAllBusRoutesWithPaginationUseCase getAllBusRoutesWithPaginationUseCase,
                                       GetRouteStopsUseCase getRouteStopsUseCase,
                                       GetActiveAlternativesForRouteUseCase getActiveAlternativesForRouteUseCase) {
        super(messageSource, requestedContentTypeResolver, routeIsFavoriteUseCase);
        this.getAllRoutesUseCase = getAllRoutesUseCase;
        this.getRouteByNumberUseCase = getRouteByNumberUseCase;
        this.getRouteByIdUseCase = getRouteByIdUseCase;
        this.getAllBusRoutesWithPaginationUseCase = getAllBusRoutesWithPaginationUseCase;
        this.getRouteStopsUseCase = getRouteStopsUseCase;
        this.getActiveAlternativesForRouteUseCase = getActiveAlternativesForRouteUseCase;
    }

    @Override
    protected String getControllerName() {
        return MobileRouteApiController.class.getSimpleName();
    }

    @GetMapping()
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
                                                            .pagination(routeList.getPagination())
                                                            .activeCount(routeList.getActiveCount())
                                                            .build()
                                            )
                            );
                }));

    }

    @GetMapping("/paginated")
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
                true,
                null
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
                                                        .pagination(routeList.getPagination())
                                                        .activeCount(routeList.getActiveCount())
                                                        .build()
                                        )
                        )
        ));
    }

    @GetMapping("/{routeNumber}")
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

    @GetMapping("/id/{routeId}")
    public Mono<ResponseEntity<ApiResponse<MobileRouteResponse>>> getRouteById(@PathVariable String routeId) {

        return ok(getCurrentPrincipal().flatMap(principal -> {
            return  Mono.just(new GetRouteByIdUseCase.Query(routeId))
                    .as(getRouteByIdUseCase::execute)
                    .flatMap(routeData    ->
                            routeIsFavoriteUseCase.execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), routeId))
                                    .map(isFavorite -> MobileRouteResponse.from(routeData, isFavorite))
                    );
        }));
    }

    @GetMapping("/{routeId}/stops")
    public Mono<ResponseEntity<ApiResponse<RouteStops>>> getStopsByRoute(@PathVariable String routeId) {
        return ok(getRouteStopsUseCase.execute(Mono.just(routeId)));
    }


    @GetMapping("/{routeId}/alternatives")
    public Mono<ResponseEntity<ApiResponse<MobileRouteAlternativesResponse>>> getRouteAlternatives(@PathVariable String routeId) {
        return ok(getCurrentPrincipal().flatMap(principal ->
                Mono.just(new GetRouteByIdUseCase.Query(routeId))
                        .as(getRouteByIdUseCase::execute)
                        .flatMap(primaryRoute ->
                                getActiveAlternativesForRouteUseCase.execute(new GetActiveAlternativesForRouteUseCase.Request(routeId))
                                        .flatMap(routeData ->
                                                routeIsFavoriteUseCase
                                                        .execute(new RouteIsFavoriteUseCase.Request(principal.getClientId(), routeData.id()))
                                                        .map(isFavorite -> MobileRouteResponse.from(routeData, isFavorite))
                                        )
                                        .collectList()
                                        .map(alternatives -> MobileRouteAlternativesResponse.builder()
                                                .primaryRouteId(routeId)
                                                .primaryRouteNumber(primaryRoute.routeNumber())
                                                .primaryRouteIsActive(primaryRoute.isActive())
                                                .alternatives(alternatives)
                                                .alternativesCount(alternatives.size())
                                                .build()
                                        )
                        )
        ));
    }
}
