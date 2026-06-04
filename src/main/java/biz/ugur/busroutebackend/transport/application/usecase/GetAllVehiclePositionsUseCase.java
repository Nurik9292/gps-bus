package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.service.GpsDataAggregatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetAllVehiclePositionsUseCase {

    private final GpsDataAggregatorService gpsDataAggregator;

    public GetAllVehiclePositionsUseCase(GpsDataAggregatorService gpsDataAggregator) {
        this.gpsDataAggregator = gpsDataAggregator;
    }

    public Mono<List<GpsPositionDTO>> execute(Integer limit, Boolean active) {
        log.debug("Fetching vehicle positions via aggregator (limit={}, active={})", limit, active);

        return gpsDataAggregator.fetchAllPositionsFromAllProviders()
                .map(positions -> filterAndLimit(positions, active, limit))
                .onErrorResume(error -> {
                    log.error("Vehicle positions endpoint degraded — returning empty list. Error: {}",
                            error.getMessage(), error);
                    return Mono.just(List.of());
                })
                .doOnSuccess(positions ->
                        log.info("Returning {} vehicle positions", positions.size()));
    }

    private List<GpsPositionDTO> filterAndLimit(List<GpsPositionDTO> positions,
                                                  Boolean active,
                                                  Integer limit) {
        List<GpsPositionDTO> filtered = (active != null && active)
                ? positions.stream()
                        .filter(p -> p.getAttributes() != null
                                && Boolean.TRUE.equals(p.getAttributes().getMotion()))
                        .toList()
                : positions;

        if (limit == null || limit <= 0 || limit >= filtered.size()) {
            return filtered;
        }
        return filtered.subList(0, limit);
    }
}
