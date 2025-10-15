package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.banner.appication.dto.admin.*;
import biz.ugur.busroutebackend.banner.appication.usecase.admin.*;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN;

@RestController
@RequestMapping(V1_ADMIN + "/banners")
@CrossOrigin(origins = "*")
public class AdminBannerController extends BaseController {

    private final CreateBannerUseCase createBannerUseCase;
    private final GetAllBannersUseCase getAllBannersUseCase;
    private final GetBannersWithPaginationUseCase getBannersWithPaginationUseCase;
    private final UpdateBannerUseCase updateBannerUseCase;
    private final DeleteBannerUseCase deleteBannerUseCase;
    private final ToggleStatusBannerUseCase toggleStatusBannerUseCase;

    public AdminBannerController(CreateBannerUseCase createBannerUseCase,
                                 GetAllBannersUseCase getAllBannersUseCase,
                                 GetBannersWithPaginationUseCase getBannersWithPaginationUseCase,
                                 UpdateBannerUseCase updateBannerUseCase,
                                 DeleteBannerUseCase deleteBannerUseCase,
                                 ToggleStatusBannerUseCase toggleStatusBannerUseCase,
                                 MessageSource messageSource) {
        super(messageSource);
        this.createBannerUseCase = createBannerUseCase;
        this.getAllBannersUseCase = getAllBannersUseCase;
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
    public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getAllBanners(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "display_order") String sort,
            @RequestParam(defaultValue = "asc") String order) {

        if (page == 1 && size == 25 && "display_order".equals(sort) && "asc".equals(order)) {
            return ok(Mono.just(active)
                    .as(getAllBannersUseCase::execute));
        }

        BannerPaginationQuery query = BannerPaginationQuery.create(page, size, camelToSnake(sort), order, active);

        return ok(Mono.just(query)
                .as(getBannersWithPaginationUseCase::execute));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<BannerResponse>>> createBanner(@Valid @RequestBody BannerCreateRequest request) {
        return created(Mono.just(request)
                .as(createBannerUseCase::execute));
    }

    @PutMapping("/{bannerId}")
    public Mono<ResponseEntity<ApiResponse<BannerResponse>>> updateBanner(@PathVariable String bannerId,
            @Valid @RequestBody BannerUpdateRequest request) {

        UpdateBannerUseCase.Request updateRequest = new UpdateBannerUseCase.Request(bannerId, request);

        return ok(Mono.just(updateRequest)
                .as(updateBannerUseCase::execute));
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
