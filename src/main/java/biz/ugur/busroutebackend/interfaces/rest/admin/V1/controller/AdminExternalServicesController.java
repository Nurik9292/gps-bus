package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.admin.infrastructure.security.AdminPrincipal;
import biz.ugur.busroutebackend.integration.application.dto.CreateExternalServiceRequest;
import biz.ugur.busroutebackend.integration.application.dto.ExternalServiceDTO;
import biz.ugur.busroutebackend.integration.application.dto.ExternalServiceListDTO;
import biz.ugur.busroutebackend.integration.application.dto.UpdateExternalServiceRequest;
import biz.ugur.busroutebackend.integration.application.usecase.*;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_EXTERNAL_SERVICES;


@RestController
@RequestMapping(V1_ADMIN_EXTERNAL_SERVICES)
public class AdminExternalServicesController extends BaseController {

    private final CreateExternalServiceUseCase createExternalServiceUseCase;
    private final GetAllExternalServicesUseCase getAllExternalServicesUseCase;
    private final GetExternalServiceByIdUseCase getExternalServiceByIdUseCase;
    private final UpdateExternalServiceUseCase updateExternalServiceUseCase;
    private final BlockExternalServiceUseCase blockExternalServiceUseCase;
    private final UnblockExternalServiceUseCase unblockExternalServiceUseCase;
    private final DeleteExternalServiceUseCase deleteExternalServiceUseCase;

    public AdminExternalServicesController(CreateExternalServiceUseCase createExternalServiceUseCase,
                                           GetAllExternalServicesUseCase getAllExternalServicesUseCase,
                                           GetExternalServiceByIdUseCase getExternalServiceByIdUseCase,
                                           UpdateExternalServiceUseCase updateExternalServiceUseCase,
                                           BlockExternalServiceUseCase blockExternalServiceUseCase,
                                           UnblockExternalServiceUseCase unblockExternalServiceUseCase,
                                           DeleteExternalServiceUseCase deleteExternalServiceUseCase,
                                           MessageSource messageSource) {
        super(messageSource);
        this.createExternalServiceUseCase = createExternalServiceUseCase;
        this.getAllExternalServicesUseCase = getAllExternalServicesUseCase;
        this.getExternalServiceByIdUseCase = getExternalServiceByIdUseCase;
        this.updateExternalServiceUseCase = updateExternalServiceUseCase;
        this.blockExternalServiceUseCase = blockExternalServiceUseCase;
        this.unblockExternalServiceUseCase = unblockExternalServiceUseCase;
        this.deleteExternalServiceUseCase = deleteExternalServiceUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminExternalServicesController.class.getSimpleName();
    }

   
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<ExternalServiceListDTO>>> getAllExternalServices(
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        if (activeOnly) {
            return ok(getAllExternalServicesUseCase.executeActiveOnly());
        }
        return ok(getAllExternalServicesUseCase.execute());
    }

  
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<ExternalServiceDTO>>> getExternalServiceById(
            @PathVariable String id) {

        return ok(getExternalServiceByIdUseCase.execute(id));
    }

  
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<ExternalServiceDTO>>> createExternalService(
            @Valid @RequestBody CreateExternalServiceRequest request) {

        return getCurrentAdminId()
                .flatMap(adminId -> createExternalServiceUseCase.execute(request, adminId))
                .flatMap(dto -> created(Mono.just(dto)));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<ExternalServiceDTO>>> updateExternalService(
            @PathVariable String id,
            @Valid @RequestBody UpdateExternalServiceRequest request) {

        return ok(updateExternalServiceUseCase.execute(id, request));
    }


    @PostMapping("/{id}/block")
    public Mono<ResponseEntity<ApiResponse<ExternalServiceDTO>>> blockExternalService(
            @PathVariable String id,
            @RequestParam(required = false) String reason) {

        return getCurrentAdminId()
                .flatMap(adminId -> blockExternalServiceUseCase.execute(id, adminId, reason))
                .flatMap(dto -> ok(Mono.just(dto)));
    }

  
    @PostMapping("/{id}/unblock")
    public Mono<ResponseEntity<ApiResponse<ExternalServiceDTO>>> unblockExternalService(
            @PathVariable String id) {

        return getCurrentAdminId()
                .flatMap(adminId -> unblockExternalServiceUseCase.execute(id, adminId))
                .flatMap(dto -> ok(Mono.just(dto)));
    }

  
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteExternalService(
            @PathVariable String id) {

        return getCurrentAdminId()
                .flatMap(adminId -> deleteExternalServiceUseCase.execute(id, adminId))
                .then(noContent());
    }

   
    private Mono<AdminId> getCurrentAdminId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (AdminPrincipal) auth.getPrincipal())
                .map(AdminPrincipal::getId);
    }
}
