package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminPaginationQuery;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.application.usecase.admin.*;
import biz.ugur.busroutebackend.admin.infrastructure.security.AdminPrincipal;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.admin.AdminCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.admin.AdminUpdateProfileRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.admin.AdminUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.admin.AvatarUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.admin.AdminListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.admin.AdminProfileResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.admin.AdminResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_USERS;

@RestController
@RequestMapping(V1_ADMIN_USERS)
public class AdminUserController extends BasePaginatedController {

    private final CreateAdminUseCase createAdminUseCase;
    private final GetAllAdminsUseCase getAllAdminsUseCase;
    private final GetAdminByIdUseCase getAdminByIdUseCase;
    private final UpdateAdminUseCase updateAdminUseCase;
    private final DeleteAdminUseCase deleteAdminUseCase;
    private final UpdateAdminStatusUseCase updateAdminStatusUseCase;
    private final UpdateCurrentAdminProfileUseCase updateCurrentAdminProfileUseCase;
    private final UpdateCurrentAdminAvatarUseCase updateCurrentAdminAvatarUseCase;
    private final RemoveCurrentAdminAvatarUseCase removeCurrentAdminAvatarUseCase;


    public AdminUserController(CreateAdminUseCase createAdminUseCase,
                               GetAllAdminsUseCase getAllAdminsUseCase,
                               GetAdminByIdUseCase getAdminByIdUseCase,
                               UpdateAdminUseCase updateAdminUseCase,
                               DeleteAdminUseCase deleteAdminUseCase,
                               UpdateAdminStatusUseCase updateAdminStatusUseCase,
                               UpdateCurrentAdminProfileUseCase updateCurrentAdminProfileUseCase,
                               UpdateCurrentAdminAvatarUseCase updateCurrentAdminAvatarUseCase,
                               RemoveCurrentAdminAvatarUseCase removeCurrentAdminAvatarUseCase,
                               MessageSource messageSource) {

        super(messageSource);
        this.createAdminUseCase = createAdminUseCase;
        this.getAllAdminsUseCase = getAllAdminsUseCase;
        this.getAdminByIdUseCase = getAdminByIdUseCase;
        this.updateAdminUseCase = updateAdminUseCase;
        this.deleteAdminUseCase = deleteAdminUseCase;
        this.updateAdminStatusUseCase = updateAdminStatusUseCase;
        this.updateCurrentAdminProfileUseCase = updateCurrentAdminProfileUseCase;
        this.updateCurrentAdminAvatarUseCase = updateCurrentAdminAvatarUseCase;
        this.removeCurrentAdminAvatarUseCase = removeCurrentAdminAvatarUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminUserController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<AdminListResponse>>> getAllAdmins(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "do") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role
    ) {
        validatePagination(page, size);

        return ok(Mono.just(AdminPaginationQuery.fromParams(page, size, sort, order, active, search, role))
                .as(getAllAdminsUseCase::execute)
                .map(AdminListResponse::fromResult));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<AdminResponse>>> createAdmin(@Valid @RequestBody AdminCreateRequest request) {

        return created(Mono.just(request.toCommand())
                .as(createAdminUseCase::execute)
                .map(this::toBasic));

    }

    @GetMapping("/{adminId}")
    public Mono<ResponseEntity<ApiResponse<AdminResponse>>> getAdminById(@PathVariable String adminId) {

        return ok(Mono.just(new GetAdminByIdUseCase.Request(adminId))
                .as(getAdminByIdUseCase::execute)
                .map(this::toBasic));

    }

    @PutMapping("/{adminId}")
    public Mono<ResponseEntity<ApiResponse<AdminResponse>>> updateAdmin(@PathVariable String adminId,
                                                           @Valid @RequestBody AdminUpdateRequest request) {

        return ok(Mono.just(new UpdateAdminUseCase.Request(adminId, request.toCommand()))
                .as(updateAdminUseCase::execute)
                .map(this::toBasic));

    }

    @DeleteMapping("/{adminId}")
    public Mono<ResponseEntity<Void>> deleteAdmin(@PathVariable String adminId) {
        return Mono.just(new DeleteAdminUseCase.Request(adminId))
                .as(deleteAdminUseCase::execute)
                .then(noContent());
    }


    @PostMapping("/{id}/deactivate")
    public Mono<ResponseEntity<ApiResponse<AdminResponse>>> deactivateAdmin(@PathVariable String id) {

        return ok(Mono.just(new UpdateAdminStatusUseCase.Request(id, false))
                .as(updateAdminStatusUseCase::execute)
                .map(this::toBasic));

    }

    @PostMapping("/{id}/activate")
    public Mono<ResponseEntity<ApiResponse<AdminResponse>>> activateAdmin(@PathVariable String id) {

        return ok(Mono.just(new UpdateAdminStatusUseCase.Request(id, true))
                .as(updateAdminStatusUseCase::execute)
                .map(this::toBasic));
    }



    @PatchMapping("/profile")
    public Mono<ResponseEntity<ApiResponse<AdminProfileResponse>>> updateProfile(@Valid @RequestBody AdminUpdateProfileRequest request) {
        return ok(getCurrentPrincipal().flatMap(principal -> {
            UpdateCurrentAdminProfileUseCase.Request req = new UpdateCurrentAdminProfileUseCase.Request(
                    principal.getId(),
                    request.getUsername(),
                    request.getFullName(),
                    request.getNewPassword()
            );

            return updateCurrentAdminProfileUseCase.execute(Mono.just(req))
                    .map(AdminProfileResponse::fromDomain);
        }));
    }


    @PatchMapping("/profile/avatar")
    public Mono<ResponseEntity<ApiResponse<AdminProfileResponse>>> updateAvatar(@Valid @RequestBody AvatarUpdateRequest request) {
        return ok(getCurrentPrincipal().flatMap(principal -> {
            UpdateCurrentAdminAvatarUseCase.Request req = new UpdateCurrentAdminAvatarUseCase.Request(
                    principal.getId(),
                    request.avatar()
            );

            return Mono.just(req)
                    .as(updateCurrentAdminAvatarUseCase::execute)
                    .map(AdminProfileResponse::fromDomain);
        }));
    }



    @DeleteMapping("/profile/avatar")
    public Mono<ResponseEntity<ApiResponse<AdminProfileResponse>>> removeAvatar() {
        return ok(getCurrentPrincipal().flatMap(principal -> {
            return removeCurrentAdminAvatarUseCase.execute(Mono.just(principal.getId()))
                    .map(AdminProfileResponse::fromDomain);
        }));
    }

    private Mono<AdminPrincipal> getCurrentPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (AdminPrincipal) auth.getPrincipal());
    }

    private AdminResponse toBasic(AdminResult result) {
        return AdminResponse.fromResult(result);
    }

}