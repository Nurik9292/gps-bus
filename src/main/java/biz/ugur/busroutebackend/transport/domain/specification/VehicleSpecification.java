package biz.ugur.busroutebackend.transport.domain.specification;

import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VehicleSpecification implements Specification<Vehicle> {

    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> parameters = new HashMap<>();

    private Boolean isActive;
    private String licensePlateContains;
    private String deviceIdContains;
    private String routeNumber;
    private Boolean isAssigned;

    private VehicleSpecification() {}

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean isSatisfiedBy(Vehicle vehicle) {
        if (isActive != null && !isActive.equals(vehicle.getIsActive())) {
            return false;
        }
        if (licensePlateContains != null &&
            (vehicle.getLicensePlate() == null ||
             !vehicle.getLicensePlate().toLowerCase().contains(licensePlateContains.toLowerCase()))) {
            return false;
        }
        if (deviceIdContains != null &&
            (vehicle.getDeviceId() == null ||
             !vehicle.getDeviceId().toLowerCase().contains(deviceIdContains.toLowerCase()))) {
            return false;
        }
        if (routeNumber != null && !routeNumber.equals(vehicle.getRouteNumber())) {
            return false;
        }
        if (isAssigned != null) {
            boolean hasRoute = vehicle.getAssignedRouteId() != null;
            if (isAssigned != hasRoute) {
                return false;
            }
        }
        return true;
    }

    @Override
    public SqlCriteria toSqlCriteria() {
        List<String> whereClauses = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (isActive != null) {
            whereClauses.add("is_active = :isActive");
            params.put("isActive", isActive);
        }

        if (licensePlateContains != null) {
            whereClauses.add("LOWER(license_plate) LIKE :licensePlatePattern");
            params.put("licensePlatePattern", "%" + licensePlateContains.toLowerCase() + "%");
        }

        if (deviceIdContains != null) {
            whereClauses.add("LOWER(device_id) LIKE :deviceIdPattern");
            params.put("deviceIdPattern", "%" + deviceIdContains.toLowerCase() + "%");
        }

        if (routeNumber != null) {
            whereClauses.add("route_number = :routeNumber");
            params.put("routeNumber", routeNumber);
        }

        if (isAssigned != null) {
            if (isAssigned) {
                whereClauses.add("assigned_route_id IS NOT NULL");
            } else {
                whereClauses.add("assigned_route_id IS NULL");
            }
        }

        String whereClause = whereClauses.isEmpty()
            ? "1=1"
            : String.join(" AND ", whereClauses);

        return new SqlCriteria(whereClause, params);
    }

    public static class Builder {
        private final VehicleSpecification spec = new VehicleSpecification();

        public Builder isActive(Boolean isActive) {
            spec.isActive = isActive;
            return this;
        }

        public Builder licensePlateContains(String pattern) {
            spec.licensePlateContains = pattern;
            return this;
        }

        public Builder deviceIdContains(String pattern) {
            spec.deviceIdContains = pattern;
            return this;
        }

        public Builder routeNumber(String routeNumber) {
            spec.routeNumber = routeNumber;
            return this;
        }

        public Builder isAssigned(Boolean isAssigned) {
            spec.isAssigned = isAssigned;
            return this;
        }

        public VehicleSpecification build() {
            return spec;
        }
    }
}
