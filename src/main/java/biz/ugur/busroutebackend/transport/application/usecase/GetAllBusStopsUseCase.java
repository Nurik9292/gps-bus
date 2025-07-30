package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.BusStopListResponse;
import biz.ugur.busroutebackend.transport.application.dto.BusStopResponse;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAllBusStopsUseCase implements UseCase<GetAllBusStopsUseCase.Request, Mono<BusStopListResponse>> {

    private final BusStopRepository busStopRepository;
    private final DatabaseClient databaseClient;

    public GetAllBusStopsUseCase(BusStopRepository busStopRepository, DatabaseClient databaseClient) {
        this.busStopRepository = busStopRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<BusStopListResponse> execute(Request request) {
        log.debug("Fetching bus stops - page: {}, size: {}, active: {}",
                request.page, request.size, request.activeOnly);

        return getStopsWithPagination(request)
                .collectList()
                .flatMap(stops -> countTotalStops(request.activeOnly)
                        .map(totalCount -> new BusStopListResponse(
                                stops.stream().map(this::toResponse).toList(),
                                totalCount,
                                request.page,
                                request.size
                        )))
                .doOnSuccess(response -> log.debug("Retrieved {} bus stops (total: {})",
                        response.getStops().size(), response.getTotalCount()));
    }

    private Flux<BusStop> getStopsWithPagination(Request request) {
        String sql = """
            SELECT * FROM bus_stops 
            WHERE (:activeOnly = false OR is_active = true)
            ORDER BY stop_name
            LIMIT :size OFFSET :offset
            """;

        return databaseClient.sql(sql)
                .bind("activeOnly", request.activeOnly != null ? request.activeOnly : false)
                .bind("size", request.size)
                .bind("offset", request.page * request.size)
                .map(this::mapRowToBusStop)
                .all();
    }

    private Mono<Long> countTotalStops(Boolean activeOnly) {
        String sql = """
            SELECT COUNT(*) FROM bus_stops 
            WHERE (:activeOnly = false OR is_active = true)
            """;

        return databaseClient.sql(sql)
                .bind("activeOnly", activeOnly != null ? activeOnly : false)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    private BusStop mapRowToBusStop(Row row, RowMetadata metadata) {
        return new BusStop(
                BusStopId.of(row.get("id", String.class)),
                row.get("stop_name", String.class),
                row.get("stop_code", String.class),
                row.get("latitude", java.math.BigDecimal.class),
                row.get("longitude", java.math.BigDecimal.class),
                row.get("is_active", Boolean.class),
                row.get("is_major_stop", Boolean.class)
        );
    }

    private BusStopResponse toResponse(BusStop busStop) {
        return new BusStopResponse(
                busStop.getId().getValue(),
                busStop.getStopName(),
                busStop.getStopCode(),
                busStop.getLatitude(),
                busStop.getLongitude(),
                busStop.getIsActive(),
                busStop.getIsMajorStop(),
                busStop.getServingRoutesCount()
        );
    }

    public record Request(int page, int size, Boolean activeOnly) {}
}