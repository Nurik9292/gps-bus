package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.routealternative.CreateRouteAlternativeRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.routealternative.UpdateRouteAlternativeRequest;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.dto.RouteAlternativeDTO;
import biz.ugur.busroutebackend.transport.application.usecase.routealternative.*;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_ROUTE_ALTERNATIVES;

@RestController
@RequestMapping(V1_ADMIN_ROUTE_ALTERNATIVES)
@CrossOrigin(origins = "*")
public class AdminRouteAlternativeController extends BaseController {

    private final CreateRouteAlternativeUseCase createRouteAlternativeUseCase;
    private final GetRouteAlternativesUseCase getRouteAlternativesUseCase;
    private final GetAllRouteAlternativesUseCase getAllRouteAlternativesUseCase;
    private final UpdateRouteAlternativeUseCase updateRouteAlternativeUseCase;
    private final DeleteRouteAlternativeUseCase deleteRouteAlternativeUseCase;

    public AdminRouteAlternativeController(
            CreateRouteAlternativeUseCase createRouteAlternativeUseCase,
            GetRouteAlternativesUseCase getRouteAlternativesUseCase,
            GetAllRouteAlternativesUseCase getAllRouteAlternativesUseCase,
            UpdateRouteAlternativeUseCase updateRouteAlternativeUseCase,
            DeleteRouteAlternativeUseCase deleteRouteAlternativeUseCase,
            MessageSource messageSource) {
        super(messageSource);
        this.createRouteAlternativeUseCase = createRouteAlternativeUseCase;
        this.getRouteAlternativesUseCase = getRouteAlternativesUseCase;
        this.getAllRouteAlternativesUseCase = getAllRouteAlternativesUseCase;
        this.updateRouteAlternativeUseCase = updateRouteAlternativeUseCase;
        this.deleteRouteAlternativeUseCase = deleteRouteAlternativeUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminRouteAlternativeController.class.getSimpleName();
    }

   
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<RouteAlternativeDTO>>>> getAllAlternatives() {
        return okList(getAllRouteAlternativesUseCase.execute(null));
    }

  
    @GetMapping("/by-route/{primaryRouteId}")
    public Mono<ResponseEntity<ApiResponse<List<RouteAlternativeDTO>>>> getAlternativesByRoute(
            @PathVariable String primaryRouteId) {
        return okList(getRouteAlternativesUseCase.execute(
                new GetRouteAlternativesUseCase.Request(primaryRouteId)));
    }


    @PostMapping
    public Mono<ResponseEntity<ApiResponse<RouteAlternativeDTO>>> createAlternative(
            @Valid @RequestBody CreateRouteAlternativeRequest request) {
        return created(createRouteAlternativeUseCase.execute(
                new CreateRouteAlternativeUseCase.Request(
                        request.primaryRouteId(),
                        request.alternativeRouteId(),
                        request.priority(),
                        request.description(),
                        request.descriptionTm(),
                        request.descriptionEn()
                )));
    }

 
    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<RouteAlternativeDTO>>> updateAlternative(
            @PathVariable String id,
            @Valid @RequestBody UpdateRouteAlternativeRequest request) {
        return ok(updateRouteAlternativeUseCase.execute(
                new UpdateRouteAlternativeUseCase.Request(
                        id,
                        request.priority(),
                        request.description(),
                        request.descriptionTm(),
                        request.descriptionEn()
                )));
    }

  
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteAlternative(@PathVariable String id) {
        return deleteRouteAlternativeUseCase.execute(
                        new DeleteRouteAlternativeUseCase.Request(id))
                .then(noContent());
    }
}
