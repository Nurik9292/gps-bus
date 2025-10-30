package biz.ugur.busroutebackend.admin.domain.specification;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AdminSpecifications {

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


    public static Specification<Admin> isActiveRegularAdmin() {
        return isActive().and(isRegularAdmin());
    }

    public static Specification<Admin> isActiveSuperAdmin() {
        return isActive().and(isSuperAdmin());
    }

    public static Specification<Admin> hasRecentLogin(int daysAgo) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysAgo);
        return lastLoginAfter(cutoffDate);
    }

    public static Specification<Admin> hasStaleLogin(int daysAgo) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysAgo);
        return lastLoginBefore(cutoffDate).or(neverLoggedIn());
    }

    public static Specification<Admin> searchByText(String searchText) {
        return usernameContains(searchText).or(fullNameContains(searchText));
    }
}
