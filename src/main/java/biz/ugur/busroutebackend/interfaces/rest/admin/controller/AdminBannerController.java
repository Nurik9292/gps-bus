package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.admin.application.dto.banner.*;
import biz.ugur.busroutebackend.admin.application.usecase.banner.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/banners")
@Slf4j
@CrossOrigin(origins = "*")
public class AdminBannerController {

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
                                 ToggleStatusBannerUseCase toggleStatusBannerUseCase) {
        this.createBannerUseCase = createBannerUseCase;
        this.getAllBannersUseCase = getAllBannersUseCase;
        this.getBannersWithPaginationUseCase = getBannersWithPaginationUseCase;
        this.updateBannerUseCase = updateBannerUseCase;
        this.deleteBannerUseCase = deleteBannerUseCase;
        this.toggleStatusBannerUseCase = toggleStatusBannerUseCase;
    }

    @GetMapping
    public Mono<ResponseEntity<BannerListResponse>> getAllBanners(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "display_order") String sort,
            @RequestParam(defaultValue = "asc") String order) {

        log.debug("Fetching banners (active: {}, page: {}, size: {}, sort: {}, order: {})",
                active, page, size, sort, order);

        if (page == 1 && size == 25 && "display_order".equals(sort) && "asc".equals(order)) {
            return Mono.just(active)
                    .as(getAllBannersUseCase::execute)
                    .map(ResponseEntity::ok);
        }

        BannerPaginationQuery query = new BannerPaginationQuery(page, size, sort, order, active);

        return Mono.just(query)
                .as(getBannersWithPaginationUseCase::execute)
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<BannerResponse>> createBanner(@Valid @RequestBody BannerCreateRequest request) {
        log.info("Creating banner: {}", request.getTitle());

        return Mono.just(request)
                .as(createBannerUseCase::execute)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Banner created successfully: {}", request.getTitle());
                    }
                })
                .doOnError(error -> log.error("Failed to create banner: {}", request.getTitle(), error));

    }

    @PutMapping("/{bannerId}")
    public Mono<ResponseEntity<BannerResponse>> updateBanner(@PathVariable String bannerId,
            @Valid @RequestBody BannerUpdateRequest request) {

        log.info("Updating banner: {}", bannerId);

        UpdateBannerUseCase.Request updateRequest = new UpdateBannerUseCase.Request(bannerId, request);

        return Mono.just(updateRequest)
                .as(updateBannerUseCase::execute)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Banner updated successfully: {}", bannerId);
                    }
                })
                .doOnError(error -> log.error("Failed to update banner: {}", bannerId, error));

    }


    @DeleteMapping("/{bannerId}")
    public Mono<ResponseEntity<Void>> deleteBanner(@PathVariable String bannerId) {
        log.info("Deleting banner: {}", bannerId);

        return Mono.just(bannerId)
                .as(deleteBannerUseCase::execute)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.notFound().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Banner deleted successfully: {}", bannerId);
                    }
                })
                .doOnError(error -> log.error("Failed to delete banner: {}", bannerId, error));
    }

    @GetMapping("/toggle-status/{id}")
    public Mono<ResponseEntity<BannerResponse>> toggleStatus(@PathVariable String id, @RequestParam Boolean active) {
        return Mono.just(new ToggleStatusBannerUseCase.Request(id, active))
                .as(toggleStatusBannerUseCase::execute)
                .map(this::toBannerResponseEntity);
    }

    private ResponseEntity<BannerResponse> toBannerResponseEntity(BannerResponse result) {
        return ResponseEntity.ok().body(result);
    }
}
