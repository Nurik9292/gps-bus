package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.admin.application.dto.banner.BannerCreateRequest;
import biz.ugur.busroutebackend.admin.application.dto.banner.BannerListResponse;
import biz.ugur.busroutebackend.admin.application.dto.banner.BannerResponse;
import biz.ugur.busroutebackend.admin.application.usecase.CreateBannerUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.DeleteBannerUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.GetAllBannersUseCase;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/banners")
@Slf4j
@CrossOrigin(origins = "*")
public class AdminBannerController {

    private final CreateBannerUseCase createBannerUseCase;
    private final GetAllBannersUseCase getAllBannersUseCase;
    private final DeleteBannerUseCase deleteBannerUseCase;

    public AdminBannerController(CreateBannerUseCase createBannerUseCase,
                                 GetAllBannersUseCase getAllBannersUseCase,
                                 DeleteBannerUseCase deleteBannerUseCase) {
        this.createBannerUseCase = createBannerUseCase;
        this.getAllBannersUseCase = getAllBannersUseCase;
        this.deleteBannerUseCase = deleteBannerUseCase;
    }

    @PostMapping
    public Mono<ResponseEntity<BannerResponse>> createBanner(@Valid @RequestBody BannerCreateRequest request) {
        log.info("Creating banner: {}", request.getTitle());

        return createBannerUseCase.execute(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Banner created successfully: {}", request.getTitle());
                    }
                })
                .doOnError(error -> log.error("Failed to create banner: {}", request.getTitle(), error));
    }

    @GetMapping
    public Mono<ResponseEntity<BannerListResponse>> getAllBanners(
            @RequestParam(required = false) Boolean active) {

        log.debug("Fetching banners (active: {})", active);

        return getAllBannersUseCase.execute(active)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> {
                    if (response.getBody() != null) {
                        log.debug("Retrieved {} banners", response.getBody().getTotalCount());
                    }
                });
    }

    @DeleteMapping("/{bannerId}")
    public Mono<ResponseEntity<Void>> deleteBanner(@PathVariable String bannerId) {
        log.info("Deleting banner: {}", bannerId);

        return deleteBannerUseCase.execute(bannerId)
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
}
