package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import biz.ugur.busroutebackend.transport.application.dto.stop.UpdateStop;
import biz.ugur.busroutebackend.transport.application.services.NameHistoryRecorder;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.NameHistoryRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Service
public class RestoreStopNameUseCase extends BaseUseCase<Mono<RestoreStopNameUseCase.Query>, StopData> {

    public record Query(String stopId, String field) {
    }

    private static final Set<String> RESTORABLE_FIELDS = Set.of("stopName", "nameEn", "nameTm");

    private final BusStopRepository busStopRepository;
    private final NameHistoryRepository nameHistoryRepository;
    private final UpdateBusStopUseCase updateBusStopUseCase;

    public RestoreStopNameUseCase(BusStopRepository busStopRepository,
                                  NameHistoryRepository nameHistoryRepository,
                                  UpdateBusStopUseCase updateBusStopUseCase,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.busStopRepository = busStopRepository;
        this.nameHistoryRepository = nameHistoryRepository;
        this.updateBusStopUseCase = updateBusStopUseCase;
    }

    @Override
    protected Mono<StopData> process(Mono<Query> request) {
        return request.flatMap(query -> {
            if (!RESTORABLE_FIELDS.contains(query.field())) {
                return Mono.error(new IllegalArgumentException(
                        "Field is not restorable: " + query.field()));
            }
            return nameHistoryRepository
                    .findByEntityAndField(NameHistoryRecorder.KIND_STOP, query.stopId(), query.field())
                    .switchIfEmpty(Mono.error(new IllegalArgumentException(
                            "No previous value recorded for field: " + query.field())))
                    .flatMap(prev -> busStopRepository.findById(BusStopId.of(query.stopId()))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                    "Bus stop not found: " + query.stopId())))
                            .flatMap(stop -> {
                                UpdateStop current = UpdateStop.fromDomain(stop);
                                UpdateStop restored = new UpdateStop(
                                        current.stopId(),
                                        "stopName".equals(query.field()) ? prev.oldValue() : current.stopName(),
                                        "nameEn".equals(query.field()) ? prev.oldValue() : current.nameEn(),
                                        "nameTm".equals(query.field()) ? prev.oldValue() : current.nameTm(),
                                        current.latitude(),
                                        current.longitude(),
                                        current.isActive(),
                                        current.isMajorStop(),
                                        current.cityId());
                                return updateBusStopUseCase.execute(Mono.just(restored));
                            }));
        });
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }
}
