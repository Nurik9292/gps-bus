package biz.ugur.busroutebackend.admin.domain.repository;

import biz.ugur.busroutebackend.admin.domain.model.Banner;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface BannerRepository extends BaseRepository<Banner, BannerId> {

    Flux<Banner> findActiveBanners();

    Mono<Long> countActiveBanners();

    Flux<Banner> findByTypeAndActive(String type);

    Mono<Long> countByType(String type);
}
