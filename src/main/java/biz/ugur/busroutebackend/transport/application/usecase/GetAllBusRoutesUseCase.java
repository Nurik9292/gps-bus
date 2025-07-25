package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.BusRouteListResponse;
import biz.ugur.busroutebackend.transport.application.dto.BusRouteResponse;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
public class GetAllBusRoutesUseCase implements UseCase<GetAllBusRoutesUseCase.Request, Mono<BusRouteListResponse>> {

    private final BusRouteRepository busRouteRepository;
    private final DatabaseClient databaseClient;

    public GetAllBusRoutesUseCase(BusRouteRepository busRouteRepository, DatabaseClient databaseClient) {
        this.busRouteRepository = busRouteRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<BusRouteListResponse> execute(Request request) {
        log.debug("Fetching bus routes - page: {}, size: {}, active: {}",
                request.page, request.size, request.activeOnly);

        return getRoutesWithPagination(request)
                .collectList()
                .flatMap(routes -> countTotalRoutes(request.activeOnly)
                        .map(totalCount -> new BusRouteListResponse(
                                routes.stream().map(this::toResponse).toList(),
                                totalCount,
                                request.page,
                                request.size
                        )))
                .doOnSuccess(response -> log.debug("Retrieved {} bus routes (total: {})",
                        response.getRoutes().size(), response.getTotalCount()));
    }

    private reactor.core.publisher.Flux<BusRoute> getRoutesWithPagination(Request request) {
        String sql = """
            SELECT br.* FROM bus_routes br
            WHERE (:activeOnly = false OR br.is_active = true)
            ORDER BY br.route_number
            LIMIT :size OFFSET :offset
            """;

        return databaseClient.sql(sql)
                .bind("activeOnly", request.activeOnly != null ? request.activeOnly : false)
                .bind("size", request.size)
                .bind("offset", request.page * request.size)
                .map(this::mapRowToBusRoute)
                .all();
    }

    private Mono<Long> countTotalRoutes(Boolean activeOnly) {
        String sql = """
            SELECT COUNT(*) FROM bus_routes 
            WHERE (:activeOnly = false OR is_active = true)
            """;

        return databaseClient.sql(sql)
                .bind("activeOnly", activeOnly != null ? activeOnly : false)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    private BusRoute mapRowToBusRoute(Row row, RowMetadata metadata) {
        return new BusRoute(
                biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId.of(row.get("id", String.class)),
                row.get("route_number", String.class),
                row.get("route_name", String.class),
                row.get("route_name_tm", String.class),
                row.get("route_color", String.class),
                row.get("is_active", Boolean.class),
                row.get("fare_price", java.math.BigDecimal.class),
                row.get("estimated_duration_minutes", Integer.class),
                row.get("route_geometry_forward", String.class),
                row.get("route_geometry_backward", String.class),
                row.get("total_distance_forward_meters", Integer.class),
                row.get("total_distance_backward_meters", Integer.class)
        );
    }

    private BusRouteResponse toResponse(BusRoute busRoute) {
        return new BusRouteResponse(
                busRoute.getId().getValue(),
                busRoute.getRouteNumber(),
                busRoute.getRouteName(),
                busRoute.getRouteNameTm(),
                busRoute.getRouteColor(),
                busRoute.getIsActive(),
                busRoute.getFarePrice(),
                busRoute.getEstimatedDurationMinutes(),
                0, // forward stops count - будет вычислено отдельно
                0, // backward stops count - будет вычислено отдельно
                busRoute.getTotalDistanceForwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceForwardMeters()).divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                busRoute.getTotalDistanceBackwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceBackwardMeters()).divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                0L // active vehicles count - будет вычислено отдельно
        );
    }

    public record Request(int page, int size, Boolean activeOnly) {}
}