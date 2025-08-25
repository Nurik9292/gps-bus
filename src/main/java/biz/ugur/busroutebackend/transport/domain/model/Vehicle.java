package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.transport.domain.event.VehicleAssignedToRouteEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehicleRegisteredEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehiclePosition;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("vehicles")
@Getter
@ToString
public class Vehicle extends AggregateRoot<Vehicle, VehicleId> {

    @Id
    @Column("id")
    private VehicleId id;

    @Setter
    @Column("device_id")
    private String deviceId; // GPS device ID from external API

    @Column("license_plate")
    private String licensePlate; // "1992 AGH"

    @Column("current_latitude")
    private Double currentLatitude;

    @Column("current_longitude")
    private Double currentLongitude;

    @Column("speed_kmh")
    private Double speedKmh;

    @Column("is_in_motion")
    private Boolean isInMotion;

    @Column("last_position_update")
    private Instant lastPositionUpdate;

    @Column("assigned_route_id")
    private BusRouteId assignedRouteId;

    @Column("route_number")
    private String routeNumber;

    @Column("is_active")
    private Boolean isActive;

    public Vehicle(String deviceId, String licensePlate) {
        this.id = VehicleId.generate();
        this.deviceId = validateDeviceId(deviceId);
        this.licensePlate = validateLicensePlate(licensePlate);
        this.isActive = true;
        this.isInMotion = false;
        this.speedKmh = 0.0;
        this.lastPositionUpdate = Instant.now();

        registerEvent(new VehicleRegisteredEvent(
                this.id.getValue(),
                this.deviceId,
                this.licensePlate
        ));
    }

    public Vehicle(VehicleId id, String deviceId, String licensePlate,
                   Double currentLatitude, Double currentLongitude,
                   Double speedKmh, Boolean isInMotion,
                   Instant lastPositionUpdate, BusRouteId assignedRouteId,
                   String routeNumber,
                   Boolean isActive) {
        this.id = id;
        this.deviceId = deviceId;
        this.licensePlate = licensePlate;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.speedKmh = speedKmh;
        this.isInMotion = isInMotion;
        this.lastPositionUpdate = lastPositionUpdate;
        this.assignedRouteId = assignedRouteId;
        this.isActive = isActive;
        this.routeNumber = routeNumber;
    }

    public void updatePosition(Double latitude, Double longitude, Double speed, Instant fixTime) {

        validateCoordinates(latitude, longitude);

        boolean positionChanged = hasPositionChangedSignificantly(latitude, longitude);

        this.currentLatitude = latitude;
        this.currentLongitude = longitude;
        this.speedKmh = speed != null ? speed : 0.0;
        this.isInMotion = this.speedKmh > 1.0;
        this.lastPositionUpdate = fixTime != null ? fixTime : Instant.now();

        if (positionChanged) {
            registerEvent(new VehiclePositionUpdatedEvent(
                    this.id.getValue(),
                    this.deviceId,
                    this.licensePlate,
                    this.routeNumber,
                    latitude,
                    longitude,
                    this.speedKmh,
                    this.isInMotion,
                    this.lastPositionUpdate
            ));
        }
    }


    public void assignToRoute(BusRouteId routeId) {
        if (routeId == null) {
            throw new IllegalArgumentException("Route ID cannot be null");
        }

        BusRouteId previousRoute = this.assignedRouteId;
        this.assignedRouteId = routeId;

        registerEvent(new VehicleAssignedToRouteEvent(
                this.id.getValue(),
                this.licensePlate,
                previousRoute != null ? previousRoute.getValue() : null,
                routeId.getValue()
        ));
    }

    public void unassignFromRoute() {
        if (this.assignedRouteId != null) {
            BusRouteId previousRoute = this.assignedRouteId;
            this.assignedRouteId = null;
            this.routeNumber = null;

            registerEvent(new VehicleAssignedToRouteEvent(
                    this.id.getValue(),
                    this.licensePlate,
                    previousRoute.getValue(),
                    null
            ));
        }
    }

    public void deactivate() {
        this.isActive = false;
        this.isInMotion = false;
        this.assignedRouteId = null;
    }

    public void updateCachedRouteNumber(String routeNumber) {
        this.routeNumber = routeNumber;
    }

    public String getDisplayRouteNumber() {
        return this.routeNumber != null ? this.routeNumber : "UNASSIGNED";
    }

    public boolean hasRecentPosition() {
        if (lastPositionUpdate == null) return false;
        return lastPositionUpdate.isAfter(Instant.now().minusSeconds(300));
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

    @Override
    public VehicleId getId() {
        return id;
    }


    private String validateDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID cannot be null or empty");
        }
        return deviceId.trim();
    }

    private String validateLicensePlate(String licensePlate) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty");
        }

        String plate = licensePlate.trim().toUpperCase();
        if (!plate.matches("\\d{4}\\s[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid Turkmen license plate format. Expected: '1992 AGH'");
        }
        return plate;
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }

        if (latitude < 35.0 || latitude > 43.0) {
            throw new IllegalArgumentException("Latitude outside Turkmenistan bounds");
        }
        if (longitude < 52.0 || longitude > 67.0) {
            throw new IllegalArgumentException("Longitude outside Turkmenistan bounds");
        }
    }

    private boolean hasPositionChangedSignificantly(Double newLat, Double newLon) {
        if (currentLatitude == null || currentLongitude == null) {
            return true;
        }

        double deltaLat = Math.abs(newLat - currentLatitude);
        double deltaLon = Math.abs(newLon - currentLongitude);

        return deltaLat > 0.0001 || deltaLon > 0.0001;
    }
}