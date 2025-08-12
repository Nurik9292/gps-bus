package biz.ugur.busroutebackend.admin.application.usecase.banner;

import biz.ugur.busroutebackend.admin.domain.repository.BannerRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.infrastructure.storage.BannerStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteBannerUseCase implements UseCase<String, Mono<Void>> {

    private final BannerRepository bannerRepository;
    private final BannerStorageService bannerStorageService;


    public DeleteBannerUseCase(BannerRepository bannerRepository, BannerStorageService bannerStorageService) {
        this.bannerRepository = bannerRepository;
        this.bannerStorageService = bannerStorageService;
    }

    @Override
    public Mono<Void> execute(String bannerId) {
        log.info("Deleting banner: {}", bannerId);

        return bannerRepository.findById(BannerId.of(bannerId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Banner not found: " + bannerId)))
                .flatMap(banner ->
                        bannerStorageService.deleteBanner(banner.getImageUrl())
                                .then(bannerRepository.deleteById(BannerId.of(bannerId)))
                )
                .doOnSuccess(v -> log.info("Banner deleted successfully: {}", bannerId))
                .doOnError(error -> log.error("Failed to delete banner: {}", bannerId, error));
    }
}