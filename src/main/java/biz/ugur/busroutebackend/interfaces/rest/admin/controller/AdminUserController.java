package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminDeleteException;
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

    @GetMapping
    public Mono<ResponseEntity<AdminListResponse>> getAllAdmins() {
        return getAllAdminsUseCase.execute(Mono.empty())
                .map(AdminListResponse::fromResult)
                .doOnNext(response -> log.debug("Retrieved {} admins", response.getTotalCount()))
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<AdminResponse>> createAdmin(@Valid @RequestBody Mono<AdminCreateRequest> request) {
        return request.flatMap(req -> {
                    String username = req.username();
                    return Mono.just(req.toCommand())
                            .as(createAdminUseCase::execute)
                            .map(this::toAdminResponseEntity)
                            .doOnSuccess(response -> {
                                if (response.getStatusCode().is2xxSuccessful()) {
                                    log.info("Admin created successfully: {}", username);
                                }
                            })
                            .doOnError(error -> log.error("Failed to create admin: {}", username, error));
                })
                .onErrorReturn(IllegalArgumentException.class, ResponseEntity.badRequest().build());
    }


    @PutMapping("/{adminId}")
    public Mono<ResponseEntity<AdminResponse>> updateAdmin(@PathVariable String adminId,
                                                           @Valid @RequestBody Mono<AdminUpdateRequest> requestMono) {
        return requestMono.flatMap(req -> {
                    String username = req.username();
                    System.out.println(req);
                    System.out.println(adminId);
                    return Mono.just(new UpdateAdminUseCase.Request(adminId, req.toCommand()))
                            .as(updateAdminUseCase::execute)
                            .map(this::toAdminResponseEntity)
                            .doOnSuccess(resp -> {
                                if (resp.getStatusCode().is2xxSuccessful()) {
                                    log.info("Admin '{}' (ID: {}) updated successfully", username, adminId);
                                }
                            })
                            .doOnError(err -> log.error("Failed to update admin '{}' (ID: {}): {}",
                                    username, adminId, err.getMessage(), err));
                })
                .onErrorResume(AdminNotFoundException.class,e -> {
                            log.warn("Admin with ID {} not found for update", adminId);
                            return Mono.just(ResponseEntity.notFound().build());
                });
    }


    @DeleteMapping("/{adminId}")
    public Mono<ResponseEntity<Void>> deleteAdmin(@PathVariable String adminId) {
        log.info("Deleting admin: {}", adminId);
        return deleteAdminUseCase.execute(Mono.just(adminId))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorReturn(AdminDeleteException.class,
                        ResponseEntity.notFound().build())
                .onErrorReturn(AdminDeleteException.class,
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