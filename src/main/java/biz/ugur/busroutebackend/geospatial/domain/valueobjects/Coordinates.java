package biz.ugur.busroutebackend.geospatial.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@EqualsAndHashCode(callSuper = false)
public class Coordinates extends ValueObject {

    private final BigDecimal latitude;
    private final BigDecimal longitude;

    private Coordinates(BigDecimal latitude, BigDecimal longitude) {
        validateGlobalBounds(latitude, longitude);

        this.latitude = latitude.setScale(6, RoundingMode.HALF_UP);
        this.longitude = longitude.setScale(6, RoundingMode.HALF_UP);
    }


    public static Coordinates of(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Latitude and longitude cannot be null");
        }
        return new Coordinates(latitude, longitude);
    }

    public static Coordinates of(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Latitude and longitude cannot be null");
        }
        return new Coordinates(
            BigDecimal.valueOf(latitude),
            BigDecimal.valueOf(longitude)
        );
    }

    public static Coordinates of(double latitude, double longitude) {
        return new Coordinates(
            BigDecimal.valueOf(latitude),
            BigDecimal.valueOf(longitude)
        );
    }

    public static Coordinates fromArray(double[] coords) {
        if (coords == null || coords.length != 2) {
            throw new IllegalArgumentException(
                "Coordinates array must contain exactly 2 elements [latitude, longitude]"
            );
        }
        return Coordinates.of(coords[0], coords[1]);
    }

    public static Coordinates fromGeoJson(double[] geoJsonCoords) {
        if (geoJsonCoords == null || geoJsonCoords.length != 2) {
            throw new IllegalArgumentException(
                "GeoJSON coordinates must contain exactly 2 elements [longitude, latitude]"
            );
        }
        return Coordinates.of(geoJsonCoords[1], geoJsonCoords[0]);
    }


    public double getLatitudeAsDouble() {
        return latitude.doubleValue();
    }

    public double getLongitudeAsDouble() {
        return longitude.doubleValue();
    }

    public double[] toArray() {
        return new double[]{getLatitudeAsDouble(), getLongitudeAsDouble()};
    }

    public double[] toGeoJson() {
        return new double[]{getLongitudeAsDouble(), getLatitudeAsDouble()};
    }

    public String toWKT() {
        return String.format("POINT(%.6f %.6f)",
            getLongitudeAsDouble(),
            getLatitudeAsDouble()
        );
    }

    public static Coordinates fromWKT(String wkt) {
        if (wkt == null || wkt.trim().isEmpty()) {
            throw new IllegalArgumentException("WKT string cannot be empty");
        }

        String trimmed = wkt.trim();
        if (!trimmed.startsWith("POINT(") || !trimmed.endsWith(")")) {
            throw new IllegalArgumentException(
                "Invalid WKT format. Expected: POINT(longitude latitude)"
            );
        }

        String coordsStr = trimmed.substring(6, trimmed.length() - 1).trim();
        String[] parts = coordsStr.split("\\s+");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                "Invalid WKT format. Expected 2 coordinates, found: " + parts.length
            );
        }

        try {
            double lon = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);
            return Coordinates.of(lat, lon);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinate values in WKT: " + coordsStr, e);
        }
    }


    public void validateWithinBounds(BoundingBox bounds) {
        bounds.validate(this);
    }

    public boolean isWithinBounds(BoundingBox bounds) {
        return bounds.contains(this);
    }

    private void validateGlobalBounds(BigDecimal lat, BigDecimal lon) {
        if (lat.compareTo(BigDecimal.valueOf(-90)) < 0 ||
            lat.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException(
                String.format("Latitude must be between -90 and 90 degrees, got: %.6f",
                    lat.doubleValue())
            );
        }

        if (lon.compareTo(BigDecimal.valueOf(-180)) < 0 ||
            lon.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException(
                String.format("Longitude must be between -180 and 180 degrees, got: %.6f",
                    lon.doubleValue())
            );
        }
    }

    public boolean isSameLocation(Coordinates other) {
        return isSameLocation(other, 10.0);
    }

    public boolean isSameLocation(Coordinates other, double toleranceMeters) {
        double latDiff = Math.abs(getLatitudeAsDouble() - other.getLatitudeAsDouble());
        double lonDiff = Math.abs(getLongitudeAsDouble() - other.getLongitudeAsDouble());

        double maxDegreeDiff = toleranceMeters / 111000.0;

        return latDiff <= maxDegreeDiff && lonDiff <= maxDegreeDiff;
    }

    public Coordinates offset(double northMeters, double eastMeters) {
        double latDegreesPerMeter = 1.0 / 111320.0;
        double lonDegreesPerMeter = 1.0 / (111320.0 * Math.cos(Math.toRadians(getLatitudeAsDouble())));

        double newLat = getLatitudeAsDouble() + (northMeters * latDegreesPerMeter);
        double newLon = getLongitudeAsDouble() + (eastMeters * lonDegreesPerMeter);

        return Coordinates.of(newLat, newLon);
    }

    public double bearingTo(Coordinates target) {
        double lat1 = Math.toRadians(getLatitudeAsDouble());
        double lat2 = Math.toRadians(target.getLatitudeAsDouble());
        double lon1 = Math.toRadians(getLongitudeAsDouble());
        double lon2 = Math.toRadians(target.getLongitudeAsDouble());

        double deltaLon = lon2 - lon1;

        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                   Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    public String getCardinalDirectionTo(Coordinates target) {
        double bearing = bearingTo(target);

        String[] directions = {
            "North", "Northeast", "East", "Southeast",
            "South", "Southwest", "West", "Northwest"
        };

        int index = (int) Math.round(bearing / 45.0) % 8;
        return directions[index];
    }

    @Override
    public String toString() {
        return String.format("Coordinates(%.6f°N, %.6f°E)",
            getLatitudeAsDouble(),
            getLongitudeAsDouble()
        );
    }

    public String toFormattedString(int decimalPlaces) {
        String format = String.format("%%.%df°N, %%.%df°E", decimalPlaces, decimalPlaces);
        return String.format(format,
            getLatitudeAsDouble(),
            getLongitudeAsDouble()
        );
    }

    public String toDMS() {
        return String.format("%s, %s",
            decimalToDMS(getLatitudeAsDouble(), true),
            decimalToDMS(getLongitudeAsDouble(), false)
        );
    }

    private String decimalToDMS(double decimal, boolean isLatitude) {
        char posDir = isLatitude ? 'N' : 'E';
        char negDir = isLatitude ? 'S' : 'W';
        char direction = decimal >= 0 ? posDir : negDir;

        decimal = Math.abs(decimal);
        int degrees = (int) decimal;
        double minutesDecimal = (decimal - degrees) * 60;
        int minutes = (int) minutesDecimal;
        double seconds = (minutesDecimal - minutes) * 60;

        return String.format("%d°%d'%.1f\"%c", degrees, minutes, seconds, direction);
    }
}
