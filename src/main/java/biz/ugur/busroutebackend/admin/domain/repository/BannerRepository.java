package biz.ugur.busroutebackend.admin.domain.repository;

import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BannerRepository {

    Mono<Banner> save(Banner banner);

    Mono<Banner> findById(BannerId bannerId);

    Flux<Banner> findActiveBanners();

    Flux<Banner> findAllBanners();

    Mono<Void> deleteById(BannerId bannerId);

    Mono<Long> countActiveBanners();
}
