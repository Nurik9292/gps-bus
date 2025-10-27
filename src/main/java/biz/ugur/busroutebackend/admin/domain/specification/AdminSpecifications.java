package biz.ugur.busroutebackend.admin.domain.specification;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Specification implementations for Admin aggregate.
 * Provides composable query conditions following the Specification pattern.
 *
 * <p>Examples:
 * <pre>
 * // Find active super admins
 * Specification<Admin> spec = isActive().and(isSuperAdmin());
 *
 * // Find regular admins with recent login
 * Specification<Admin> spec = isRegularAdmin().and(lastLoginAfter(someDate));
 *
 * // Search by username or full name
 * Specification<Admin> spec = usernameContains("john").or(fullNameContains("john"));
 * </pre>
 */
public class AdminSpecifications {

    /**
     * Matches active admins (is_active = true).
     */
    public static Specification<Admin> isActive() {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return Boolean.TRUE.equals(admin.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", true);
            }
        };
    }

    /**
     * Matches inactive admins (is_active = false).
     */
    public static Specification<Admin> isInactive() {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return Boolean.FALSE.equals(admin.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", false);
            }
        };
    }

    /**
     * Matches super admins (is_super_admin = true).
     */
    public static Specification<Admin> isSuperAdmin() {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return Boolean.TRUE.equals(admin.getIsSuperAdmin());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_super_admin = :isSuperAdmin", "isSuperAdmin", true);
            }
        };
    }

    /**
     * Matches regular admins (is_super_admin = false).
     */
    public static Specification<Admin> isRegularAdmin() {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return Boolean.FALSE.equals(admin.getIsSuperAdmin());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_super_admin = :isSuperAdmin", "isSuperAdmin", false);
            }
        };
    }

    /**
     * Matches admins whose username contains the given text (case-insensitive).
     *
     * @param searchText text to search for in username
     */
    public static Specification<Admin> usernameContains(String searchText) {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return admin.getUsername()
                        .toLowerCase()
                        .contains(searchText.toLowerCase());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "LOWER(username) LIKE :usernameSearch",
                        "usernameSearch",
                        "%" + searchText.toLowerCase() + "%"
                );
            }
        };
    }

    /**
     * Matches admins whose full name contains the given text (case-insensitive).
     *
     * @param searchText text to search for in full name
     */
    public static Specification<Admin> fullNameContains(String searchText) {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return admin.getFullName()
                        .toLowerCase()
                        .contains(searchText.toLowerCase());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "LOWER(full_name) LIKE :fullNameSearch",
                        "fullNameSearch",
                        "%" + searchText.toLowerCase() + "%"
                );
            }
        };
    }

    /**
     * Matches admins with a specific username (exact match).
     *
     * @param username exact username to match
     */
    public static Specification<Admin> hasUsername(String username) {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return admin.getUsername().equals(username);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("username = :username", "username", username);
            }
        };
    }

    /**
     * Matches admins with a specific ID.
     *
     * @param id admin ID to match
     */
    public static Specification<Admin> hasId(String id) {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return admin.getId().getValue().equals(id);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("id = :adminId", "adminId", id);
            }
        };
    }

    /**
     * Matches admins who logged in after the specified date.
     *
     * @param date the date to compare against
     */
    public static Specification<Admin> lastLoginAfter(LocalDateTime date) {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                LocalDateTime lastLogin = admin.getLastLoginAt();
                return lastLogin != null && lastLogin.isAfter(date);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "last_login_at > :lastLoginAfter",
                        "lastLoginAfter",
                        date
                );
            }
        };
    }

    /**
     * Matches admins who logged in before the specified date.
     *
     * @param date the date to compare against
     */
    public static Specification<Admin> lastLoginBefore(LocalDateTime date) {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                LocalDateTime lastLogin = admin.getLastLoginAt();
                return lastLogin != null && lastLogin.isBefore(date);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "last_login_at < :lastLoginBefore",
                        "lastLoginBefore",
                        date
                );
            }
        };
    }

    /**
     * Matches admins who never logged in.
     */
    public static Specification<Admin> neverLoggedIn() {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return admin.getLastLoginAt() == null;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("last_login_at IS NULL", "dummyParam", true);
            }
        };
    }

    /**
     * Matches admins created after the specified date.
     *
     * @param date the date to compare against
     */
    public static Specification<Admin> createdAfter(LocalDateTime date) {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return admin.getCreatedAt().isAfter(date);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at > :createdAfter", "createdAfter", date);
            }
        };
    }

    /**
     * Matches admins created before the specified date.
     *
     * @param date the date to compare against
     */
    public static Specification<Admin> createdBefore(LocalDateTime date) {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return admin.getCreatedAt().isBefore(date);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at < :createdBefore", "createdBefore", date);
            }
        };
    }

    /**
     * Matches admins who have an avatar set.
     */
    public static Specification<Admin> hasAvatar() {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return admin.getAvatar() != null && !admin.getAvatar().isEmpty();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "avatar IS NOT NULL AND avatar != ''",
                        "dummyParam",
                        true
                );
            }
        };
    }

    /**
     * Matches admins who don't have an avatar set.
     */
    public static Specification<Admin> noAvatar() {
        return new Specification<Admin>() {
            @Override
            public boolean isSatisfiedBy(Admin admin) {
                return admin.getAvatar() == null || admin.getAvatar().isEmpty();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "(avatar IS NULL OR avatar = '')",
                        "dummyParam",
                        true
                );
            }
        };
    }

    // ============= Composite Specifications (combinations) =============

    /**
     * Matches admins who are active and regular (not super admin).
     * Useful for listing regular active users.
     */
    public static Specification<Admin> isActiveRegularAdmin() {
        return isActive().and(isRegularAdmin());
    }

    /**
     * Matches admins who are active super admins.
     * Useful for security checks or admin management.
     */
    public static Specification<Admin> isActiveSuperAdmin() {
        return isActive().and(isSuperAdmin());
    }

    /**
     * Matches admins who logged in recently (within specified days).
     *
     * @param daysAgo number of days to look back
     */
    public static Specification<Admin> hasRecentLogin(int daysAgo) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysAgo);
        return lastLoginAfter(cutoffDate);
    }

    /**
     * Matches admins who haven't logged in for a specified number of days.
     * Useful for identifying inactive accounts.
     *
     * @param daysAgo number of days to look back
     */
    public static Specification<Admin> hasStaleLogin(int daysAgo) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysAgo);
        return lastLoginBefore(cutoffDate).or(neverLoggedIn());
    }

    /**
     * Searches admins by text in username OR full name.
     * Useful for general search functionality.
     *
     * @param searchText text to search for
     */
    public static Specification<Admin> searchByText(String searchText) {
        return usernameContains(searchText).or(fullNameContains(searchText));
    }
}
