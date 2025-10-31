package biz.ugur.busroutebackend.transport.domain.specification;

import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class BusRouteSpecifications {


    public static Specification<BusRoute> isActive() {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return Boolean.TRUE.equals(route.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", true);
            }
        };
    }


    public static Specification<BusRoute> isInactive() {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return Boolean.FALSE.equals(route.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", false);
            }
        };
    }


    public static Specification<BusRoute> hasGeometry() {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return route.hasGeometry();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria(
                    "(route_geometry_forward IS NOT NULL OR route_geometry_backward IS NOT NULL)",
                    Collections.emptyMap()
                );
            }
        };
    }

    public static Specification<BusRoute> hasForwardGeometry() {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return route.hasForwardGeometry();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("route_geometry_forward IS NOT NULL", Collections.emptyMap());
            }
        };
    }


    public static Specification<BusRoute> hasBackwardGeometry() {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return route.hasBackwardGeometry();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("route_geometry_backward IS NOT NULL", Collections.emptyMap());
            }
        };
    }


    public static Specification<BusRoute> hasCompleteGeometry() {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return route.hasCompleteGeometry();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria(
                    "route_geometry_forward IS NOT NULL AND route_geometry_backward IS NOT NULL",
                    Collections.emptyMap()
                );
            }
        };
    }


    public static Specification<BusRoute> servesCity(String cityId) {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return cityId != null && cityId.equals(route.getCityId());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("city_id = :cityId", "cityId", cityId);
            }
        };
    }


    public static Specification<BusRoute> hasRouteNumber(String routeNumber) {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return routeNumber != null && routeNumber.equalsIgnoreCase(route.getRouteNumber());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("UPPER(route_number) = UPPER(:routeNumber)", "routeNumber", routeNumber);
            }
        };
    }


    public static Specification<BusRoute> nameContains(String searchText) {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return route.getRouteName() != null
                    && route.getRouteName().toLowerCase().contains(searchText.toLowerCase());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                    "LOWER(route_name) LIKE :searchText",
                    "searchText",
                    "%" + searchText.toLowerCase() + "%"
                );
            }
        };
    }


    public static Specification<BusRoute> hasEnglishTranslation() {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return route.getNameEn() != null && !route.getNameEn().trim().isEmpty();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("name_en IS NOT NULL AND name_en != ''", Collections.emptyMap());
            }
        };
    }


    public static Specification<BusRoute> hasTurkmenTranslation() {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return route.getNameTm() != null && !route.getNameTm().trim().isEmpty();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("name_tm IS NOT NULL AND name_tm != ''", Collections.emptyMap());
            }
        };
    }


    public static Specification<BusRoute> distanceAbove(int meters) {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                Integer forwardDist = route.getTotalDistanceForwardMeters();
                Integer backwardDist = route.getTotalDistanceBackwardMeters();
                return (forwardDist != null && forwardDist > meters)
                    || (backwardDist != null && backwardDist > meters);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                    "(total_distance_forward_meters > :distanceThreshold OR total_distance_backward_meters > :distanceThreshold)",
                    "distanceThreshold",
                    meters
                );
            }
        };
    }


    public static Specification<BusRoute> durationAbove(int minutes) {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                Integer duration = route.getEstimatedDurationMinutes();
                return duration != null && duration > minutes;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                    "estimated_duration_minutes > :durationThreshold",
                    "durationThreshold",
                    minutes
                );
            }
        };
    }


    public static Specification<BusRoute> hasColor(String colorHex) {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return colorHex != null && colorHex.equalsIgnoreCase(route.getRouteColor());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("UPPER(route_color) = UPPER(:routeColor)", "routeColor", colorHex);
            }
        };
    }


    public static Specification<BusRoute> hasId(String id) {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                return route.getId().getValue().equals(id);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("id = :routeId", "routeId", id);
            }
        };
    }

    public static Specification<BusRoute> isReadyForService() {
        return isActive()
            .and(hasCompleteGeometry());
    }


    public static Specification<BusRoute> requiresGeometryUpdate() {
        return isActive()
            .and(hasGeometry().not());
    }


    public static Specification<BusRoute> isFullyConfigured() {
        return isActive()
            .and(hasCompleteGeometry())
            .and(hasEnglishTranslation())
            .and(hasTurkmenTranslation());
    }


    public static Specification<BusRoute> isLongDistance() {
        return isActive()
            .and(distanceAbove(10000)); // 10 km
    }


    public static Specification<BusRoute> isShortDistance() {
        return new Specification<BusRoute>() {
            @Override
            public boolean isSatisfiedBy(BusRoute route) {
                Integer forwardDist = route.getTotalDistanceForwardMeters();
                Integer backwardDist = route.getTotalDistanceBackwardMeters();
                return (forwardDist != null && forwardDist < 5000)
                    || (backwardDist != null && backwardDist < 5000);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria(
                    "(total_distance_forward_meters < 5000 OR total_distance_backward_meters < 5000)",
                    Collections.emptyMap()
                );
            }
        };
    }
}
