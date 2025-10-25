package biz.ugur.busroutebackend.banner.domain.repository;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface AdminBannerRepository extends BaseRepository<Banner, BannerId> {

    Flux<Banner> findActiveBanners();

    Mono<Long> countActiveBanners();

    Flux<Banner> findByTypeAndActive(BannerType type);

    Mono<Long> countByType(BannerType type);

    /**
     * Поиск баннеров по спецификации без пагинации.
     *
     * @param specification спецификация для фильтрации
     * @return поток баннеров, удовлетворяющих спецификации
     */
    Flux<Banner> findBySpecification(Specification<Banner> specification);

    /**
     * Поиск баннеров по спецификации с пагинацией.
     *
     * @param specification спецификация для фильтрации
     * @param pageable      параметры пагинации
     * @return поток баннеров, удовлетворяющих спецификации
     */
    Flux<Banner> findBySpecification(Specification<Banner> specification, Pageable pageable);

    /**
     * Подсчет количества баннеров, удовлетворяющих спецификации.
     *
     * @param specification спецификация для фильтрации
     * @return количество баннеров
     */
    Mono<Long> countBySpecification(Specification<Banner> specification);
}
