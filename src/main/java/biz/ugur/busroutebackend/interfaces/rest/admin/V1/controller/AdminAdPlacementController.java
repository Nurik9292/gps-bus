package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementList;
import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.CancelAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.CreateAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementByIdUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementsPaginatedUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_AD_PLACEMENTS;

@RestController
@RequestMapping(V1_ADMIN_AD_PLACEMENTS)
@CrossOrigin(origins = "*")
public class AdminAdPlacementController extends BasePaginatedController {

    private final CreateAdPlacementUseCase createAdPlacementUseCase;
    private final GetAdPlacementByIdUseCase getAdPlacementByIdUseCase;
    private final GetAdPlacementsPaginatedUseCase getAdPlacementsPaginatedUseCase;
    private final CancelAdPlacementUseCase cancelAdPlacementUseCase;

    public AdminAdPlacementController(CreateAdPlacementUseCase createAdPlacementUseCase,
                                       GetAdPlacementByIdUseCase getAdPlacementByIdUseCase,
                                       GetAdPlacementsPaginatedUseCase getAdPlacementsPaginatedUseCase,
                                       CancelAdPlacementUseCase cancelAdPlacementUseCase,
                                       MessageSource messageSource) {
        super(messageSource);
        this.createAdPlacementUseCase = createAdPlacementUseCase;
        this.getAdPlacementByIdUseCase = getAdPlacementByIdUseCase;
        this.getAdPlacementsPaginatedUseCase = getAdPlacementsPaginatedUseCase;
        this.cancelAdPlacementUseCase = cancelAdPlacementUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminAdPlacementController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<AdPlacementList>>> list(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(value = "business_id", required = false) String businessId) {
        validatePagination(page, size);
        return okPaginated(getAdPlacementsPaginatedUseCase.execute(
                new GetAdPlacementsPaginatedUseCase.Query(page, size, status, businessId)));
    }

    @GetMapping("/{placementId}")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> getById(@PathVariable String placementId) {
        return ok(getAdPlacementByIdUseCase.execute(placementId));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> create(
            @Valid @RequestBody CreateAdPlacementCommand request) {
        return created(createAdPlacementUseCase.execute(Mono.just(request)));
    }

    @PostMapping("/{placementId}/cancel")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> cancel(@PathVariable String placementId) {
        return ok(cancelAdPlacementUseCase.execute(placementId));
    }
}
