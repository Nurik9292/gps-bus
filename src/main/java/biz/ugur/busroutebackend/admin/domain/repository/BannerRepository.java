package biz.ugur.busroutebackend.admin.domain.repository;

import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface BannerRepository {

    Mono<Banner> save(Banner banner);

    Mono<Banner> findById(BannerId bannerId);

    Mono<Boolean> existsById(BannerId bannerId);

    Flux<Banner> findActiveBanners();

    Flux<Banner> findAllBanners();

    Flux<Banner> findActiveBannersWithPagination(Pageable pageable);

    Flux<Banner> findAllBannersWithPagination(Pageable pageable);

    Mono<Void> deleteById(BannerId bannerId);

    Mono<Long> countActiveBanners();

    Mono<Long> countTotalBanners();

    Flux<Banner> findByTypeAndActive(String type);

    Mono<Long> countByType(String type);
}
