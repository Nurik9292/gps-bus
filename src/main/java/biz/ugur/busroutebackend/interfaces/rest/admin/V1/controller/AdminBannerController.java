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
@RequestMapping(V1_ADMIN_BANNERS)
@CrossOrigin(origins = "*")
public class AdminBannerController extends BasePaginatedController {

    private final CreateBannerUseCase createBannerUseCase;
    private final GetBannersWithPaginationUseCase getBannersWithPaginationUseCase;
    private final UpdateBannerUseCase updateBannerUseCase;
    private final DeleteBannerUseCase deleteBannerUseCase;
    private final ToggleStatusBannerUseCase toggleStatusBannerUseCase;

    public AdminBannerController(CreateBannerUseCase createBannerUseCase,
                                 GetBannersWithPaginationUseCase getBannersWithPaginationUseCase,
                                 UpdateBannerUseCase updateBannerUseCase,
                                 DeleteBannerUseCase deleteBannerUseCase,
                                 ToggleStatusBannerUseCase toggleStatusBannerUseCase,
                                 MessageSource messageSource) {
        super(messageSource);
        this.createBannerUseCase = createBannerUseCase;
        this.getBannersWithPaginationUseCase = getBannersWithPaginationUseCase;
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
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "display_order") String sort,
            @RequestParam(defaultValue = "asc") String order) {

        validatePagination(page, size);

        BannerPaginationQuery query = BannerPaginationQuery.create(
            page,
            size,
            camelToSnake(sort),
            order,
            active
        );

        return okPaginated(Mono.just(query)
                .as(getBannersWithPaginationUseCase::execute));
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
