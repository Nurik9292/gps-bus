package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.application.services.NameHistoryRecorder;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.NameHistoryRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Service
public class RestoreRouteNameUseCase extends BaseUseCase<Mono<RestoreRouteNameUseCase.Query>, RouteData> {

    public record Query(String routeId, String field) {
    }

    private static final Set<String> RESTORABLE_FIELDS = Set.of("routeName", "nameEn", "nameTm");

    private final BusRouteRepository busRouteRepository;
    private final NameHistoryRepository nameHistoryRepository;
    private final NameHistoryRecorder nameHistoryRecorder;
    private final RouteDataMapper routeDataMapper;

    public RestoreRouteNameUseCase(BusRouteRepository busRouteRepository,
                                   NameHistoryRepository nameHistoryRepository,
                                   NameHistoryRecorder nameHistoryRecorder,
                                   RouteDataMapper routeDataMapper,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.busRouteRepository = busRouteRepository;
        this.nameHistoryRepository = nameHistoryRepository;
        this.nameHistoryRecorder = nameHistoryRecorder;
        this.routeDataMapper = routeDataMapper;
    }

    @Override
    protected Mono<RouteData> process(Mono<Query> request) {
        return request.flatMap(query -> {
            if (!RESTORABLE_FIELDS.contains(query.field())) {
                return Mono.error(new IllegalArgumentException(
                        "Field is not restorable: " + query.field()));
            }
            return nameHistoryRepository
                    .findByEntityAndField(NameHistoryRecorder.KIND_ROUTE, query.routeId(), query.field())
                    .switchIfEmpty(Mono.error(new IllegalArgumentException(
                            "No previous value recorded for field: " + query.field())))
                    .flatMap(prev -> busRouteRepository.findById(BusRouteId.of(query.routeId()))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                    "Bus route not found: " + query.routeId())))
                            .flatMap(route -> {
                                BusRoute restored = route.updateBasicInfo(
                                        route.getRouteNumber(),
                                        "routeName".equals(query.field()) ? prev.oldValue() : route.getRouteName(),
                                        "nameTm".equals(query.field()) ? prev.oldValue() : route.getNameTm(),
                                        "nameEn".equals(query.field()) ? prev.oldValue() : route.getNameEn(),
                                        route.getRouteColor(),
                                        route.getEstimatedDurationMinutes(),
                                        route.getCityId());
                                return busRouteRepository.save(restored)
                                        .flatMap(saved -> nameHistoryRecorder.recordRouteChanges(route, saved)
                                                .thenReturn(saved));
                            })
                            .flatMap(routeDataMapper::toRouteData));
        });
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }
}
