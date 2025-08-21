package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.CreateRoute;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;


@Service
@Slf4j
public class CreateBusRouteUseCase extends BaseUseCase<Mono<CreateRoute>, RouteData> {

    private final BusRouteRepository busRouteRepository;
    private final RouteStopsService routeStopsService;

    public CreateBusRouteUseCase(BusRouteRepository busRouteRepository,
                                 RouteStopsService routeStopsService,
                                 EventBus eventBus,
                                 CorrelationContextService correlationService) {
        super(correlationService, eventBus);
        this.busRouteRepository = busRouteRepository;
        this.routeStopsService = routeStopsService;
    }

    @Override
    protected Mono<RouteData> process(Mono<CreateRoute> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<RouteData> processInternal(CreateRoute command) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Creating bus route: Correlation - {} RouteNumber - {} with {} forward stops and {} backward stops",
                    correlationId, command.routeNumber(),
                    command.forwardStopIds().size(), command.backwardStopIds().size());

            return createBusRoute(command)
                    .flatMap(savedRoute -> {
                        if (command.hasStops()) {
                            log.info("Saving route stops for route: {}", savedRoute.getId().getValue());
                            return routeStopsService.saveRouteStops(
                                    savedRoute.getId().getValue(),
                                    command.forwardStopIds(),
                                    command.backwardStopIds()
                            ).thenReturn(savedRoute);
                        } else {
                            log.debug("No stops to save for route: {}", savedRoute.getId().getValue());
                            return Mono.just(savedRoute);
                        }
                    })

                    .map(RouteData::fromDomain)
                    .doOnSuccess(response -> log.info("Bus route created successfully: {} with stops", response.routeNumber()))
                    .doOnError(error -> log.error("Failed to create bus route: {}", command.routeNumber(), error));
        });
    }

    private Mono<BusRoute> createBusRoute(CreateRoute command) {
        BusRoute busRoute = new BusRoute(
                command.routeNumber(),
                command.routeName(),
                command.nameTm(),
                command.nameEn(),
                command.routeColor(),
                command.cityId(),
                command.estimatedDurationMinutes()
        );

        if (hasValidGeometry(command)) {
            processRouteGeometry(busRoute, command);
        }

        return busRouteRepository.save(busRoute);
    }

    private boolean hasValidGeometry(CreateRoute command) {
        return (command.forwardGeometry() != null && !command.forwardGeometry().isEmpty()) ||
                (command.backwardGeometry() != null && !command.backwardGeometry().isEmpty());
    }

    private void processRouteGeometry(BusRoute busRoute, CreateRoute command) {
        RouteGeometry forwardGeometry = createRouteGeometry(command.forwardGeometry(), "forward");
        RouteGeometry backwardGeometry = createRouteGeometry(command.backwardGeometry(), "backward");
        busRoute.updateRouteGeometry(forwardGeometry, backwardGeometry);
    }

    private RouteGeometry createRouteGeometry(List<List<Double>> coordinates, String direction) {
        if (coordinates == null || coordinates.isEmpty()) {
            return null;
        }
        return RouteGeometry.fromCoordinates(coordinates);
    }


}