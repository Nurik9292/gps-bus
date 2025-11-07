package biz.ugur.busroutebackend.admin.domain.repository;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface AdminRepository extends BaseRepository<Admin, AdminId> {


    Mono<Admin> findByUsername(String username);

    Flux<Admin> findActiveAdmins();


    Mono<Boolean> existsByUsername(String username);

    Mono<Long> countActiveAdmins();


    Mono<Admin> updateAvatar(AdminId adminId, String avatar);

    Flux<Admin> findBySpecification(Specification<Admin> specification);

    Flux<Admin> findBySpecification(Specification<Admin> specification, Pageable pageable);

    Mono<Long> countBySpecification(Specification<Admin> specification);
}