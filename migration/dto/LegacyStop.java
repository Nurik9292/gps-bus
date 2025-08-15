package biz.ugur.busroutebackend.migration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LegacyStop {
    private Long id;
    private String name;
    private String locationWkt; // Geometry as WKT string
    private Integer cityId;

    public double getLatitude() {
        return parseCoordinatesFromWkt().getLatitude();
    }

    public double getLongitude() {
        return parseCoordinatesFromWkt().getLongitude();
    }

    private Coordinates parseCoordinatesFromWkt() {
        if (locationWkt == null || !locationWkt.startsWith("POINT")) {
            return new Coordinates(58.38, 37.95); // Ashgabat default
        }

        String coords = locationWkt.replaceAll("POINT\\(|\\)", "").trim();
        String[] parts = coords.split("\\s+");

        if (parts.length >= 2) {
            double longitude = Double.parseDouble(parts[0]);
            double latitude = Double.parseDouble(parts[1]);
            return new Coordinates(latitude, longitude);
        }

        return new Coordinates(58.38, 37.95); // Ashgabat default
    }

    @Data
    @AllArgsConstructor
    private static class Coordinates {
        private double latitude;
        private double longitude;
    }
}