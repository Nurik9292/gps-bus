package biz.ugur.busroutebackend.shared.infrastructure.security;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.extern.slf4j.Slf4j;

import java.security.Principal;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

@Slf4j
public record AdminPrincipal(
        AdminId id,
        String username,
        Set<String> roles,
        boolean isSuperAdmin
) implements Principal {

    public AdminPrincipal {
        Objects.requireNonNull(id, "Admin ID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(roles, "Roles cannot be null");

        if (username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Admin must have at least one role");
        }

        // Делаем роли immutable
        roles = Collections.unmodifiableSet(roles);

        log.debug("Created AdminPrincipal for user: {} with roles: {}", username, roles);
    }

    @Override
    public String getName() {
        return username;
    }

    public boolean hasRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return false;
        }

        boolean hasRole = roles.contains(role.trim().toUpperCase());
        log.trace("Admin {} role check for '{}': {}", username, role, hasRole);
        return hasRole;
    }

    public boolean hasAnyRole(String... rolesToCheck) {
        if (rolesToCheck == null || rolesToCheck.length == 0) {
            return false;
        }

        for (String role : rolesToCheck) {
            if (hasRole(role)) {
                log.trace("Admin {} has role '{}' from set: {}", username, role, rolesToCheck);
                return true;
            }
        }

        log.trace("Admin {} has none of roles: {}", username, rolesToCheck);
        return false;
    }

    public boolean hasAllRoles(String... rolesToCheck) {
        if (rolesToCheck == null || rolesToCheck.length == 0) {
            return true; // Если роли не указаны, считаем что все есть
        }

        for (String role : rolesToCheck) {
            if (!hasRole(role)) {
                log.trace("Admin {} missing role '{}' from required set: {}", username, role, rolesToCheck);
                return false;
            }
        }

        log.trace("Admin {} has all required roles: {}", username, rolesToCheck);
        return true;
    }

    public Set<String> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    @JsonIgnore
    public boolean isSuperAdmin() {
        return isSuperAdmin;
    }

    @JsonIgnore
    public boolean canPerformSuperAdminOperations() {
        boolean canPerform = isSuperAdmin && hasRole("SUPER_ADMIN");
        log.trace("Admin {} super admin operations check: {}", username, canPerform);
        return canPerform;
    }

    @JsonIgnore
    public boolean canManageAdmins() {
        return canPerformSuperAdminOperations();
    }

    @JsonIgnore
    public boolean canManageSystem() {
        return canPerformSuperAdminOperations();
    }

    public boolean canModifyAdmin(AdminId targetAdminId) {
        if (targetAdminId == null) {
            return false;
        }

        // Супер-админ может модифицировать любого
        if (canPerformSuperAdminOperations()) {
            log.trace("Super admin {} can modify admin {}", username, targetAdminId.getValue());
            return true;
        }

        // Обычный админ может модифицировать только себя
        boolean canModify = this.id.equals(targetAdminId);
        log.trace("Admin {} can modify admin {}: {}", username, targetAdminId.getValue(), canModify);
        return canModify;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        AdminPrincipal that = (AdminPrincipal) obj;
        return Objects.equals(id, that.id) &&
                Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    public static AdminPrincipal fromAdmin(biz.ugur.busroutebackend.admin.domain.model.Admin admin) {
        Objects.requireNonNull(admin, "Admin cannot be null");

        Set<String> roles = admin.getIsSuperAdmin()
                ? Set.of("ADMIN", "SUPER_ADMIN")
                : Set.of("ADMIN");

        return new AdminPrincipal(
                admin.getId(),
                admin.getUsername(),
                roles,
                admin.getIsSuperAdmin()
        );
    }

    public static AdminPrincipal forTesting(String username, Set<String> roles, boolean isSuperAdmin) {
        return new AdminPrincipal(
                AdminId.generate(), // Генерируем случайный ID для тестов
                username,
                roles,
                isSuperAdmin
        );
    }

    public static AdminPrincipal regularAdminForTesting(String username) {
        return forTesting(username, Set.of("ADMIN"), false);
    }

    public static AdminPrincipal superAdminForTesting(String username) {
        return forTesting(username, Set.of("ADMIN", "SUPER_ADMIN"), true);
    }
}