package biz.ugur.busroutebackend.interfaces.rest.client.V1.controller;

import biz.ugur.busroutebackend.client.infrastructure.security.ClientPrincipal;
import biz.ugur.busroutebackend.complaint.application.dto.CreateComplaintInput;
import biz.ugur.busroutebackend.complaint.application.dto.GetClientComplaintsInput;
import biz.ugur.busroutebackend.complaint.application.usecase.CreateComplaintUseCase;
import biz.ugur.busroutebackend.complaint.application.usecase.GetClientComplaintsUseCase;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintType;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.ComplaintListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.ComplaintResponse;
import biz.ugur.busroutebackend.interfaces.rest.client.V1.request.ComplaintCreateRequest;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_CLIENT_COMPLAINTS;

@RestController
@RequestMapping(V1_CLIENT_COMPLAINTS)
public class ClientComplaintController extends BaseController {

    private final CreateComplaintUseCase createComplaintUseCase;
    private final GetClientComplaintsUseCase getClientComplaintsUseCase;

    public ClientComplaintController(CreateComplaintUseCase createComplaintUseCase,
                                      GetClientComplaintsUseCase getClientComplaintsUseCase,
                                      MessageSource messageSource) {
        super(messageSource);
        this.createComplaintUseCase = createComplaintUseCase;
        this.getClientComplaintsUseCase = getClientComplaintsUseCase;
    }

    @Override
    protected String getControllerName() {
        return ClientComplaintController.class.getSimpleName();
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<ComplaintResponse>>> createComplaint(
            @Valid @RequestBody ComplaintCreateRequest request) {
        return created(getCurrentClientId().flatMap(clientId -> {
            CreateComplaintInput input = new CreateComplaintInput(
                    ComplaintType.valueOf(request.getType().toUpperCase()),
                    request.getTitle(),
                    request.getDescription(),
                    clientId
            );
            return Mono.just(input)
                    .as(createComplaintUseCase::execute)
                    .map(ComplaintResponse::fromResult);
        }));
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<ComplaintListResponse>>> getMyComplaints(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "created_at") String sort,
            @RequestParam(defaultValue = "desc") String order) {
        return ok(getCurrentClientId().flatMap(clientId -> {
            GetClientComplaintsInput input = GetClientComplaintsInput.fromParams(
                    clientId, page, size, camelToSnake(sort), order);
            return Mono.just(input)
                    .as(getClientComplaintsUseCase::execute)
                    .map(ComplaintListResponse::fromResult);
        }));
    }

    private Mono<String> getCurrentClientId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (ClientPrincipal) auth.getPrincipal())
                .map(ClientPrincipal::getClientId);
    }
}
