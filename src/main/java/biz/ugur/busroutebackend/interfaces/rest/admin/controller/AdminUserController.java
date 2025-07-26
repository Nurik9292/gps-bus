package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminCreateRequest;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminListResponse;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResponse;
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
@RequestMapping("/api/admin/users")
@Slf4j
@CrossOrigin(origins = "*")
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
        log.info("Creating admin: {}", request.getUsername());

        return createAdminUseCase.execute(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.badRequest().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Admin created successfully: {}", request.getUsername());
                    }
                })
                .doOnError(error -> log.error("Failed to create admin: {}", request.getUsername(), error));
    }

    @GetMapping
    public Mono<ResponseEntity<AdminListResponse>> getAllAdmins() {
        log.debug("Fetching all admins");

        return getAllAdminsUseCase.execute(null)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> {
                    if (response.getBody() != null) {
                        log.debug("Retrieved {} admins", response.getBody().getTotalCount());
                    }
                });
    }

    @PutMapping("/{adminId}")
    public Mono<ResponseEntity<AdminResponse>> updateAdmin(
            @PathVariable String adminId,
            @Valid @RequestBody AdminUpdateRequest request) {

        log.info("Updating admin: {}", adminId);

        return updateAdminUseCase.execute(new UpdateAdminUseCase.Request(adminId, request))
                .map(ResponseEntity::ok)
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.notFound().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Admin updated successfully: {}", adminId);
                    }
                })
                .doOnError(error -> log.error("Failed to update admin: {}", adminId, error));
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
}