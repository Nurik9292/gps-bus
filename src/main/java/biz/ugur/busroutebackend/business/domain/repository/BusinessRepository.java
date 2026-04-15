package biz.ugur.busroutebackend.business.domain.repository;

import biz.ugur.busroutebackend.business.domain.enums.BusinessStatus;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.model.Business;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BusinessRepository extends BaseRepository<Business, BusinessId> {

    Flux<Business> findByStatus(BusinessStatus status, Pageable pageable);

    Mono<Long> countByStatus(BusinessStatus status);

    Flux<Business> findByType(BusinessType type, Pageable pageable);

    Mono<Boolean> existsByTaxNumber(String taxNumber);

    Mono<Boolean> existsByContactPhone(String phone);
}
