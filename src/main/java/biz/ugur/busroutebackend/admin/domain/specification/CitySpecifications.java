package biz.ugur.busroutebackend.admin.domain.specification;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Specification implementations for City aggregate.
 * Provides composable query conditions following the Specification pattern.
 *
 * <p>Examples:
 * <pre>
 * // Find active cities with high priority
 * Specification<City> spec = isActive().and(displayOrderLessThan(10));
 *
 * // Search cities by name (English or Turkmen)
 * Specification<City> spec = nameContains("ashgabat").or(nameTmContains("aşgabat"));
 *
 * // Find recently created cities
 * Specification<City> spec = createdAfter(LocalDateTime.now().minusDays(7));
 * </pre>
 */
public class CitySpecifications {

    /**
     * Matches active cities (is_active = true).
     */
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

    /**
     * Matches inactive cities (is_active = false).
     */
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

    /**
     * Matches cities whose name contains the given text (case-insensitive).
     *
     * @param searchText text to search for in name
     */
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

    /**
     * Matches cities whose Turkmen name contains the given text (case-insensitive).
     *
     * @param searchText text to search for in Turkmen name
     */
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

    /**
     * Matches cities with a specific name (exact match, case-sensitive).
     *
     * @param name exact name to match
     */
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

    /**
     * Matches cities with a specific ID.
     *
     * @param id city ID to match
     */
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

    /**
     * Matches cities with display order less than the specified value.
     * Lower display order means higher priority.
     *
     * @param maxOrder maximum display order (exclusive)
     */
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

    /**
     * Matches cities with display order greater than the specified value.
     *
     * @param minOrder minimum display order (exclusive)
     */
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

    /**
     * Matches cities with display order between the specified range (inclusive).
     *
     * @param min minimum display order (inclusive)
     * @param max maximum display order (inclusive)
     */
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

    /**
     * Matches cities created after the specified date.
     *
     * @param date the date to compare against
     */
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

    /**
     * Matches cities created before the specified date.
     *
     * @param date the date to compare against
     */
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

    /**
     * Matches cities that have been updated after the specified date.
     *
     * @param date the date to compare against
     */
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

    /**
     * Matches cities that have a Turkmen name set.
     */
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

    /**
     * Matches cities that don't have a Turkmen name set.
     */
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

    // ============= Composite Specifications (combinations) =============

    /**
     * Matches active cities with high priority (display order < 10).
     * Useful for highlighting major cities.
     */
    public static Specification<City> isActiveWithHighPriority() {
        return isActive().and(displayOrderLessThan(10));
    }

    /**
     * Matches recently created cities (within specified days).
     *
     * @param daysAgo number of days to look back
     */
    public static Specification<City> isRecentlyCreated(int daysAgo) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysAgo);
        return createdAfter(cutoffDate);
    }

    /**
     * Matches recently updated cities (within specified days).
     *
     * @param daysAgo number of days to look back
     */
    public static Specification<City> isRecentlyUpdated(int daysAgo) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysAgo);
        return updatedAfter(cutoffDate);
    }

    /**
     * Searches cities by text in name OR Turkmen name.
     * Useful for general search functionality.
     *
     * @param searchText text to search for
     */
    public static Specification<City> searchByText(String searchText) {
        return nameContains(searchText).or(nameTmContains(searchText));
    }

    /**
     * Matches cities that are active AND have both English and Turkmen names set.
     * Useful for ensuring data completeness.
     */
    public static Specification<City> isCompleteAndActive() {
        return isActive().and(hasTurkmenName());
    }
}
