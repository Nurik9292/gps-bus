package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseFluxUseCase;
import biz.ugur.busroutebackend.transport.application.dto.RouteInAreaDTO;
import biz.ugur.busroutebackend.transport.application.dto.RoutePointDTO;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteInAreaInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


@Service
@Slf4j
public class FindRoutesInAreaUseCase extends BaseFluxUseCase<FindRoutesInAreaUseCase.Request, RouteInAreaDTO> {

    private final BusRouteRepository busRouteRepository;

    public FindRoutesInAreaUseCase(BusRouteRepository busRouteRepository,
                                   CorrelationContextService correlationContextService,
                                   EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    protected Flux<RouteInAreaDTO> process(Request request) {
        return processInternal(request);
    }

    @Override
    protected String getBoundedContext() {
        return "transport";
    }

    private Flux<RouteInAreaDTO> processInternal(Request request) {
        return correlationService.getCurrentCorrelationId().flatMapMany(correlationId -> {
            log.debug("Finding routes in area: lat={}, lon={}, radius={}m - CorrelationId: {}",
                    request.latitude, request.longitude, request.radiusMeters, correlationId);

            return busRouteRepository.findRoutesIntersectingArea(
                            request.latitude, request.longitude, request.radiusMeters)
                    .map(this::mapToDTO)
                    .doOnComplete(() -> log.debug("Routes in area search completed"));
        });
    }

    private RouteInAreaDTO mapToDTO(RouteInAreaInfo result) {
        RoutePointDTO nearestPoint = new RoutePointDTO(
                result.getNearestPointLatitude(),
                result.getNearestPointLongitude()
        );

        return new RouteInAreaDTO(
                result.getRouteId(),
                result.getRouteNumber(),
                result.getRouteName(),
                result.getRouteColor(),
                result.getDirection(),
                nearestPoint,
                result.getDistanceToCenterMeters(),
               0L
        );
    }

    public static class Request {
        public final Double latitude;
        public final Double longitude;
        public final Integer radiusMeters;

        public Request(Double latitude, Double longitude, Integer radiusMeters) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.radiusMeters = radiusMeters;
        }
    }
}