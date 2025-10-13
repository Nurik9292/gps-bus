package biz.ugur.busroutebackend.banner.domain.repository;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ClientBannerRepository  extends BaseRepository<Banner, BannerId> {

    Flux<Banner> findActiveBannersByTypeWithPagination(BannerType type, Pageable pageable);

}
