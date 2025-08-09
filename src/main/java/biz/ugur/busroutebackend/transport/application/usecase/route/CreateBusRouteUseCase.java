package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.CreateRoute;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import biz.ugur.busroutebackend.transport.domain.valueobject.RoutePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class CreateBusRouteUseCase implements UseCase<Mono<CreateRoute>, Mono<RouteResult>> {

    private final BusRouteRepository busRouteRepository;
    private final EventBus eventBus;
    private final CorrelationContextService correlationService;

    public CreateBusRouteUseCase(BusRouteRepository busRouteRepository,
                                 EventBus eventBus,
                                 CorrelationContextService correlationService) {
        this.busRouteRepository = busRouteRepository;
        this.eventBus = eventBus;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<RouteResult> execute(Mono<CreateRoute> command) {

        return correlationService.executeWithCorrelation(command.flatMap(this::executeWithCorrelation), "admin");
    }

    private RouteGeometry createRouteGeometry(List<List<Double>> coordinates) {
        List<RoutePoint> points = coordinates.stream()
                .map(coord -> new biz.ugur.busroutebackend.transport.domain.valueobject.RoutePoint(coord.getFirst(), coord.get(1)))
                .toList();
        return new RouteGeometry(points);
    }

    private Mono<RouteResult> executeWithCorrelation(CreateRoute command) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Creating bus route: Correlation - {} RouteNumber - {}", correlationId, command.routeNumber());

            return createBusRoute(command)
                    .doOnNext(savedRoute -> {
                        savedRoute.getUncommittedEvents().forEach(eventBus::publish);
                        savedRoute.markEventsAsCommitted();
                    })
                    .map(RouteResult::fromDomain)
                    .doOnSuccess(response -> log.info("Bus route created successfully: {}", response.routeNumber()))
                    .doOnError(error -> log.error("Failed to create bus route: {}",command.routeNumber(), error));
        });
    }

    private Mono<BusRoute> createBusRoute(CreateRoute command) {
        BusRoute busRoute = new BusRoute(
                command.routeNumber(),
                command.routeName(),
                command.nameTm(),
                command.nameEn(),
                command.routeColor(),
                command.estimatedDurationMinutes()
        );

        if (command.forwardGeometry() != null && !command.forwardGeometry().isEmpty()) {
            RouteGeometry forwardGeometry = createRouteGeometry(command.forwardGeometry());
            RouteGeometry backwardGeometry = null;

            if (command.backwardGeometry() != null && !command.backwardGeometry().isEmpty()) {
                backwardGeometry = createRouteGeometry(command.backwardGeometry());
            }

            busRoute.updateRouteGeometry(forwardGeometry, backwardGeometry);
        }

        return busRouteRepository.save(busRoute);
    }

}
