package biz.ugur.busroutebackend.geospatial.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@EqualsAndHashCode(callSuper = false)
public class BoundingBox extends ValueObject {

    private final BigDecimal minLatitude;
    private final BigDecimal maxLatitude;
    private final BigDecimal minLongitude;
    private final BigDecimal maxLongitude;

    public BoundingBox(BigDecimal minLat, BigDecimal maxLat,
                       BigDecimal minLon, BigDecimal maxLon) {
        validateBounds(minLat, maxLat, minLon, maxLon);

        this.minLatitude = minLat;
        this.maxLatitude = maxLat;
        this.minLongitude = minLon;
        this.maxLongitude = maxLon;
    }

    public static BoundingBox of(Double minLat, Double maxLat, Double minLon, Double maxLon) {
        return new BoundingBox(
            BigDecimal.valueOf(minLat),
            BigDecimal.valueOf(maxLat),
            BigDecimal.valueOf(minLon),
            BigDecimal.valueOf(maxLon)
        );
    }

    public static BoundingBox of(BigDecimal minLat, BigDecimal maxLat,
                                 BigDecimal minLon, BigDecimal maxLon) {
        return new BoundingBox(minLat, maxLat, minLon, maxLon);
    }

    public static BoundingBox fromCenterAndRadius(Coordinates center, double radiusKm) {
        double latDegreesPerKm = 1.0 / 111.0;
        double lonDegreesPerKm = 1.0 / (111.0 * Math.cos(Math.toRadians(center.getLatitudeAsDouble())));

        double latDelta = radiusKm * latDegreesPerKm;
        double lonDelta = radiusKm * lonDegreesPerKm;

        return BoundingBox.of(
            center.getLatitude().subtract(BigDecimal.valueOf(latDelta)),
            center.getLatitude().add(BigDecimal.valueOf(latDelta)),
            center.getLongitude().subtract(BigDecimal.valueOf(lonDelta)),
            center.getLongitude().add(BigDecimal.valueOf(lonDelta))
        );
    }

    public boolean contains(Coordinates coordinates) {
        return coordinates.getLatitude().compareTo(minLatitude) >= 0 &&
               coordinates.getLatitude().compareTo(maxLatitude) <= 0 &&
               coordinates.getLongitude().compareTo(minLongitude) >= 0 &&
               coordinates.getLongitude().compareTo(maxLongitude) <= 0;
    }

    public boolean contains(BigDecimal latitude, BigDecimal longitude) {
        return latitude.compareTo(minLatitude) >= 0 &&
               latitude.compareTo(maxLatitude) <= 0 &&
               longitude.compareTo(minLongitude) >= 0 &&
               longitude.compareTo(maxLongitude) <= 0;
    }

    public void validate(Coordinates coordinates) {
        if (!contains(coordinates)) {
            throw new IllegalArgumentException(
                String.format("Coordinates %s are outside bounding box %s",
                    coordinates, this)
            );
        }
    }

    public boolean intersects(BoundingBox other) {
        return this.minLatitude.compareTo(other.maxLatitude) <= 0 &&
               this.maxLatitude.compareTo(other.minLatitude) >= 0 &&
               this.minLongitude.compareTo(other.maxLongitude) <= 0 &&
               this.maxLongitude.compareTo(other.minLongitude) >= 0;
    }

    public boolean containsBoundingBox(BoundingBox other) {
        return this.minLatitude.compareTo(other.minLatitude) <= 0 &&
               this.maxLatitude.compareTo(other.maxLatitude) >= 0 &&
               this.minLongitude.compareTo(other.minLongitude) <= 0 &&
               this.maxLongitude.compareTo(other.maxLongitude) >= 0;
    }

    public Coordinates getCenter() {
        BigDecimal centerLat = minLatitude.add(maxLatitude).divide(BigDecimal.valueOf(2));
        BigDecimal centerLon = minLongitude.add(maxLongitude).divide(BigDecimal.valueOf(2));
        return Coordinates.of(centerLat, centerLon);
    }

    public double getWidthDegrees() {
        return maxLongitude.subtract(minLongitude).doubleValue();
    }

