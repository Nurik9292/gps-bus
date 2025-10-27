package biz.ugur.busroutebackend.banner.domain.specification;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BannerSpecifications {

    public static Specification<Banner> isActive() {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                return Boolean.TRUE.equals(banner.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", true);
            }
        };
    }

    public static Specification<Banner> isInactive() {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                return Boolean.FALSE.equals(banner.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", false);
            }
        };
    }

    public static Specification<Banner> hasType(BannerType type) {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                return banner.getType() == type;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("type = :type", "type", type.getValue());
            }
        };
    }

    public static Specification<Banner> isPeriodActive(LocalDateTime now) {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                LocalDateTime start = banner.getPeriod().getStartTime();
                LocalDateTime end = banner.getPeriod().getEndTime();
                return !now.isBefore(start) && !now.isAfter(end);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                Map<String, Object> params = new HashMap<>();
                params.put("periodNow", now);
                return new SqlCriteria(
                    "start_date <= :periodNow AND end_date >= :periodNow",
                    params
                );
            }
        };
    }


    public static Specification<Banner> periodExpiresWithinDays(int daysFromNow) {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                LocalDateTime deadline = LocalDateTime.now().plusDays(daysFromNow);
                LocalDateTime end = banner.getPeriod().getEndTime();
                return end.isBefore(deadline) && end.isAfter(LocalDateTime.now());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                LocalDateTime deadline = LocalDateTime.now().plusDays(daysFromNow);
                Map<String, Object> params = new HashMap<>();
                params.put("expiryDeadline", deadline);
                params.put("expiryNow", LocalDateTime.now());
                return new SqlCriteria(
                    "end_date < :expiryDeadline AND end_date > :expiryNow",
                    params
                );
            }
        };
    }


    public static Specification<Banner> titleContains(String searchText) {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                return banner.getTitle().getValue()
                    .toLowerCase()
                    .contains(searchText.toLowerCase());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                    "LOWER(title) LIKE :titleSearch",
                    "titleSearch",
                    "%" + searchText.toLowerCase() + "%"
                );
            }
        };
    }

    public static Specification<Banner> displayOrderBetween(int min, int max) {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                int order = banner.getDisplayOrder();
                return order >= min && order <= max;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                Map<String, Object> params = new HashMap<>();
                params.put("minOrder", min);
                params.put("maxOrder", max);
                return new SqlCriteria(
                    "display_order BETWEEN :minOrder AND :maxOrder",
                    params
                );
            }
        };
    }

    public static Specification<Banner> createdAfter(LocalDateTime date) {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                return banner.getCreatedAt().isAfter(date);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at > :createdAfter", "createdAfter", date);
            }
        };
    }


    public static Specification<Banner> hasId(String id) {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                return banner.getId().getValue().equals(id);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("id = :bannerId", "bannerId", UUID.fromString(id));
            }
        };
    }


    public static Specification<Banner> isReadyForDisplay() {
        return isActive()
            .and(isPeriodActive(LocalDateTime.now()));
    }


    public static Specification<Banner> requiresAdminAttention() {
        return isActive()
            .and(periodExpiresWithinDays(7));
    }
}
