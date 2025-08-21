package biz.ugur.busroutebackend.transport.application.usecase.route;


import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.dto.route.UpdateRoute;
import biz.ugur.busroutebackend.transport.application.services.RouteStopsService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class UpdateBusRouteUseCase extends BaseUseCase<Mono<UpdateRoute>, RouteData> {

    private final BusRouteRepository busRouteRepository;
    private final RouteStopsService routeStopsService;

    public UpdateBusRouteUseCase(BusRouteRepository busRouteRepository,
                                 EventBus eventBus,
                                 CorrelationContextService correlationService,
                                 RouteStopsService routeStopsService) {
        super(correlationService, eventBus);
        this.busRouteRepository = busRouteRepository;
        this.routeStopsService = routeStopsService;
    }


    @Override
    protected Mono<RouteData> process(Mono<UpdateRoute> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }


    private Mono<RouteData> processInternal(UpdateRoute command) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Updating bus route: CorrelationId - {} RouteId - {}", correlationId, command.routeId());

            return busRouteRepository.findById(BusRouteId.of(command.routeId()))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus route not found: " + command.routeId())))
                    .flatMap(exsistBusRoute -> updateBusRoute(exsistBusRoute, command))
                    .flatMap(updatedRoute -> {
                        log.info("Updating route stops for route: {}", updatedRoute.getId().getValue());
                        return routeStopsService.updateRouteStops(
                                updatedRoute.getId().getValue(),
                                command.forwardStopIds(),
                                command.backwardStopIds()
                        ).thenReturn(updatedRoute);
                    })
                    .map(RouteData::fromDomain)
                    .doOnSuccess(response -> log.info("Bus route updated successfully: {}", response.routeNumber()))
                    .doOnError(error -> log.error("Failed to update bus route: {}", command.routeNumber(), error));

        });
    }

    private Mono<BusRoute> updateBusRoute(BusRoute exsistBusRoute, UpdateRoute command) {

        if (command.isActive())
            exsistBusRoute.activate();
        else exsistBusRoute.deactivate();

        exsistBusRoute.updateBasicInfo(
                command.routeNumber(),
                command.routeName(),
                command.nameTm(),
                command.nameEn(),
                command.routeColor(),
                command.estimatedDurationMinutes(),
                command.cityId());


        if (hasValidGeometry(command)) {
            processRouteGeometry(exsistBusRoute, command);
        }

        return busRouteRepository.save(exsistBusRoute);
    }


    private boolean hasValidGeometry(UpdateRoute command) {
        return (command.forwardGeometry() != null && !command.forwardGeometry().isEmpty()) ||
                (command.backwardGeometry() != null && !command.backwardGeometry().isEmpty());
    }

    private void processRouteGeometry(BusRoute busRoute, UpdateRoute command) {
        RouteGeometry forwardGeometry = createRouteGeometry(command.forwardGeometry(), "forward");
        RouteGeometry backwardGeometry = createRouteGeometry(command.backwardGeometry(), "backward");
        busRoute.updateRouteGeometry(forwardGeometry, backwardGeometry);
    }

    private RouteGeometry createRouteGeometry(List<List<Double>> coordinates, String direction) {
        return RouteGeometry.fromCoordinates(coordinates);
    }
}
