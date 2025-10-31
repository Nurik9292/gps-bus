package biz.ugur.busroutebackend.transport.domain.specification;

import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class BusStopSpecifications {

    public static Specification<BusStop> isActive() {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return Boolean.TRUE.equals(stop.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", true);
            }
        };
    }

    public static Specification<BusStop> isInactive() {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return Boolean.FALSE.equals(stop.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", false);
            }
        };
    }

    public static Specification<BusStop> isMajorStop() {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return Boolean.TRUE.equals(stop.getIsMajorStop());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_major_stop = :isMajor", "isMajor", true);
            }
        };
    }

    public static Specification<BusStop> isRegularStop() {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return Boolean.FALSE.equals(stop.getIsMajorStop());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("(is_major_stop = false OR is_major_stop IS NULL)", Collections.emptyMap());
            }
        };
    }

    public static Specification<BusStop> hasLocation() {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return stop.hasLocation();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("latitude IS NOT NULL AND longitude IS NOT NULL", Collections.emptyMap());
            }
        };
    }

    public static Specification<BusStop> servesCity(String cityId) {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return cityId != null && cityId.equals(stop.getCityId());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("city_id = :cityId", "cityId", cityId);
            }
        };
    }

    public static Specification<BusStop> nameContains(String searchText) {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return stop.getStopName() != null
                    && stop.getStopName().toLowerCase().contains(searchText.toLowerCase());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                    "LOWER(stop_name) LIKE :searchText OR LOWER(name_en) LIKE :searchText OR LOWER(name_tm) LIKE :searchText",
                    "searchText",
                    "%" + searchText.toLowerCase() + "%"
                );
            }
        };
    }

    public static Specification<BusStop> hasEnglishTranslation() {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return stop.hasTranslation("en");
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("name_en IS NOT NULL AND name_en != ''", Collections.emptyMap());
            }
        };
    }

    public static Specification<BusStop> hasTurkmenTranslation() {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return stop.hasTranslation("tm");
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("name_tm IS NOT NULL AND name_tm != ''", Collections.emptyMap());
            }
        };
    }

    public static Specification<BusStop> hasCompleteTranslations() {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return stop.hasCompleteTranslations();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria(
                    "stop_name IS NOT NULL AND stop_name != '' AND name_en IS NOT NULL AND name_en != '' AND name_tm IS NOT NULL AND name_tm != ''",
                    Collections.emptyMap()
                );
            }
        };
    }

    public static Specification<BusStop> hasStopCode(String stopCode) {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return stop.getStopCode() != null && stopCode.equals(stop.getStopCode().getValue());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("stop_code = :stopCode", "stopCode", stopCode);
            }
        };
    }

    public static Specification<BusStop> hasId(String id) {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return stop.getId().getValue().equals(id);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("id = :stopId", "stopId", id);
            }
        };
    }

    public static Specification<BusStop> servesMultipleRoutes(int minRoutes) {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                return stop.getServingRoutesCount() >= minRoutes;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of(
                    "serving_routes_count >= :minRoutes",
                    "minRoutes",
                    minRoutes
                );
            }
        };
    }

    public static Specification<BusStop> withinBoundingBox(
            double minLat, double minLon, double maxLat, double maxLon) {
        return new Specification<BusStop>() {
            @Override
            public boolean isSatisfiedBy(BusStop stop) {
                if (!stop.hasLocation()) return false;
                double lat = stop.getLatitude().doubleValue();
                double lon = stop.getLongitude().doubleValue();
                return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                Map<String, Object> params = new HashMap<>();
                params.put("minLat", minLat);
                params.put("minLon", minLon);
                params.put("maxLat", maxLat);
                params.put("maxLon", maxLon);
                return new SqlCriteria(
                    "latitude >= :minLat AND latitude <= :maxLat AND longitude >= :minLon AND longitude <= :maxLon",
                    params
                );
            }
        };
    }

    public static Specification<BusStop> isReadyForService() {
        return isActive()
            .and(hasLocation());
    }

    public static Specification<BusStop> isMajorTransferPoint() {
        return isActive()
            .and(isMajorStop())
            .and(servesMultipleRoutes(2));
    }

    public static Specification<BusStop> isFullyConfigured() {
        return isActive()
            .and(hasLocation())
            .and(hasCompleteTranslations());
    }

    public static Specification<BusStop> requiresAttention() {
        return isActive()
            .and(hasLocation().not().or(hasCompleteTranslations().not()));
    }

    public static Specification<BusStop> isImportant() {
        return isReadyForService()
            .and(isMajorStop())
            .and(servesMultipleRoutes(3));
    }
}
