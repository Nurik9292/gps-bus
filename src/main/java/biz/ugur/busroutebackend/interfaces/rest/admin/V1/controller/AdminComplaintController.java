package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.complaint.application.dto.GetAllComplaintsInput;
import biz.ugur.busroutebackend.complaint.application.dto.UpdateComplaintStatusInput;
import biz.ugur.busroutebackend.complaint.application.usecase.CloseComplaintUseCase;
import biz.ugur.busroutebackend.complaint.application.usecase.GetAllComplaintsUseCase;
import biz.ugur.busroutebackend.complaint.application.usecase.MarkComplaintInProgressUseCase;
import biz.ugur.busroutebackend.complaint.application.usecase.ResolveComplaintUseCase;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.ComplaintStatusUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.ComplaintListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.ComplaintResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_COMPLAINTS;

@RestController
@RequestMapping(V1_ADMIN_COMPLAINTS)
public class AdminComplaintController extends BasePaginatedController {

    private final GetAllComplaintsUseCase getAllComplaintsUseCase;
    private final MarkComplaintInProgressUseCase markComplaintInProgressUseCase;
    private final ResolveComplaintUseCase resolveComplaintUseCase;
    private final CloseComplaintUseCase closeComplaintUseCase;

    public AdminComplaintController(GetAllComplaintsUseCase getAllComplaintsUseCase,
                                     MarkComplaintInProgressUseCase markComplaintInProgressUseCase,
                                     ResolveComplaintUseCase resolveComplaintUseCase,
                                     CloseComplaintUseCase closeComplaintUseCase,
                                     MessageSource messageSource) {
        super(messageSource);
        this.getAllComplaintsUseCase = getAllComplaintsUseCase;
        this.markComplaintInProgressUseCase = markComplaintInProgressUseCase;
        this.resolveComplaintUseCase = resolveComplaintUseCase;
        this.closeComplaintUseCase = closeComplaintUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminComplaintController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<ComplaintListResponse>>> getAllComplaints(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "created_at") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {

        validatePagination(page, size);

        return ok(Mono.just(GetAllComplaintsInput.fromParams(page, size, camelToSnake(sort), order, status, type))
                .as(getAllComplaintsUseCase::execute)
                .map(ComplaintListResponse::fromResult));
    }

    @PatchMapping("/{id}/in-progress")
    public Mono<ResponseEntity<ApiResponse<ComplaintResponse>>> markInProgress(@PathVariable String id) {
        return ok(Mono.just(new UpdateComplaintStatusInput(id, null))
                .as(markComplaintInProgressUseCase::execute)
                .map(ComplaintResponse::fromResult));
    }

    @PatchMapping("/{id}/resolve")
    public Mono<ResponseEntity<ApiResponse<ComplaintResponse>>> resolve(
            @PathVariable String id,
            @RequestBody(required = false) ComplaintStatusUpdateRequest request) {
        String comment = request != null ? request.getComment() : null;
        return ok(Mono.just(new UpdateComplaintStatusInput(id, comment))
                .as(resolveComplaintUseCase::execute)
                .map(ComplaintResponse::fromResult));
    }

    @PatchMapping("/{id}/close")
    public Mono<ResponseEntity<ApiResponse<ComplaintResponse>>> close(
            @PathVariable String id,
            @RequestBody(required = false) ComplaintStatusUpdateRequest request) {
        String comment = request != null ? request.getComment() : null;
        return ok(Mono.just(new UpdateComplaintStatusInput(id, comment))
                .as(closeComplaintUseCase::execute)
                .map(ComplaintResponse::fromResult));
    }
}
