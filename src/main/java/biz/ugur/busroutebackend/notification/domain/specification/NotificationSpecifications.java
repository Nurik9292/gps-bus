package biz.ugur.busroutebackend.notification.domain.specification;

import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NotificationSpecifications {

    public static Specification<Notification> isActive() {
        return new Specification<Notification>() {
            @Override
            public boolean isSatisfiedBy(Notification notification) {
                return Boolean.TRUE.equals(notification.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", true);
            }
        };
    }

    public static Specification<Notification> isInactive() {
        return new Specification<Notification>() {
            @Override
            public boolean isSatisfiedBy(Notification notification) {
                return Boolean.FALSE.equals(notification.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", false);
            }
        };
    }

    public static Specification<Notification> titleContains(String searchText) {
        return new Specification<Notification>() {
            @Override
            public boolean isSatisfiedBy(Notification notification) {
                return notification.getTitle().getValue()
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

    public static Specification<Notification> displayOrderBetween(int min, int max) {
        return new Specification<Notification>() {
            @Override
            public boolean isSatisfiedBy(Notification notification) {
                int order = notification.getDisplayOrder();
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

    public static Specification<Notification> createdAfter(LocalDateTime date) {
        return new Specification<Notification>() {
            @Override
            public boolean isSatisfiedBy(Notification notification) {
                return notification.getCreatedAt().isAfter(date);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at > :createdAfter", "createdAfter", date);
            }
        };
    }


    public static Specification<Notification> hasId(String id) {
        return new Specification<Notification>() {
            @Override
            public boolean isSatisfiedBy(Notification notification) {
                return notification.getId().getValue().equals(id);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("id = :notificationId", "notificationId", UUID.fromString(id));
            }
        };
    }


    public static Specification<Notification> isReadyForDisplay() {
        return isActive();
    }
}
