package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementAnalyticsResponse;
import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementList;
import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementStatusCounts;
import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.dto.RejectAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.ApproveAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.CancelAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.CreateAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementAnalyticsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementByIdUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementStatusCountsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementsPaginatedUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.RejectAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_AD_PLACEMENTS;

@RestController
@RequestMapping(V1_ADMIN_AD_PLACEMENTS)
public class AdminAdPlacementController extends BasePaginatedController {

    private final CreateAdPlacementUseCase createAdPlacementUseCase;
    private final GetAdPlacementByIdUseCase getAdPlacementByIdUseCase;
    private final GetAdPlacementsPaginatedUseCase getAdPlacementsPaginatedUseCase;
    private final CancelAdPlacementUseCase cancelAdPlacementUseCase;
    private final ApproveAdPlacementUseCase approveAdPlacementUseCase;
    private final RejectAdPlacementUseCase rejectAdPlacementUseCase;
    private final GetAdPlacementStatusCountsUseCase getAdPlacementStatusCountsUseCase;
    private final GetAdPlacementAnalyticsUseCase getAdPlacementAnalyticsUseCase;

    public AdminAdPlacementController(CreateAdPlacementUseCase createAdPlacementUseCase,
                                       GetAdPlacementByIdUseCase getAdPlacementByIdUseCase,
                                       GetAdPlacementsPaginatedUseCase getAdPlacementsPaginatedUseCase,
                                       CancelAdPlacementUseCase cancelAdPlacementUseCase,
                                       ApproveAdPlacementUseCase approveAdPlacementUseCase,
                                       RejectAdPlacementUseCase rejectAdPlacementUseCase,
                                       GetAdPlacementStatusCountsUseCase getAdPlacementStatusCountsUseCase,
                                       GetAdPlacementAnalyticsUseCase getAdPlacementAnalyticsUseCase,
                                       MessageSource messageSource) {
        super(messageSource);
        this.createAdPlacementUseCase = createAdPlacementUseCase;
        this.getAdPlacementByIdUseCase = getAdPlacementByIdUseCase;
        this.getAdPlacementsPaginatedUseCase = getAdPlacementsPaginatedUseCase;
        this.cancelAdPlacementUseCase = cancelAdPlacementUseCase;
        this.approveAdPlacementUseCase = approveAdPlacementUseCase;
        this.rejectAdPlacementUseCase = rejectAdPlacementUseCase;
        this.getAdPlacementStatusCountsUseCase = getAdPlacementStatusCountsUseCase;
        this.getAdPlacementAnalyticsUseCase = getAdPlacementAnalyticsUseCase;
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

    @GetMapping("/counts")
    public Mono<ResponseEntity<ApiResponse<AdPlacementStatusCounts>>> counts() {
        return ok(getAdPlacementStatusCountsUseCase.execute(null));
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

    @PostMapping("/{placementId}/approve")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> approve(
            @PathVariable String placementId,
            @AuthenticationPrincipal UserDetails admin) {
        String adminId = admin != null ? admin.getUsername() : "system";
        return ok(approveAdPlacementUseCase.execute(
                new ApproveAdPlacementUseCase.Request(placementId, adminId)));
    }

    @PostMapping("/{placementId}/reject")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> reject(
            @PathVariable String placementId,
            @Valid @RequestBody RejectAdPlacementCommand request,
            @AuthenticationPrincipal UserDetails admin) {
        String adminId = admin != null ? admin.getUsername() : "system";
        return ok(rejectAdPlacementUseCase.execute(
                new RejectAdPlacementUseCase.Request(placementId, adminId, request)));
    }

    @PostMapping("/{placementId}/cancel")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> cancel(@PathVariable String placementId) {
        return ok(cancelAdPlacementUseCase.execute(placementId));
    }

    @GetMapping("/{placementId}/analytics")
    public Mono<ResponseEntity<ApiResponse<AdPlacementAnalyticsResponse>>> getAnalytics(
            @PathVariable String placementId,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to",   required = false) Instant to) {
        return ok(getAdPlacementAnalyticsUseCase.execute(
                new GetAdPlacementAnalyticsUseCase.Query(PlacementId.of(placementId), from, to)
        ));
    }
}
