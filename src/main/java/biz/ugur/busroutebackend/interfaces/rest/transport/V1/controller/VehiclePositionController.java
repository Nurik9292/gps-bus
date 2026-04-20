package biz.ugur.busroutebackend.interfaces.rest.transport.V1.controller;

import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.usecase.GetAllVehiclePositionsUseCase;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;


@RestController
@RequestMapping("/api/vehicles")
public class VehiclePositionController extends BaseController {

    private final GetAllVehiclePositionsUseCase getAllVehiclePositionsUseCase;

    public VehiclePositionController(GetAllVehiclePositionsUseCase getAllVehiclePositionsUseCase,
                                     MessageSource messageSource) {
        super(messageSource);
        this.getAllVehiclePositionsUseCase = getAllVehiclePositionsUseCase;
    }

    @Override
    protected String getControllerName() {
        return VehiclePositionController.class.getSimpleName();
    }


    @GetMapping("/positions")
    public Mono<ResponseEntity<ApiResponse<List<GpsPositionDTO>>>> getVehiclePositions(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean active) {

        return ok(getAllVehiclePositionsUseCase.execute(limit, active));
    }
}
