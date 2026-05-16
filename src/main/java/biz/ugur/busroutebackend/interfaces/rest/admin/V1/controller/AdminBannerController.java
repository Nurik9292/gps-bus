package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.application.usecase.admin.*;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.banner.BannerCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.banner.BannerUpdateRequest;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_BANNERS;

@RestController
@Deprecated(since = "2026-05-15", forRemoval = true)
@RequestMapping(V1_ADMIN_BANNERS)
public class AdminBannerController extends BasePaginatedController {

    private final CreateBannerUseCase createBannerUseCase;
    private final GetBannersWithPaginationUseCase getBannersWithPaginationUseCase;
    private final GetBannerByIdUseCase getBannerByIdUseCase;
    private final UpdateBannerUseCase updateBannerUseCase;
    private final DeleteBannerUseCase deleteBannerUseCase;
    private final ToggleStatusBannerUseCase toggleStatusBannerUseCase;

    public AdminBannerController(CreateBannerUseCase createBannerUseCase,
                                 GetBannersWithPaginationUseCase getBannersWithPaginationUseCase,
                                 GetBannerByIdUseCase getBannerByIdUseCase,
                                 UpdateBannerUseCase updateBannerUseCase,
                                 DeleteBannerUseCase deleteBannerUseCase,
                                 ToggleStatusBannerUseCase toggleStatusBannerUseCase,
                                 MessageSource messageSource) {
        super(messageSource);
        this.createBannerUseCase = createBannerUseCase;
        this.getBannersWithPaginationUseCase = getBannersWithPaginationUseCase;
        this.getBannerByIdUseCase = getBannerByIdUseCase;
        this.updateBannerUseCase = updateBannerUseCase;
        this.deleteBannerUseCase = deleteBannerUseCase;
        this.toggleStatusBannerUseCase = toggleStatusBannerUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminBannerController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<BannerList>>> getAllBanners(
            @RequestParam(required = false, defaultValue = "false") Boolean active,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "display_order") String sort,
            @RequestParam(required = false, defaultValue = "asc") String order,
            @RequestParam(required = false) String query) {

        validatePagination(page, size);

        BannerPaginationQuery paginationQuery = BannerPaginationQuery.create(
            page,
            size,
            camelToSnake(sort),
            order,
            active,
            query
        );

        return okPaginated(Mono.just(paginationQuery)
                .as(getBannersWithPaginationUseCase::execute));
    }

    @GetMapping("/{bannerId}")
    public Mono<ResponseEntity<ApiResponse<BannerResponse>>> getBannerById(@PathVariable String bannerId) {
        return ok(Mono.just(bannerId)
                .as(getBannerByIdUseCase::execute));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<BannerResponse>>> createBanner(@Valid @RequestBody BannerCreateRequest request) {
        return created(Mono.just(request.toCommand())
                .as(createBannerUseCase::execute));
    }

    @PutMapping("/{bannerId}")
    public Mono<ResponseEntity<ApiResponse<BannerResponse>>> updateBanner(@PathVariable String bannerId,
            @Valid @RequestBody BannerUpdateRequest request) {
        return ok(Mono.just(request.toCommand(bannerId)).as(updateBannerUseCase::execute));
    }


    @DeleteMapping("/{bannerId}")
    public Mono<ResponseEntity<Void>> deleteBanner(@PathVariable String bannerId) {
        return Mono.just(bannerId)
                .as(deleteBannerUseCase::execute)
                .then(noContent());
    }

    @GetMapping("/toggle-status/{id}")
    public Mono<ResponseEntity<ApiResponse<BannerResponse>>> toggleStatus(@PathVariable String id, @RequestParam Boolean active) {
        return ok(Mono.just(new ToggleStatusBannerUseCase.Request(id, active))
                .as(toggleStatusBannerUseCase::execute));
    }


}
