package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.repository.NearbyRouteQueryRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.NearbyRouteInfo;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Log4j2
@Service
public class GetNearbyRoutesUseCase
        implements UseCase<Mono<GetNearbyRoutesUseCase.Query>, Mono<List<NearbyRouteInfo>>> {

    private final NearbyRouteQueryRepository repository;
    private final CorrelationContextService correlationContextService;

    private static final int DEFAULT_RADIUS_METERS = 500;
    private static final int MAX_RADIUS_METERS = 5000;

    public GetNearbyRoutesUseCase(NearbyRouteQueryRepository repository,
                                   CorrelationContextService correlationContextService) {
        this.repository = repository;
        this.correlationContextService = correlationContextService;
    }

    @Override
    public Mono<List<NearbyRouteInfo>> execute(Mono<Query> query) {
        return correlationContextService.executeWithCorrelation(
                query.flatMap(this::executeWithCorrelation), "transport");
    }

    private Mono<List<NearbyRouteInfo>> executeWithCorrelation(Query query) {
        return correlationContextService.getCurrentCorrelationId().flatMap(correlationId -> {
            int radiusMeters = query.radiusMeters() != null
                    ? Math.min(query.radiusMeters(), MAX_RADIUS_METERS)
                    : DEFAULT_RADIUS_METERS;

            log.info("GetNearbyRoutes CorrelationId: {} - lat: {}, lon: {}, radius: {}m",
                    correlationId, query.latitude(), query.longitude(), radiusMeters);

            return repository.findRoutesNearLocation(query.latitude(), query.longitude(), radiusMeters)
                    .collectList();
        });
    }

    public record Query(Double latitude, Double longitude, Integer radiusMeters) {
        public Query(Double latitude, Double longitude) {
            this(latitude, longitude, null);
        }
    }
}
