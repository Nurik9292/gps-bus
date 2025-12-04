package biz.ugur.busroutebackend.admin.infrastructure.security;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class AdminPrincipal implements UserDetails {

    private final AdminId id;
    private final String username;
    private final Set<String> roles;
    private final boolean isSuperAdmin;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;
    private final boolean enabled;

    public AdminPrincipal(AdminId id, String username, Set<String> roles, boolean isSuperAdmin) {
        this(id, username, roles, isSuperAdmin, true, true, true, true);
    }

    public AdminPrincipal(AdminId id, String username, Set<String> roles, boolean isSuperAdmin,
                          boolean accountNonExpired, boolean accountNonLocked,
                          boolean credentialsNonExpired, boolean enabled) {
        Objects.requireNonNull(id, "Admin ID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(roles, "Roles cannot be null");

        if (username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Admin must have at least one role");
        }

        this.id = id;
        this.username = username;
        this.roles = Collections.unmodifiableSet(roles);
        this.isSuperAdmin = isSuperAdmin;
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
        this.enabled = enabled;

    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }


    public boolean hasRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return false;
        }

        return roles.contains(role.trim().toUpperCase());
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
            return true;
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

        if (canPerformSuperAdminOperations()) {
            log.trace("Super admin {} can modify admin {}", username, targetAdminId.getValue());
            return true;
        }

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

    @Override
    public String toString() {
        return "AdminPrincipal{" +
                "id=" + id.getValue() +
                ", username='" + username + '\'' +
                ", roles=" + roles +
                ", isSuperAdmin=" + isSuperAdmin +
                ", enabled=" + enabled +
                '}';
    }


    public static AdminPrincipal fromAdmin(Admin admin) {
        Objects.requireNonNull(admin, "Admin cannot be null");

        Set<String> roles = admin.getIsSuperAdmin()
                ? Set.of("ADMIN", "SUPER_ADMIN")
                : Set.of("ADMIN");

        return new AdminPrincipal(
                admin.getId(),
                admin.getUsername(),
                roles,
                admin.getIsSuperAdmin(),
                true,
                true,
                true,
                admin.getIsActive()
        );
    }

    public static AdminPrincipal forTesting(String username, Set<String> roles, boolean isSuperAdmin) {
        return new AdminPrincipal(
                AdminId.generate(),
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
