package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.AdminCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.AdminListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.AdminResponse;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminUpdateRequest;
import biz.ugur.busroutebackend.admin.application.usecase.CreateAdminUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.DeleteAdminUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.GetAllAdminsUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.UpdateAdminUseCase;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/users")
@Slf4j
public class AdminUserController {

    private final CreateAdminUseCase createAdminUseCase;
    private final GetAllAdminsUseCase getAllAdminsUseCase;
    private final UpdateAdminUseCase updateAdminUseCase;
    private final DeleteAdminUseCase deleteAdminUseCase;

    public AdminUserController(CreateAdminUseCase createAdminUseCase,
                               GetAllAdminsUseCase getAllAdminsUseCase,
                               UpdateAdminUseCase updateAdminUseCase,
                               DeleteAdminUseCase deleteAdminUseCase) {
        this.createAdminUseCase = createAdminUseCase;
        this.getAllAdminsUseCase = getAllAdminsUseCase;
        this.updateAdminUseCase = updateAdminUseCase;
        this.deleteAdminUseCase = deleteAdminUseCase;
    }

    @PostMapping
    public Mono<ResponseEntity<AdminResponse>> createAdmin(@Valid @RequestBody AdminCreateRequest request) {
        log.info("Creating admin: {}", request.username());

        return createAdminUseCase.execute(request.toCommand())
                .map(this::toAdminResponseEntity)
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.badRequest().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Admin created successfully: {}", request.username());
                    }
                })
                .doOnError(error -> log.error("Failed to create admin: {}", request.username(), error));
    }

    @GetMapping
    public Mono<ResponseEntity<AdminListResponse>> getAllAdmins() {
        return getAllAdminsUseCase.execute(Mono.empty())
                .map(AdminListResponse::fromResult)
                .doOnNext(response -> log.debug("Retrieved {} admins", response.getTotalCount()))
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{adminId}")
    public Mono<ResponseEntity<AdminResponse>> updateAdmin(
            @PathVariable String adminId,
            @Valid @RequestBody  Mono<AdminUpdateRequest> requestMono) {
        log.info("Updating admin: {}", adminId);
        return requestMono
                .map(req -> new UpdateAdminUseCase.Request(adminId, req.toCommand()))
                .as(updateAdminUseCase::execute)
                .map(result -> ResponseEntity.ok(AdminResponse.fromResult(result)))
                .onErrorResume(AdminNotFoundException.class, e -> Mono.just(ResponseEntity.notFound().build()))
                .doOnSuccess(resp -> log.info("Admin update response: {}", resp.getStatusCode()))
                .doOnError(err -> log.error("Error updating admin {}", adminId, err));
    }

    @DeleteMapping("/{adminId}")
    public Mono<ResponseEntity<Void>> deleteAdmin(@PathVariable String adminId) {
        log.info("Deleting admin: {}", adminId);
        return deleteAdminUseCase.execute(adminId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.notFound().build())
                .onErrorReturn(IllegalStateException.class,
                        ResponseEntity.badRequest().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Admin deleted successfully: {}", adminId);
                    }
                })
                .doOnError(error -> log.error("Failed to delete admin: {}", adminId, error));
    }

    private ResponseEntity<AdminResponse> toAdminResponseEntity(AdminResult result) {
        return ResponseEntity.ok(AdminResponse.fromResult(result));
    }
}