package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.business.application.dto.BusinessList;
import biz.ugur.busroutebackend.business.application.dto.BusinessPaginationQuery;
import biz.ugur.busroutebackend.business.application.dto.BusinessResponse;
import biz.ugur.busroutebackend.business.application.dto.CreateBusinessCommand;
import biz.ugur.busroutebackend.business.application.dto.RejectBusinessCommand;
import biz.ugur.busroutebackend.business.application.dto.SuspendBusinessCommand;
import biz.ugur.busroutebackend.business.application.dto.UpdateBusinessCommand;
import biz.ugur.busroutebackend.business.application.usecase.admin.ApproveBusinessUseCase;
import biz.ugur.busroutebackend.business.application.usecase.admin.CreateBusinessUseCase;
import biz.ugur.busroutebackend.business.application.usecase.admin.DeleteBusinessUseCase;
import biz.ugur.busroutebackend.business.application.usecase.admin.GetBusinessByIdUseCase;
import biz.ugur.busroutebackend.business.application.usecase.admin.GetBusinessesPaginatedUseCase;
import biz.ugur.busroutebackend.business.application.usecase.admin.ReactivateBusinessUseCase;
import biz.ugur.busroutebackend.business.application.usecase.admin.RejectBusinessUseCase;
import biz.ugur.busroutebackend.business.application.usecase.admin.SuspendBusinessUseCase;
import biz.ugur.busroutebackend.business.application.usecase.admin.UpdateBusinessUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_BUSINESSES;

@RestController
@RequestMapping(V1_ADMIN_BUSINESSES)
public class AdminBusinessController extends BasePaginatedController {

    private final CreateBusinessUseCase createBusinessUseCase;
    private final UpdateBusinessUseCase updateBusinessUseCase;
    private final ApproveBusinessUseCase approveBusinessUseCase;
    private final RejectBusinessUseCase rejectBusinessUseCase;
    private final SuspendBusinessUseCase suspendBusinessUseCase;
    private final ReactivateBusinessUseCase reactivateBusinessUseCase;
    private final GetBusinessByIdUseCase getBusinessByIdUseCase;
    private final GetBusinessesPaginatedUseCase getBusinessesPaginatedUseCase;
    private final DeleteBusinessUseCase deleteBusinessUseCase;

    public AdminBusinessController(CreateBusinessUseCase createBusinessUseCase,
                                    UpdateBusinessUseCase updateBusinessUseCase,
                                    ApproveBusinessUseCase approveBusinessUseCase,
                                    RejectBusinessUseCase rejectBusinessUseCase,
                                    SuspendBusinessUseCase suspendBusinessUseCase,
                                    ReactivateBusinessUseCase reactivateBusinessUseCase,
                                    GetBusinessByIdUseCase getBusinessByIdUseCase,
                                    GetBusinessesPaginatedUseCase getBusinessesPaginatedUseCase,
                                    DeleteBusinessUseCase deleteBusinessUseCase,
                                    MessageSource messageSource) {
        super(messageSource);
        this.createBusinessUseCase = createBusinessUseCase;
        this.updateBusinessUseCase = updateBusinessUseCase;
        this.approveBusinessUseCase = approveBusinessUseCase;
        this.rejectBusinessUseCase = rejectBusinessUseCase;
        this.suspendBusinessUseCase = suspendBusinessUseCase;
        this.reactivateBusinessUseCase = reactivateBusinessUseCase;
        this.getBusinessByIdUseCase = getBusinessByIdUseCase;
        this.getBusinessesPaginatedUseCase = getBusinessesPaginatedUseCase;
        this.deleteBusinessUseCase = deleteBusinessUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminBusinessController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<BusinessList>>> list(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "created_at") String sort,
            @RequestParam(required = false, defaultValue = "desc") String order,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        validatePagination(page, size);
        BusinessPaginationQuery q = BusinessPaginationQuery.create(
                page, size, camelToSnake(sort), order, status, type);
        return okPaginated(getBusinessesPaginatedUseCase.execute(q));
    }

    @GetMapping("/{businessId}")
    public Mono<ResponseEntity<ApiResponse<BusinessResponse>>> getById(@PathVariable String businessId) {
        return ok(getBusinessByIdUseCase.execute(businessId));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<BusinessResponse>>> create(
            @Valid @RequestBody CreateBusinessCommand request) {
        return created(createBusinessUseCase.execute(Mono.just(request)));
    }

    @PutMapping("/{businessId}")
    public Mono<ResponseEntity<ApiResponse<BusinessResponse>>> update(
            @PathVariable String businessId,
            @Valid @RequestBody UpdateBusinessCommand request) {
        return ok(updateBusinessUseCase.execute(
                new UpdateBusinessUseCase.Request(businessId, request)));
    }

    @PostMapping("/{businessId}/approve")
    public Mono<ResponseEntity<ApiResponse<BusinessResponse>>> approve(
            @PathVariable String businessId,
            @AuthenticationPrincipal UserDetails admin) {
        String adminId = admin != null ? admin.getUsername() : "system";
        return ok(approveBusinessUseCase.execute(
                new ApproveBusinessUseCase.Request(businessId, adminId)));
    }

    @PostMapping("/{businessId}/reject")
    public Mono<ResponseEntity<ApiResponse<BusinessResponse>>> reject(
            @PathVariable String businessId,
            @Valid @RequestBody RejectBusinessCommand request) {
        return ok(rejectBusinessUseCase.execute(
                new RejectBusinessUseCase.Request(businessId, request)));
    }

    @PostMapping("/{businessId}/suspend")
    public Mono<ResponseEntity<ApiResponse<BusinessResponse>>> suspend(
            @PathVariable String businessId,
            @Valid @RequestBody SuspendBusinessCommand request) {
        return ok(suspendBusinessUseCase.execute(
                new SuspendBusinessUseCase.Request(businessId, request)));
    }

    @PostMapping("/{businessId}/reactivate")
    public Mono<ResponseEntity<ApiResponse<BusinessResponse>>> reactivate(@PathVariable String businessId) {
        return ok(reactivateBusinessUseCase.execute(businessId));
    }

    @DeleteMapping("/{businessId}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String businessId) {
        return deleteBusinessUseCase.execute(businessId).then(noContent());
    }
}
