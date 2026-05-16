package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementAnalyticsResponse;
import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementAnalyticsTrendResponse;
import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementList;
import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementStatusCounts;
import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.RejectAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.dto.SalesReportResponse;
import biz.ugur.busroutebackend.advertising.application.dto.UpdateEditorialAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.ApproveAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.CancelAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.PauseAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.ResumeAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.CreateAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementAnalyticsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementAnalyticsTrendUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementByIdUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementStatusCountsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdPlacementsPaginatedUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetSalesReportUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.RejectAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.UpdateEditorialAdPlacementUseCase;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
    private final GetAdPlacementAnalyticsTrendUseCase getAdPlacementAnalyticsTrendUseCase;
    private final GetSalesReportUseCase getSalesReportUseCase;
    private final UpdateEditorialAdPlacementUseCase updateEditorialUseCase;
    private final PauseAdPlacementUseCase pauseAdPlacementUseCase;
    private final ResumeAdPlacementUseCase resumeAdPlacementUseCase;
    private final SecurityContextService securityContextService;

    public AdminAdPlacementController(CreateAdPlacementUseCase createAdPlacementUseCase,
                                       GetAdPlacementByIdUseCase getAdPlacementByIdUseCase,
                                       GetAdPlacementsPaginatedUseCase getAdPlacementsPaginatedUseCase,
                                       CancelAdPlacementUseCase cancelAdPlacementUseCase,
                                       ApproveAdPlacementUseCase approveAdPlacementUseCase,
                                       RejectAdPlacementUseCase rejectAdPlacementUseCase,
                                       GetAdPlacementStatusCountsUseCase getAdPlacementStatusCountsUseCase,
                                       GetAdPlacementAnalyticsUseCase getAdPlacementAnalyticsUseCase,
                                       GetAdPlacementAnalyticsTrendUseCase getAdPlacementAnalyticsTrendUseCase,
                                       GetSalesReportUseCase getSalesReportUseCase,
                                       UpdateEditorialAdPlacementUseCase updateEditorialUseCase,
                                       PauseAdPlacementUseCase pauseAdPlacementUseCase,
                                       ResumeAdPlacementUseCase resumeAdPlacementUseCase,
                                       SecurityContextService securityContextService,
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
        this.getAdPlacementAnalyticsTrendUseCase = getAdPlacementAnalyticsTrendUseCase;
        this.getSalesReportUseCase = getSalesReportUseCase;
        this.updateEditorialUseCase = updateEditorialUseCase;
        this.pauseAdPlacementUseCase = pauseAdPlacementUseCase;
        this.resumeAdPlacementUseCase = resumeAdPlacementUseCase;
        this.securityContextService = securityContextService;
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
            @RequestParam(value = "business_id", required = false) String businessId,
            @RequestParam(required = false) String kind) {
        validatePagination(page, size);
        return okPaginated(getAdPlacementsPaginatedUseCase.execute(
                new GetAdPlacementsPaginatedUseCase.Query(page, size, status, businessId, kind)));
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
    public Mono<ResponseEntity<ApiResponse<CreateAdPlacementResponse>>> create(
            @Valid @RequestBody CreateAdPlacementCommand request) {
        return created(createAdPlacementUseCase.execute(Mono.just(request)));
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> updateEditorial(
            @PathVariable("id") String id,
            @RequestBody UpdateEditorialAdPlacementCommand body) {
        UpdateEditorialAdPlacementCommand cmd = new UpdateEditorialAdPlacementCommand(
                id,
                body.title(), body.content(), body.imageUrl(), body.targetUrl(), body.ctaText(),
                body.startsAt(), body.endsAt(),
                body.targets(), body.displayOrder(), body.contentType());
        return ok(updateEditorialUseCase.execute(Mono.just(cmd)));
    }

    @PostMapping("/{placementId}/approve")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> approve(
            @PathVariable String placementId) {
        return ok(securityContextService.getCurrentUsername()
                .flatMap(adminId -> approveAdPlacementUseCase.execute(
                        new ApproveAdPlacementUseCase.Request(placementId, adminId))));
    }

    @PostMapping("/{placementId}/reject")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> reject(
            @PathVariable String placementId,
            @Valid @RequestBody RejectAdPlacementCommand request) {
        return ok(securityContextService.getCurrentUsername()
                .flatMap(adminId -> rejectAdPlacementUseCase.execute(
                        new RejectAdPlacementUseCase.Request(placementId, adminId, request))));
    }

    @PostMapping("/{placementId}/cancel")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> cancel(@PathVariable String placementId) {
        return ok(cancelAdPlacementUseCase.execute(placementId));
    }

    @PostMapping("/{placementId}/pause")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> pause(@PathVariable String placementId) {
        return ok(pauseAdPlacementUseCase.execute(placementId));
    }

    @PostMapping("/{placementId}/resume")
    public Mono<ResponseEntity<ApiResponse<AdPlacementResponse>>> resume(@PathVariable String placementId) {
        return ok(resumeAdPlacementUseCase.execute(placementId));
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

    @GetMapping("/{placementId}/analytics/trend")
    public Mono<ResponseEntity<ApiResponse<AdPlacementAnalyticsTrendResponse>>> getAnalyticsTrend(
            @PathVariable String placementId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ok(getAdPlacementAnalyticsTrendUseCase.execute(
                new GetAdPlacementAnalyticsTrendUseCase.Query(PlacementId.of(placementId), from, to)
        ));
    }

    @GetMapping("/sales-report")
    public Mono<ResponseEntity<ApiResponse<SalesReportResponse>>> getSalesReport(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(name = "payment_status", required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) PaymentProvider provider) {
        Instant fromOrDefault = from != null ? from : Instant.now().minus(Duration.ofDays(30));
        Instant toOrDefault   = to   != null ? to   : Instant.now();
        return ok(getSalesReportUseCase.execute(
                new GetSalesReportUseCase.Query(fromOrDefault, toOrDefault, page, size, paymentStatus, provider)
        ));
    }
}
