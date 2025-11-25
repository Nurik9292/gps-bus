package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.geospatial.domain.constants.TurkmenistanBounds;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.event.VehicleAssignedToRouteEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehicleRegisteredEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehiclePosition;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;

@Log4j2
@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class Vehicle extends AggregateRoot<Vehicle, VehicleId> {

    private final VehicleId id;

    private final String deviceId;
    private final String licensePlate;

    private final Double currentLatitude;
    private final Double currentLongitude;
    private final Double speedKmh;
    private final Boolean isInMotion;
    private final LocalDateTime lastPositionUpdate;
    private final Double course;

    private final BusRouteId assignedRouteId;
    private final String routeNumber;

    private final Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;


    public static Vehicle create(String deviceId, String licensePlate) {
        String validatedDeviceId = validateDeviceId(deviceId);
        String validatedLicensePlate = validateLicensePlate(licensePlate);

        Vehicle vehicle = builder()
                .id(VehicleId.generate())
                .deviceId(validatedDeviceId)
                .licensePlate(validatedLicensePlate)
                .isActive(true)
                .isInMotion(false)
                .speedKmh(0.0)
                .course(0.0)
                .lastPositionUpdate(LocalDateTime.now())
                .version(0L)
                .build();

        vehicle.registerEvent(new VehicleRegisteredEvent(
                vehicle.id.getValue(),
                vehicle.deviceId,
                vehicle.licensePlate
        ));

        return vehicle;
    }

    public static Vehicle restore(
            VehicleId id,
            String deviceId,
            String licensePlate,
            Double currentLatitude,
            Double currentLongitude,
            Double speedKmh,
            Boolean isInMotion,
            LocalDateTime lastPositionUpdate,
            BusRouteId assignedRouteId,
            String routeNumber,
            Boolean isActive,
            Double course,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {

        return builder()
                .id(id)
                .deviceId(deviceId)
                .licensePlate(licensePlate)
                .currentLatitude(currentLatitude)
                .currentLongitude(currentLongitude)
                .speedKmh(speedKmh != null ? speedKmh : 0.0)
                .isInMotion(isInMotion != null ? isInMotion : false)
                .lastPositionUpdate(lastPositionUpdate)
                .assignedRouteId(assignedRouteId)
                .routeNumber(routeNumber)
                .isActive(isActive != null ? isActive : true)
                .course(course != null ? course : 0.0)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }


    public Vehicle updatePosition(Double latitude, Double longitude, Double speed, LocalDateTime fixTime, Double course) {
        validateCoordinates(latitude, longitude);

        Double newSpeed = speed != null ? speed : 0.0;
        Boolean newIsInMotion = newSpeed > 1.0;
        LocalDateTime newFixTime = LocalDateTime.now();
        Double newCourse = course != null ? course : 0.0;

        Vehicle updatedVehicle = this.toBuilder()
                .currentLatitude(latitude)
                .currentLongitude(longitude)
                .speedKmh(newSpeed)
                .isInMotion(newIsInMotion)
                .lastPositionUpdate(newFixTime)
                .course(newCourse)
                .build();

        updatedVehicle.registerEvent(new VehiclePositionUpdatedEvent(
                this.id.getValue(),
                this.deviceId,
                this.licensePlate,
                this.routeNumber,
                latitude,
                longitude,
                newSpeed,
                newIsInMotion,
                newFixTime,
                newCourse
        ));

        return updatedVehicle;
    }

    public Vehicle updatePositionFromCoordinates(Coordinates coordinates, Double speed, LocalDateTime fixTime, Double course) {
        if (coordinates == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        return updatePosition(
                coordinates.getLatitudeAsDouble(),
                coordinates.getLongitudeAsDouble(),
                speed,
                fixTime,
                course
        );
    }

    public Vehicle assignToRoute(BusRouteId routeId) {
        if (routeId == null) {
            throw new IllegalArgumentException("Route ID cannot be null");
        }

        BusRouteId previousRoute = this.assignedRouteId;

        Vehicle updatedVehicle = this.toBuilder()
                .assignedRouteId(routeId)
                .build();

        updatedVehicle.registerEvent(new VehicleAssignedToRouteEvent(
                this.id.getValue(),
                this.licensePlate,
                previousRoute != null ? previousRoute.getValue() : null,
                routeId.getValue()
        ));

        return updatedVehicle;
    }

    public Vehicle unassignFromRoute() {
        if (this.assignedRouteId != null) {

            Vehicle updatedVehicle = this.toBuilder()
                    .assignedRouteId(null)
                    .routeNumber(null)
                    .build();

            updatedVehicle.registerEvent(new VehicleAssignedToRouteEvent(
                    this.id.getValue(),
                    this.licensePlate,
                    this.assignedRouteId.getValue(),
                    null
            ));

            return updatedVehicle;
        }
        return this;
    }

    public Vehicle deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }

        return this.toBuilder()
                .isActive(false)
                .isInMotion(false)
                .assignedRouteId(null)
                .routeNumber(null)
                .build();
    }

    public Vehicle activate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            return this;
        }

        return this.toBuilder()
                .isActive(true)
                .build();
    }

    public Vehicle updateCachedRouteNumber(String routeNumber) {
        return this.toBuilder()
                .routeNumber(routeNumber)
                .build();
    }

    public Vehicle updateDeviceId(String deviceId) {
        String validatedDeviceId = validateDeviceId(deviceId);
        return this.toBuilder()
                .deviceId(validatedDeviceId)
                .build();
    }


    public String getDisplayRouteNumber() {
        return this.routeNumber != null ? this.routeNumber : "UNASSIGNED";
    }

    /**
     * Checks if vehicle has recent GPS position update.
     * Default timeout is 10 minutes (600 seconds) - allows for traffic lights,
     * traffic jams, and passenger boarding.
     */
    public boolean hasRecentPosition() {
        return hasRecentPosition(600); // 10 minutes default
    }

    /**
     * Checks if vehicle has GPS position update within specified seconds.
     */
    public boolean hasRecentPosition(int maxAgeSeconds) {
        if (lastPositionUpdate == null) return false;
        return lastPositionUpdate.isAfter(LocalDateTime.now().minusSeconds(maxAgeSeconds));
    }

    public VehiclePosition getCurrentPosition() {
        if (currentLatitude == null || currentLongitude == null) {
            return null;
        }
        return new VehiclePosition(currentLatitude, currentLongitude, speedKmh, isInMotion);
    }

    public boolean hasAssignedRoute() {
        return assignedRouteId != null && this.routeNumber != null && !this.routeNumber.isEmpty();
    }

    public boolean hasPosition() {
        return currentLatitude != null && currentLongitude != null;
    }

    public boolean isMoving() {
        return Boolean.TRUE.equals(isInMotion) && speedKmh != null && speedKmh > 1.0;
    }

    public Coordinates toCoordinates() {
        if (currentLatitude == null || currentLongitude == null) {
            return null;
        }
        return Coordinates.of(currentLatitude, currentLongitude);
    }


    private static String validateDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID cannot be null or empty");
        }
        return deviceId.trim();
    }

    private static String validateLicensePlate(String licensePlate) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty");
        }

        String plate = licensePlate.trim().toUpperCase();
        if (!plate.matches("\\d{4}\\s[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid Turkmen license plate format. Expected: '1992 AGH'");
        }
        return plate;
    }

    private static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }

        if (!TurkmenistanBounds.isWithinStandardBounds(latitude, longitude)) {
            throw new IllegalArgumentException(
                    String.format("Coordinates (%.6f, %.6f) are outside Turkmenistan bounds", latitude, longitude)
            );
        }
    }


    @Override
    public VehicleId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id=" + id +
                ", licensePlate='" + licensePlate + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", routeNumber='" + getDisplayRouteNumber() + '\'' +
                ", isActive=" + isActive +
                ", isInMotion=" + isInMotion +
                ", hasPosition=" + hasPosition() +
                '}';
    }
}
