package biz.ugur.busroutebackend.admin.domain.repository;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AdminRepository {

    Mono<Admin> save(Admin admin);

    Mono<Admin> findById(AdminId adminId);

    Mono<Admin> findByUsername(String username);

    Flux<Admin> findActiveAdmins();

    Flux<Admin> findAllAdmins();

    Mono<Boolean> existsByUsername(String username);

    Mono<Void> deleteById(AdminId adminId);

    Mono<Long> countActiveAdmins();

    Mono<Admin> updateAvatar(AdminId adminId, String avatar);
}