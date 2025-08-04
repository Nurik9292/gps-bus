package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.application.usecase.*;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminAlreadyExistsException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminDeleteException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.AdminCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.AdminListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.AdminResponse;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminUpdateRequest;
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
    private final UpdateAdminStatusUseCase updateAdminStatusUseCase;

    public AdminUserController(CreateAdminUseCase createAdminUseCase,
                               GetAllAdminsUseCase getAllAdminsUseCase,
                               UpdateAdminUseCase updateAdminUseCase,
                               DeleteAdminUseCase deleteAdminUseCase,
                               UpdateAdminStatusUseCase updateAdminStatusUseCase) {
        this.createAdminUseCase = createAdminUseCase;
        this.getAllAdminsUseCase = getAllAdminsUseCase;
        this.updateAdminUseCase = updateAdminUseCase;
        this.deleteAdminUseCase = deleteAdminUseCase;
        this.updateAdminStatusUseCase = updateAdminStatusUseCase;
    }

    @GetMapping
    public Mono<ResponseEntity<AdminListResponse>> getAllAdmins() {
        return getAllAdminsUseCase.execute(Mono.empty())
                .map(AdminListResponse::fromResult)
                .doOnNext(response -> log.debug("Retrieved {} admins", response.getTotalCount()))
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<AdminResponse>> createAdmin(@Valid @RequestBody AdminCreateRequest request) {
        String username = request.username();
        log.info("Creating admin: {}", username);

        return Mono.just(request.toCommand())
                .as(createAdminUseCase::execute)
                .map(this::toAdminResponseEntity)
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Admin created successfully: {}", username);
                    }
                })
                .doOnError(error -> log.error("Failed to create admin: {}", username, error));
    }

    @PutMapping("/{adminId}")
    public Mono<ResponseEntity<AdminResponse>> updateAdmin(@PathVariable String adminId,
                                                           @Valid @RequestBody AdminUpdateRequest request) {
        String username = request.username();
        log.info("Updating admin - ID: {} - Username: {}", adminId, username);

        return Mono.just(new UpdateAdminUseCase.Request(adminId, request.toCommand()))
                .as(updateAdminUseCase::execute)
                .map(this::toAdminResponseEntity)
                .doOnSuccess(resp -> {
                    if (resp.getStatusCode().is2xxSuccessful()) {
                        log.info("Admin '{}' (ID: {}) updated successfully", username, adminId);
                    }
                })
                .doOnError(error -> log.error("Failed to update admin '{}' (ID: {}): {}",
                        username, adminId, error.getMessage(), error));
    }

    @DeleteMapping("/{adminId}")
    public Mono<ResponseEntity<Void>> deleteAdmin(@PathVariable String adminId) {
        log.info("Deleting admin - ID: {}", adminId);

        return deleteAdminUseCase.execute(Mono.just(new DeleteAdminUseCase.Request(adminId)))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .doOnSuccess(resp -> log.info("Admin deleted successfully - ID: {}", adminId))
                .doOnError(error -> log.error("Failed to delete admin - ID: {}: {}",
                        adminId, error.getMessage(), error));
    }


    @PostMapping("/{id}/deactivate")
    public Mono<ResponseEntity<AdminResponse>> deactivateAdmin(@PathVariable String id) {
        log.info("Deactivating admin - ID: {}", id);

        return Mono.just(new UpdateAdminStatusUseCase.Request(id, false))
                .as(updateAdminStatusUseCase::execute)
                .map(this::toAdminResponseEntity)
                .doOnSuccess(resp -> log.info("Admin update status - ID: {}", id))
                .doOnError(error -> log.error("Failed update status admin - ID: {}: {}", id, error.getMessage(), error));
    }

    @PostMapping("/{id}/activate")
    public Mono<ResponseEntity<AdminResponse>> activateAdmin(@PathVariable String id) {
        log.info("Activate admin - ID: {}", id);

        return Mono.just(new UpdateAdminStatusUseCase.Request(id, true))
                .as(updateAdminStatusUseCase::execute)
                .map(this::toAdminResponseEntity)
                .doOnSuccess(resp -> log.info("Admin update status - ID: {}", id))
                .doOnError(error -> log.error("Failed update status admin - ID: {}: {}", id, error.getMessage(), error));
    }


    private ResponseEntity<AdminResponse> toAdminResponseEntity(AdminResult result) {
        return ResponseEntity.ok(AdminResponse.fromResult(result));
    }
}