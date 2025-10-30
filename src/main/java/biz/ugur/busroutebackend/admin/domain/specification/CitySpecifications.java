package biz.ugur.busroutebackend.admin.domain.specification;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class CitySpecifications {

    public static Specification<City> isActive() {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return Boolean.TRUE.equals(city.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", true);
            }
        };
    }

    public static Specification<City> isInactive() {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return Boolean.FALSE.equals(city.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", false);
            }
        };
    }

    public static Specification<City> nameContains(String searchText) {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return city.getName()
                        .toLowerCase()
                        .contains(searchText.toLowerCase());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "LOWER(name) LIKE :nameSearch",
                        "nameSearch",
                        "%" + searchText.toLowerCase() + "%"
                );
            }
        };
    }

    public static Specification<City> nameTmContains(String searchText) {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                String nameTm = city.getNameTm();
                return nameTm != null &&
                        nameTm.toLowerCase().contains(searchText.toLowerCase());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "LOWER(name_tm) LIKE :nameTmSearch",
                        "nameTmSearch",
                        "%" + searchText.toLowerCase() + "%"
                );
            }
        };
    }

    public static Specification<City> hasName(String name) {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return city.getName().equals(name);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("name = :name", "name", name);
            }
        };
    }

    public static Specification<City> hasId(String id) {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return city.getId().getValue().equals(id);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("id = :cityId", "cityId", id);
            }
        };
    }

    public static Specification<City> displayOrderLessThan(int maxOrder) {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return city.getDisplayOrder() < maxOrder;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "display_order < :maxOrder",
                        "maxOrder",
                        maxOrder
                );
            }
        };
    }

    public static Specification<City> displayOrderGreaterThan(int minOrder) {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return city.getDisplayOrder() > minOrder;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "display_order > :minOrder",
                        "minOrder",
                        minOrder
                );
            }
        };
    }

    public static Specification<City> displayOrderBetween(int min, int max) {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                int order = city.getDisplayOrder();
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

    public static Specification<City> createdAfter(LocalDateTime date) {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return city.getCreatedAt().isAfter(date);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at > :createdAfter", "createdAfter", date);
            }
        };
    }

    public static Specification<City> createdBefore(LocalDateTime date) {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return city.getCreatedAt().isBefore(date);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at < :createdBefore", "createdBefore", date);
            }
        };
    }

    public static Specification<City> updatedAfter(LocalDateTime date) {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return city.getUpdatedAt().isAfter(date);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("updated_at > :updatedAfter", "updatedAfter", date);
            }
        };
    }

    public static Specification<City> hasTurkmenName() {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return city.getNameTm() != null && !city.getNameTm().isEmpty();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "name_tm IS NOT NULL AND name_tm != ''",
                        "dummyParam",
                        true
                );
            }
        };
    }

    public static Specification<City> noTurkmenName() {
        return new Specification<City>() {
            @Override
            public boolean isSatisfiedBy(City city) {
                return city.getNameTm() == null || city.getNameTm().isEmpty();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                        "(name_tm IS NULL OR name_tm = '')",
                        "dummyParam",
                        true
                );
            }
        };
    }


    public static Specification<City> isActiveWithHighPriority() {
        return isActive().and(displayOrderLessThan(10));
    }

    public static Specification<City> isRecentlyCreated(int daysAgo) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysAgo);
        return createdAfter(cutoffDate);
    }

    public static Specification<City> isRecentlyUpdated(int daysAgo) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysAgo);
        return updatedAfter(cutoffDate);
    }

    public static Specification<City> searchByText(String searchText) {
        return nameContains(searchText).or(nameTmContains(searchText));
    }

    public static Specification<City> isCompleteAndActive() {
        return isActive().and(hasTurkmenName());
    }
}