    public double getHeightDegrees() {
        return maxLatitude.subtract(minLatitude).doubleValue();
    }

    public double getApproximateWidthKm() {
        double centerLat = getCenter().getLatitudeAsDouble();
        double kmPerDegreeLon = 111.32 * Math.cos(Math.toRadians(centerLat));
        return getWidthDegrees() * kmPerDegreeLon;
    }

    public double getApproximateHeightKm() {
        return getHeightDegrees() * 111.32; // km per degree of latitude
    }

    public double getApproximateAreaKm2() {
        return getApproximateWidthKm() * getApproximateHeightKm();
    }

    public BoundingBox expand(double distanceKm) {
        double latDegreesPerKm = 1.0 / 111.32;
        double centerLat = getCenter().getLatitudeAsDouble();
        double lonDegreesPerKm = 1.0 / (111.32 * Math.cos(Math.toRadians(centerLat)));

        double latDelta = distanceKm * latDegreesPerKm;
        double lonDelta = distanceKm * lonDegreesPerKm;

        return BoundingBox.of(
            minLatitude.subtract(BigDecimal.valueOf(latDelta)),
            maxLatitude.add(BigDecimal.valueOf(latDelta)),
            minLongitude.subtract(BigDecimal.valueOf(lonDelta)),
            maxLongitude.add(BigDecimal.valueOf(lonDelta))
        );
    }

    public BoundingBox union(BoundingBox other) {
        return BoundingBox.of(
            minLatitude.min(other.minLatitude),
            maxLatitude.max(other.maxLatitude),
            minLongitude.min(other.minLongitude),
            maxLongitude.max(other.maxLongitude)
        );
    }

    public Coordinates[] getCorners() {
        return new Coordinates[]{
            Coordinates.of(minLatitude, minLongitude),
            Coordinates.of(minLatitude, maxLongitude),
            Coordinates.of(maxLatitude, maxLongitude),
            Coordinates.of(maxLatitude, minLongitude)
        };
    }

    public String toWKT() {
        return String.format("POLYGON((%.6f %.6f, %.6f %.6f, %.6f %.6f, %.6f %.6f, %.6f %.6f))",
            minLongitude.doubleValue(), minLatitude.doubleValue(),
            maxLongitude.doubleValue(), minLatitude.doubleValue(),
            maxLongitude.doubleValue(), maxLatitude.doubleValue(),
            minLongitude.doubleValue(), maxLatitude.doubleValue(),
            minLongitude.doubleValue(), minLatitude.doubleValue()
        );
    }

    private void validateBounds(BigDecimal minLat, BigDecimal maxLat,
                                 BigDecimal minLon, BigDecimal maxLon) {
        if (minLat == null || maxLat == null || minLon == null || maxLon == null) {
            throw new IllegalArgumentException("Bounding box coordinates cannot be null");
        }

        if (minLat.compareTo(maxLat) >= 0) {
            throw new IllegalArgumentException(
                String.format("minLatitude (%.6f) must be less than maxLatitude (%.6f)",
                    minLat.doubleValue(), maxLat.doubleValue())
            );
        }

        if (minLon.compareTo(maxLon) >= 0) {
            throw new IllegalArgumentException(
                String.format("minLongitude (%.6f) must be less than maxLongitude (%.6f)",
                    minLon.doubleValue(), maxLon.doubleValue())
            );
        }

        // Validate global bounds
        if (minLat.compareTo(BigDecimal.valueOf(-90)) < 0 ||
            maxLat.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException(
                "Latitude must be between -90 and 90 degrees"
            );
        }

        if (minLon.compareTo(BigDecimal.valueOf(-180)) < 0 ||
            maxLon.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException(
                "Longitude must be between -180 and 180 degrees"
            );
        }
    }

    @Override
    public String toString() {
        return String.format("BoundingBox[lat: %.2f-%.2f, lon: %.2f-%.2f] (~%.1f×%.1f km)",
            minLatitude.doubleValue(), maxLatitude.doubleValue(),
            minLongitude.doubleValue(), maxLongitude.doubleValue(),
            getApproximateWidthKm(), getApproximateHeightKm()
        );
    }
}
