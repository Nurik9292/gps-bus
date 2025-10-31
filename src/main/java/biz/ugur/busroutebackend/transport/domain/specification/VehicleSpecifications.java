package biz.ugur.busroutebackend.transport.domain.specification;

import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class VehicleSpecifications {


    public static Specification<Vehicle> isActive() {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return Boolean.TRUE.equals(vehicle.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", true);
            }
        };
    }


    public static Specification<Vehicle> isInactive() {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return Boolean.FALSE.equals(vehicle.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", false);
            }
        };
    }


    public static Specification<Vehicle> isInMotion() {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return Boolean.TRUE.equals(vehicle.getIsInMotion())
                    && vehicle.getSpeedKmh() != null
                    && vehicle.getSpeedKmh() > 1.0;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_in_motion = :inMotion AND speed_kmh > 1.0", "inMotion", true);
            }
        };
    }


    public static Specification<Vehicle> isStationary() {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return Boolean.FALSE.equals(vehicle.getIsInMotion())
                    || vehicle.getSpeedKmh() == null
                    || vehicle.getSpeedKmh() <= 1.0;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("(is_in_motion = false OR speed_kmh <= 1.0 OR speed_kmh IS NULL)", Collections.emptyMap());
            }
        };
    }


    public static Specification<Vehicle> hasRecentPosition() {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                if (vehicle.getLastPositionUpdate() == null) return false;
                return vehicle.getLastPositionUpdate().isAfter(LocalDateTime.now().minusMinutes(5));
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria(
                    "last_position_update > NOW() - INTERVAL '5 minutes'",
                    Collections.emptyMap()
                );
            }
        };
    }


    public static Specification<Vehicle> hasStalePosition(int minutes) {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                if (vehicle.getLastPositionUpdate() == null) return true;
                return vehicle.getLastPositionUpdate().isBefore(LocalDateTime.now().minusMinutes(minutes));
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                Map<String, Object> params = new HashMap<>();
                params.put("staleMinutes", minutes);
                return new SqlCriteria(
                    "last_position_update < NOW() - MAKE_INTERVAL(mins => :staleMinutes) OR last_position_update IS NULL",
                    params
                );
            }
        };
    }

    public static Specification<Vehicle> isUnassigned() {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return vehicle.getAssignedRouteId() == null
                    || vehicle.getRouteNumber() == null
                    || vehicle.getRouteNumber().isEmpty();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("(route_number IS NULL OR assigned_route_id IS NULL)", Collections.emptyMap());
            }
        };
    }


    public static Specification<Vehicle> isAssigned() {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return vehicle.getAssignedRouteId() != null
                    && vehicle.getRouteNumber() != null
                    && !vehicle.getRouteNumber().isEmpty();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("route_number IS NOT NULL AND assigned_route_id IS NOT NULL", Collections.emptyMap());
            }
        };
    }


    public static Specification<Vehicle> hasRouteNumber(String routeNumber) {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return routeNumber != null && routeNumber.equals(vehicle.getRouteNumber());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("route_number = :routeNumber", "routeNumber", routeNumber);
            }
        };
    }


    public static Specification<Vehicle> hasDeviceId(String deviceId) {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return deviceId != null && deviceId.equals(vehicle.getDeviceId());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("device_id = :deviceId", "deviceId", deviceId);
            }
        };
    }


    public static Specification<Vehicle> hasLicensePlate(String licensePlate) {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return licensePlate != null && licensePlate.equals(vehicle.getLicensePlate());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("license_plate = :licensePlate", "licensePlate", licensePlate);
            }
        };
    }


    public static Specification<Vehicle> hasPosition() {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return vehicle.getCurrentLatitude() != null && vehicle.getCurrentLongitude() != null;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria("current_latitude IS NOT NULL AND current_longitude IS NOT NULL", Collections.emptyMap());
            }
        };
    }


    public static Specification<Vehicle> speedAbove(double kmh) {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return vehicle.getSpeedKmh() != null && vehicle.getSpeedKmh() > kmh;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("speed_kmh > :speedThreshold", "speedThreshold", kmh);
            }
        };
    }


    public static Specification<Vehicle> speedBelow(double kmh) {
        return new Specification<Vehicle>() {
            @Override
            public boolean isSatisfiedBy(Vehicle vehicle) {
                return vehicle.getSpeedKmh() != null && vehicle.getSpeedKmh() < kmh;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("speed_kmh < :speedThreshold", "speedThreshold", kmh);
            }
        };
    }


    public static Specification<Vehicle> isReadyForService() {
        return isActive()
            .and(isAssigned())
            .and(hasRecentPosition())
            .and(hasPosition());
    }


    public static Specification<Vehicle> isOperational() {
        return isActive()
            .and(hasRecentPosition())
            .and(hasPosition());
    }


    public static Specification<Vehicle> requiresAttention() {
        return isActive()
            .and(hasStalePosition(10).or(isUnassigned()));
    }


    public static Specification<Vehicle> isActivelyWorking() {
        return isActive()
            .and(isAssigned())
            .and(isInMotion())
            .and(hasRecentPosition());
    }
}
