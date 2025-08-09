package biz.ugur.busroutebackend.transport.application.usecase.route;


import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.route.BusRouteCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.route.BusRouteResponse;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Slf4j
public class UpdateBusRouteUseCase implements UseCase<UpdateBusRouteUseCase.Request, Mono<BusRouteResponse>> {

    private final BusRouteRepository busRouteRepository;
    private final EventBus eventBus;

    public UpdateBusRouteUseCase(BusRouteRepository busRouteRepository, EventBus eventBus) {
        this.busRouteRepository = busRouteRepository;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<BusRouteResponse> execute(Request request) {
        log.info("Updating bus route: {}", request.routeId);

        return busRouteRepository.findById(BusRouteId.of(request.routeId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus route not found: " + request.routeId)))
                .flatMap(busRoute -> {
                    busRoute.updateRouteInfo(
                            request.updateRequest.getRouteName(),
                            request.updateRequest.getNameTm(),
                            request.updateRequest.getNameEn(),
                            request.updateRequest.getEstimatedDurationMinutes()
                    );

                    if (request.updateRequest.getIsActive() != null) {
                        if (request.updateRequest.getIsActive()) {
                            busRoute.activate();
                        } else {
                            busRoute.deactivate();
                        }
                    }

                    if (request.updateRequest.getForwardGeometry() != null && !request.updateRequest.getForwardGeometry().isEmpty()) {
                        RouteGeometry forwardGeometry = createRouteGeometry(request.updateRequest.getForwardGeometry());
                        RouteGeometry backwardGeometry = null;

                        if (request.updateRequest.getBackwardGeometry() != null && !request.updateRequest.getBackwardGeometry().isEmpty()) {
                            backwardGeometry = createRouteGeometry(request.updateRequest.getBackwardGeometry());
                        }

                        busRoute.updateRouteGeometry(forwardGeometry, backwardGeometry);
                    }

                    return busRouteRepository.save(busRoute);
                })
                .doOnNext(savedRoute -> {
                    savedRoute.getUncommittedEvents().forEach(eventBus::publish);
                    savedRoute.markEventsAsCommitted();
                })
                .map(this::toResponse)
                .doOnSuccess(response -> log.info("Bus route updated successfully: {}", response.getRouteNumber()))
                .doOnError(error -> log.error("Failed to update bus route: {}", request.routeId, error));
    }

    private RouteGeometry createRouteGeometry(List<List<Double>> coordinates) {
        List<biz.ugur.busroutebackend.transport.domain.valueobject.RoutePoint> points = coordinates.stream()
                .map(coord -> new biz.ugur.busroutebackend.transport.domain.valueobject.RoutePoint(coord.get(0), coord.get(1)))
                .toList();
        return new RouteGeometry(points);
    }

    private BusRouteResponse toResponse(biz.ugur.busroutebackend.transport.domain.model.BusRoute busRoute) {
        return new BusRouteResponse(
                busRoute.getId().getValue(),
                busRoute.getRouteNumber(),
                busRoute.getRouteName(),
                busRoute.getNameTm(),
                busRoute.getNameEn(),
                busRoute.getRouteColor(),
                busRoute.getIsActive(),
                busRoute.getEstimatedDurationMinutes(),
                0, // forward stops count
                0, // backward stops count
                busRoute.getTotalDistanceForwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceForwardMeters()).divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                busRoute.getTotalDistanceBackwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceBackwardMeters()).divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                0L, // active vehicles count
                busRoute.getCreatedAt(),
                busRoute.getUpdatedAt()
        );
    }

    public record Request(String routeId, BusRouteCreateRequest updateRequest) {}
}
