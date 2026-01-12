package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.constants.VehicleConstants;
import biz.ugur.busroutebackend.transport.domain.service.VehicleValidationService;
import biz.ugur.busroutebackend.transport.domain.event.VehicleAssignedToRouteEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehicleRegisteredEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
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

    private final Integer currentDirection;

    private final Integer lastStopSequence;

    private final BusRouteId assignedRouteId;
    private final String routeNumber;

    private final Boolean isActive;

    private final String lastGarageId;
    private final LocalDateTime garageEntryTime;
    private final LocalDateTime garageExitTime;
    private final Boolean isInGarage;

    private final RouteSource routeSource;
    private final Integer routeConfidence;
    private final Boolean gpsDetectionEnabled;

    private final GpsProviderType gpsProvider;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;


    public static Vehicle create(String deviceId, String licensePlate) {
        String validatedDeviceId = VehicleValidationService.validateDeviceId(deviceId);
        String validatedLicensePlate = VehicleValidationService.validateLicensePlate(licensePlate);

        Vehicle vehicle = builder()
                .id(VehicleId.generate())
                .deviceId(validatedDeviceId)
                .licensePlate(validatedLicensePlate)
                .isActive(true)
                .isInMotion(false)
                .speedKmh(0.0)
                .course(0.0)
                .currentDirection(null)
                .lastStopSequence(null)
                .lastPositionUpdate(LocalDateTime.now())
                .lastGarageId(null)
                .garageEntryTime(null)
                .garageExitTime(null)
                .isInGarage(false)
                .routeSource(RouteSource.UNKNOWN)
                .routeConfidence(0)
                .gpsDetectionEnabled(true)
                .gpsProvider(GpsProviderType.defaultProvider())
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
            Integer currentDirection,
            Integer lastStopSequence,
            String lastGarageId,
            LocalDateTime garageEntryTime,
            LocalDateTime garageExitTime,
            Boolean isInGarage,
            RouteSource routeSource,
            Integer routeConfidence,
            Boolean gpsDetectionEnabled,
            GpsProviderType gpsProvider,
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
                .currentDirection(currentDirection)
                .lastStopSequence(lastStopSequence)
                .lastGarageId(lastGarageId)
                .garageEntryTime(garageEntryTime)
                .garageExitTime(garageExitTime)
                .isInGarage(isInGarage != null ? isInGarage : false)
                .routeSource(routeSource != null ? routeSource : RouteSource.UNKNOWN)
                .routeConfidence(routeConfidence != null ? routeConfidence : 0)
                .gpsDetectionEnabled(gpsDetectionEnabled != null ? gpsDetectionEnabled : true)
                .gpsProvider(gpsProvider != null ? gpsProvider : GpsProviderType.defaultProvider())
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }


    public Vehicle updatePosition(Double latitude, Double longitude, Double speed, LocalDateTime fixTime, Double course) {
        VehicleValidationService.validateCoordinates(latitude, longitude);

        Double newSpeed = sanitizeSpeed(speed);
        Boolean newIsInMotion = newSpeed > VehicleConstants.MOTION_SPEED_THRESHOLD_KMH;
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
                newCourse,
                this.currentDirection
        ));

        return updatedVehicle;
    }

    public Vehicle updateDirection(Integer newStopSequence, Integer stopDirection) {
        if (newStopSequence == null || stopDirection == null) {
            return this;
        }

        if (this.currentDirection == null) {
            log.debug("Vehicle {} direction initialized: {} (stop seq: {})",
                    licensePlate, stopDirection == 0 ? "forward" : "backward", newStopSequence);
        } else if (!this.currentDirection.equals(stopDirection)) {
            log.debug("Vehicle {} direction changed: {} -> {} (stop seq: {})",
                    licensePlate,
                    this.currentDirection == 0 ? "forward" : "backward",
                    stopDirection == 0 ? "forward" : "backward",
                    newStopSequence);
        } else if (this.lastStopSequence != null && !this.lastStopSequence.equals(newStopSequence)) {
            log.debug("Vehicle {} moving {} (seq: {} -> {})",
                    licensePlate, stopDirection == 0 ? "forward" : "backward",
                    this.lastStopSequence, newStopSequence);
        }

        return this.toBuilder()
                .currentDirection(stopDirection)
                .lastStopSequence(newStopSequence)
                .build();
    }

    public Vehicle clearRouteAssignment() {
        if (this.assignedRouteId == null && this.routeNumber == null) {
            return this;
        }

        String previousRouteId = this.assignedRouteId != null ? this.assignedRouteId.getValue() : null;

        Vehicle updatedVehicle = this.toBuilder()
                .assignedRouteId(null)
                .routeNumber(null)
                .routeSource(RouteSource.UNKNOWN)
                .routeConfidence(0)
                .build();

        if (previousRouteId != null) {
            updatedVehicle.registerEvent(new VehicleAssignedToRouteEvent(
                    this.id.getValue(),
                    this.licensePlate,
                    previousRouteId,
                    null
            ));
        }

        return updatedVehicle;
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
        String validatedDeviceId = VehicleValidationService.validateDeviceId(deviceId);
        return this.toBuilder()
                .deviceId(validatedDeviceId)
                .build();
    }

    public boolean hasRecentPosition() {
        return hasRecentPosition((int) VehicleConstants.RECENT_POSITION_THRESHOLD_SECONDS);
    }

    public boolean hasRecentPosition(int maxAgeSeconds) {
        if (lastPositionUpdate == null) return false;
        return lastPositionUpdate.isAfter(LocalDateTime.now().minusSeconds(maxAgeSeconds));
    }

    public boolean hasAssignedRoute() {
        return assignedRouteId != null && this.routeNumber != null && !this.routeNumber.isEmpty();
    }

    public boolean hasPosition() {
        return currentLatitude != null && currentLongitude != null;
    }

    public Coordinates toCoordinates() {
        if (currentLatitude == null || currentLongitude == null) {
            return null;
        }
        return Coordinates.of(currentLatitude, currentLongitude);
    }


    public Vehicle enterGarage(String garageId) {
        if (garageId == null || garageId.trim().isEmpty()) {
            throw new IllegalArgumentException("Garage ID cannot be null or empty");
        }

        LocalDateTime now = LocalDateTime.now();

        return this.toBuilder()
                .lastGarageId(garageId)
                .garageEntryTime(now)
                .garageExitTime(null)
                .isInGarage(true)
                .isInMotion(false)
                .build();
    }


    public Vehicle exitGarage() {
        if (!Boolean.TRUE.equals(isInGarage)) {
            log.warn("Attempted to exit garage for vehicle {} that is not in garage", licensePlate);
            return this;
        }

        LocalDateTime now = LocalDateTime.now();

        return this.toBuilder()
                .garageExitTime(now)
                .isInGarage(false)
                .build();
    }


    public boolean isCurrentlyInGarage() {
        return Boolean.TRUE.equals(isInGarage);
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

    private static Double sanitizeSpeed(Double speed) {
        if (speed == null) {
            return 0.0;
        }
        if (speed < VehicleConstants.MIN_SPEED_KMH || speed > VehicleConstants.MAX_SPEED_KMH) {
            log.warn("GPS anomaly: speed {} km/h out of valid range [{}-{}], clamping",
                    speed, VehicleConstants.MIN_SPEED_KMH, VehicleConstants.MAX_SPEED_KMH);
        }
        return Math.max(VehicleConstants.MIN_SPEED_KMH, Math.min(speed, VehicleConstants.MAX_SPEED_KMH));
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id=" + id +
                ", licensePlate='" + licensePlate + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", routeNumber='" + (routeNumber != null ? routeNumber : "UNASSIGNED") + '\'' +
                ", isActive=" + isActive +
                ", isInMotion=" + isInMotion +
                ", hasPosition=" + hasPosition() +
                '}';
    }
}
