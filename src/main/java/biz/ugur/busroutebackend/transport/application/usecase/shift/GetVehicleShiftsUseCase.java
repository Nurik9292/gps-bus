package biz.ugur.busroutebackend.transport.application.usecase.shift;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.shift.VehicleShiftsData;
import biz.ugur.busroutebackend.transport.application.mapper.ShiftAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetVehicleShiftsUseCase extends BaseUseCase<Mono<String>, VehicleShiftsData> {

    private final VehicleRepository vehicleRepository;
    private final ShiftAssignmentDataMapper dataMapper;

    public GetVehicleShiftsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleRepository vehicleRepository,
            ShiftAssignmentDataMapper dataMapper) {
        super(correlationService, eventBus);
        this.vehicleRepository = vehicleRepository;
        this.dataMapper = dataMapper;
    }

    @Override
    protected Mono<VehicleShiftsData> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<VehicleShiftsData> processInternal(String vehicleIdStr) {
        VehicleId vehicleId = VehicleId.of(vehicleIdStr);

        return vehicleRepository.findById(vehicleId)
                .switchIfEmpty(Mono.error(new VehicleNotFoundException(vehicleIdStr)))
                .then(dataMapper.toVehicleShiftsData(vehicleId));
    }
}
