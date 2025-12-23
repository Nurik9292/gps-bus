package biz.ugur.busroutebackend.geospatial.domain.model;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.valueobject.GarageId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Garage aggregate root representing a vehicle depot with geofence zone
 */
@Getter
public class Garage extends AggregateRoot<Garage, GarageId> {

    private final GarageId id;
    private final String name;
    private final String nameTm;
    private final String nameEn;
    private final Coordinates location;
    private final Integer radiusMeters;
    private final String cityId;
    private final Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    private Garage(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.nameTm = builder.nameTm;
        this.nameEn = builder.nameEn;
        this.location = builder.location;
        this.radiusMeters = builder.radiusMeters;
        this.cityId = builder.cityId;
        this.isActive = builder.isActive;
    }

    /**
     * Create a new Garage
     */
    public static Garage create(
            String name,
            String nameTm,
            String nameEn,
            Coordinates location,
            Integer radiusMeters,
            String cityId
    ) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(radiusMeters, "radiusMeters must not be null");

        if (radiusMeters <= 0 || radiusMeters > 1000) {
            throw new IllegalArgumentException("radiusMeters must be between 1 and 1000");
        }

        return new Builder()
                .id(new GarageId())
                .name(name)
                .nameTm(nameTm)
                .nameEn(nameEn)
                .location(location)
                .radiusMeters(radiusMeters)
                .cityId(cityId)
                .isActive(true)
                .build();
    }

    /**
     * Check if a vehicle position is inside the garage geofence zone
     *
     * @param vehiclePosition the current position of the vehicle
     * @return true if vehicle is inside the garage zone
     */
    public boolean isVehicleInside(Coordinates vehiclePosition) {
        Objects.requireNonNull(vehiclePosition, "vehiclePosition must not be null");

        double distanceMeters = calculateHaversineDistance(
                this.location.getLatitudeAsDouble(),
                this.location.getLongitudeAsDouble(),
                vehiclePosition.getLatitudeAsDouble(),
                vehiclePosition.getLongitudeAsDouble()
        );

        return distanceMeters <= this.radiusMeters;
    }

    /**
     * Check if vehicle has exited the garage zone (with additional buffer)
     *
     * @param vehiclePosition current position
     * @param bufferMeters additional buffer distance in meters
     * @return true if vehicle is outside garage zone + buffer
     */
    public boolean hasVehicleExited(Coordinates vehiclePosition, int bufferMeters) {
        Objects.requireNonNull(vehiclePosition, "vehiclePosition must not be null");

        double distanceMeters = calculateHaversineDistance(
                this.location.getLatitudeAsDouble(),
                this.location.getLongitudeAsDouble(),
                vehiclePosition.getLatitudeAsDouble(),
                vehiclePosition.getLongitudeAsDouble()
        );

        return distanceMeters > (this.radiusMeters + bufferMeters);
    }

    /**
     * Calculate distance from garage center to a position
     *
     * @param position the position to measure distance to
     * @return distance in meters
     */
    public double calculateDistanceMeters(Coordinates position) {
        Objects.requireNonNull(position, "position must not be null");
        return calculateHaversineDistance(
                this.location.getLatitudeAsDouble(),
                this.location.getLongitudeAsDouble(),
                position.getLatitudeAsDouble(),
                position.getLongitudeAsDouble()
        );
    }

    /**
     * Calculate Haversine distance between two points
     *
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in meters
     */
    private static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_METERS = 6371000.0;

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLatRad = Math.toRadians(lat2 - lat1);
        double deltaLonRad = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    @Override
    public GarageId getId() {
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

    public static class Builder {
        private GarageId id;
        private String name;
        private String nameTm;
        private String nameEn;
        private Coordinates location;
        private Integer radiusMeters;
        private String cityId;
        private Boolean isActive;

        public Builder id(GarageId id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder nameTm(String nameTm) {
            this.nameTm = nameTm;
            return this;
        }

        public Builder nameEn(String nameEn) {
            this.nameEn = nameEn;
            return this;
        }

        public Builder location(Coordinates location) {
            this.location = location;
            return this;
        }

        public Builder radiusMeters(Integer radiusMeters) {
            this.radiusMeters = radiusMeters;
            return this;
        }

        public Builder cityId(String cityId) {
            this.cityId = cityId;
            return this;
        }

        public Builder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Garage build() {
            return new Garage(this);
        }
    }
}
