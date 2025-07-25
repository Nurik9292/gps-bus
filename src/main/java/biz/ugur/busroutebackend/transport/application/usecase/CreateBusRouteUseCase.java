package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.BusRouteCreateRequest;
import biz.ugur.busroutebackend.transport.application.dto.BusRouteResponse;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Slf4j
public class CreateBusRouteUseCase implements UseCase<BusRouteCreateRequest, Mono<BusRouteResponse>> {

    private final BusRouteRepository busRouteRepository;
    private final EventBus eventBus;

    public CreateBusRouteUseCase(BusRouteRepository busRouteRepository, EventBus eventBus) {
        this.busRouteRepository = busRouteRepository;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<BusRouteResponse> execute(BusRouteCreateRequest request) {
        log.info("Creating bus route: {}", request.getRouteNumber());

        return busRouteRepository.existsByRouteNumber(request.getRouteNumber())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Route number already exists: " + request.getRouteNumber()));
                    }

                    BusRoute busRoute = new BusRoute(
                            request.getRouteNumber(),
                            request.getRouteName(),
                            request.getRouteNameTm(),
                            request.getRouteColor()
                    );

                    busRoute.updateRouteInfo(
                            request.getRouteName(),
                            request.getRouteNameTm(),
                            request.getFarePrice(),
                            request.getEstimatedDurationMinutes()
                    );

                    if (request.getForwardGeometry() != null && !request.getForwardGeometry().isEmpty()) {
                        RouteGeometry forwardGeometry = createRouteGeometry(request.getForwardGeometry());
                        RouteGeometry backwardGeometry = null;

                        if (request.getBackwardGeometry() != null && !request.getBackwardGeometry().isEmpty()) {
                            backwardGeometry = createRouteGeometry(request.getBackwardGeometry());
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
                .doOnSuccess(response -> log.info("Bus route created successfully: {}", response.getRouteNumber()))
                .doOnError(error -> log.error("Failed to create bus route: {}", request.getRouteNumber(), error));
    }

    private RouteGeometry createRouteGeometry(List<List<Double>> coordinates) {
        List<biz.ugur.busroutebackend.transport.domain.valueobject.RoutePoint> points = coordinates.stream()
                .map(coord -> new biz.ugur.busroutebackend.transport.domain.valueobject.RoutePoint(coord.get(0), coord.get(1)))
                .toList();
        return new RouteGeometry(points);
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
                0,
                0, // backward stops count - будет вычислено отдельно
                busRoute.getTotalDistanceForwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceForwardMeters()).divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                busRoute.getTotalDistanceBackwardMeters() != null ?
                        new BigDecimal(busRoute.getTotalDistanceBackwardMeters()).divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP) : null,
                0L // active vehicles count - будет вычислено отдельно
        );
    }
}
