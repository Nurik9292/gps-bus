package biz.ugur.busroutebackend.admin.domain.repository;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository interface for Admin aggregate.
 * Extends BaseRepository for CRUD operations and adds admin-specific queries.
 * Supports Specification pattern for flexible, composable queries.
 */
public interface AdminRepository extends BaseRepository<Admin, AdminId> {

    // ============= Basic Queries =============

    /**
     * Find admin by username.
     *
     * @param username the username to search for
     * @return Mono of Admin if found, empty otherwise
     */
    Mono<Admin> findByUsername(String username);

    /**
     * Find all active admins.
     *
     * @return Flux of active admins
     */
    Flux<Admin> findActiveAdmins();

    /**
     * Check if admin with given username exists.
     *
     * @param username the username to check
     * @return Mono of Boolean (true if exists)
     */
    Mono<Boolean> existsByUsername(String username);

    /**
     * Count active admins.
     *
     * @return Mono of Long (count of active admins)
     */
    Mono<Long> countActiveAdmins();

    /**
     * Update admin avatar (optimized update for avatar only).
     *
     * @param adminId the admin ID
     * @param avatar the new avatar URL
     * @return Mono of updated Admin
     */
    Mono<Admin> updateAvatar(AdminId adminId, String avatar);

    // ============= Specification Pattern Support =============

    /**
     * Find admins matching the given specification.
     *
     * @param specification the specification to match
     * @return Flux of admins matching the specification
     */
    Flux<Admin> findBySpecification(Specification<Admin> specification);

    /**
     * Find admins matching the given specification with pagination.
     *
     * @param specification the specification to match
     * @param pageable pagination parameters
     * @return Flux of admins matching the specification
     */
    Flux<Admin> findBySpecification(Specification<Admin> specification, Pageable pageable);

    /**
     * Count admins matching the given specification.
     *
     * @param specification the specification to match
     * @return Mono of Long (count of matching admins)
     */
    Mono<Long> countBySpecification(Specification<Admin> specification);
}