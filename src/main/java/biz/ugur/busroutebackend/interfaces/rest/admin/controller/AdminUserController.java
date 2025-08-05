package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminProfileResponse;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.application.usecase.*;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.AdminCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.AdminUpdateProfileRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.AvatarUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.AdminListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.AdminResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.AdminUpdateRequest;
import biz.ugur.busroutebackend.shared.infrastructure.security.AdminPrincipal;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.Part;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
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
    private final UpdateCurrentAdminProfileUseCase updateCurrentAdminProfileUseCase;
    private final UpdateCurrentAdminAvatarUseCase updateCurrentAdminAvatarUseCase;
    private final RemoveCurrentAdminAvatarUseCase removeCurrentAdminAvatarUseCase;


    public AdminUserController(CreateAdminUseCase createAdminUseCase,
                               GetAllAdminsUseCase getAllAdminsUseCase,
                               UpdateAdminUseCase updateAdminUseCase,
                               DeleteAdminUseCase deleteAdminUseCase,
                               UpdateAdminStatusUseCase updateAdminStatusUseCase,
                               UpdateCurrentAdminProfileUseCase updateCurrentAdminProfileUseCase,
                               UpdateCurrentAdminAvatarUseCase updateCurrentAdminAvatarUseCase,
                               RemoveCurrentAdminAvatarUseCase removeCurrentAdminAvatarUseCase) {
        this.createAdminUseCase = createAdminUseCase;
        this.getAllAdminsUseCase = getAllAdminsUseCase;
        this.updateAdminUseCase = updateAdminUseCase;
        this.deleteAdminUseCase = deleteAdminUseCase;
        this.updateAdminStatusUseCase = updateAdminStatusUseCase;
        this.updateCurrentAdminProfileUseCase = updateCurrentAdminProfileUseCase;
        this.updateCurrentAdminAvatarUseCase = updateCurrentAdminAvatarUseCase;
        this.removeCurrentAdminAvatarUseCase = removeCurrentAdminAvatarUseCase;
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



    @PatchMapping("/profile")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdminProfileResponse> updateProfile(@Valid @RequestBody AdminUpdateProfileRequest request) {

        return getCurrentPrincipal().flatMap(principal -> {
            log.debug("Обновление профиля для админа: {}", principal.username());
            log.debug("Обновление профиля для админа2: {}", request.getUsername());
            log.debug("Обновление профиля для админа3: {}", request.getFullName());

            UpdateCurrentAdminProfileUseCase.Request req = new UpdateCurrentAdminProfileUseCase.Request(
                    principal.id(),
                    request.getUsername(),
                    request.getFullName(),
                    request.getAvatar()
            );

            return updateCurrentAdminProfileUseCase.execute(Mono.just(req))
                    .map(AdminProfileResponse::fromDomain)
                    .doOnSuccess(response -> log.info("✅ Профиль обновлен для: {}", principal.username()))
                    .doOnError(error -> log.error("❌ Ошибка обновления профиля: {}", error.getMessage()));
        });

    }


    @PatchMapping("/profile/avatar")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdminProfileResponse> updateAvatar(@Valid @RequestBody AvatarUpdateRequest request) {
        System.out.println("test " + request.avatar());
        return getCurrentPrincipal().flatMap(principal -> {
            log.debug("Обновление аватара для админа: {}", principal.username());

            UpdateCurrentAdminAvatarUseCase.Request req = new UpdateCurrentAdminAvatarUseCase.Request(
                    principal.id(),
                    request.avatar()
            );

            return updateCurrentAdminAvatarUseCase.execute(req)
                    .map(AdminProfileResponse::fromDomain)
                    .doOnSuccess(response -> log.info("✅ Аватар обновлен для: {}", principal.username()))
                    .doOnError(error -> log.error("❌ Ошибка обновления аватара: {}", error.getMessage()));
        });
    }



    @DeleteMapping("/profile/avatar")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdminProfileResponse> removeAvatar() {
        return getCurrentPrincipal().flatMap(principal -> {
            log.debug("Удаление аватара для админа: {}", principal.username());

            return removeCurrentAdminAvatarUseCase.execute(Mono.just(principal.id()))
                    .map(AdminProfileResponse::fromDomain)
                    .doOnSuccess(response -> log.info("✅ Аватар удален для: {}", principal.username()))
                    .doOnError(error -> log.error("❌ Ошибка удаления аватара: {}", error.getMessage()));
        });


    }

    private Mono<AdminPrincipal> getCurrentPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (AdminPrincipal) auth.getPrincipal());
    }

    private ResponseEntity<AdminResponse> toAdminResponseEntity(AdminResult result) {
        return ResponseEntity.ok(AdminResponse.fromResult(result));
    }
}