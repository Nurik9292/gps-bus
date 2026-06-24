package biz.ugur.busroutebackend.routing.domain.valueobjects;

import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Log4j2
@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteSegment extends ValueObject {

    private final SegmentType type;
    private final Coordinates fromLocation;
    private final Coordinates toLocation;
    private final int durationMinutes;
    private final String routeNumber;
    private final String instruction;
    private final String detailedDescription;

    private  String routeGeometryWkt;
    private  Integer totalDistanceMeters;
    private  List<Double[]> walkingPath;
    private  List<List<Double>> walkingGeometry;

    @lombok.Setter
    private String fromLocationName;
    @lombok.Setter
    private String toLocationName;

    @lombok.Setter
    private String fromStopId;
    @lombok.Setter
    private String toStopId;

    public RouteSegment(SegmentType type, Coordinates fromLocation, Coordinates toLocation,
                        int durationMinutes, String routeNumber, String instruction) {
        this.type = type;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.durationMinutes = durationMinutes;
        this.routeNumber = routeNumber;
        this.instruction = instruction;
        this.detailedDescription = generateDetailedDescription();
    }

    public RouteSegment(SegmentType type, Coordinates fromLocation, Coordinates toLocation,
                        int durationMinutes, String routeNumber, String instruction,
                        String routeGeometryWkt, Integer totalDistanceMeters) {
        this.type = type;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.durationMinutes = durationMinutes;
        this.routeNumber = routeNumber;
        this.instruction = instruction;
        this.routeGeometryWkt = routeGeometryWkt;
        this.totalDistanceMeters = totalDistanceMeters;
        this.walkingPath = null;

        this.detailedDescription = generateDetailedDescription();
    }

    public RouteSegment(SegmentType type, Coordinates fromLocation, Coordinates toLocation,
                        int durationMinutes, String routeNumber, String instruction,
                        List<List<Double>> walkingGeometry, Integer totalDistanceMeters) {
        this.type = type;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.durationMinutes = durationMinutes;
        this.routeNumber = routeNumber;
        this.instruction = instruction;
        this.walkingGeometry = walkingGeometry != null ? Collections.unmodifiableList(walkingGeometry) : null;
        this.totalDistanceMeters = totalDistanceMeters;
        this.walkingPath = null;
        this.detailedDescription = generateDetailedDescription();
    }

    public static RouteSegment walkingSegment(Coordinates from, Coordinates to, int minutes) {
        String instruction = String.format("Walk %d minutes", minutes);
        return new RouteSegment(SegmentType.WALKING, from, to, minutes, null, instruction);
    }

    public static RouteSegment walkingSegmentWithGeometry(Coordinates from, Coordinates to, int minutes,
                                                          List<List<Double>> geometry, int distanceMeters) {
        String instruction = String.format("Walk %d minutes", minutes);
        return new RouteSegment(SegmentType.WALKING, from, to, minutes, null, instruction,
                geometry, distanceMeters);
    }

    public static List<List<Double>> straightLineGeometry(Coordinates from, Coordinates to) {
        return List.of(
                List.of(from.getLatitudeAsDouble(), from.getLongitudeAsDouble()),
                List.of(to.getLatitudeAsDouble(), to.getLongitudeAsDouble()));
    }

    public static RouteSegment busRideSegment(Coordinates from, Coordinates to, int minutes, String routeNumber) {
        String instruction = String.format("Take bus %s (%d min)", routeNumber, minutes);
        return new RouteSegment(SegmentType.BUS_RIDE, from, to, minutes, routeNumber, instruction);
    }

    public static RouteSegment transferSegment(Coordinates transferLocation, int waitMinutes) {
        String instruction = String.format("Transfer (wait %d min)", waitMinutes);
        return new RouteSegment(SegmentType.TRANSFER, transferLocation, transferLocation,
                waitMinutes, null, instruction);
    }

    public static RouteSegment busRideSegmentWithGeometry(Coordinates from, Coordinates to,
                                                          int durationMinutes, String routeNumber,
                                                          String routeGeometryWkt, Integer distanceMeters) {
        return new RouteSegment(
                SegmentType.BUS_RIDE,
                from,
                to,
                durationMinutes,
                routeNumber,
                String.format("Take bus %s (%d min)", routeNumber, durationMinutes),
                routeGeometryWkt,
                distanceMeters
        );
    }

    private String generateDetailedDescription() {
        DistanceCalculationService distanceService = new DistanceCalculationService();

        return switch (type) {
            case WALKING -> {
                double distance = distanceService.calculateDistance(
                    fromLocation.getLatitudeAsDouble(),
                    fromLocation.getLongitudeAsDouble(),
                    toLocation.getLatitudeAsDouble(),
                    toLocation.getLongitudeAsDouble()
                ).getMeters();
                yield String.format("Walk %.0fm", distance);
            }
            case BUS_RIDE -> String.format("Bus route %s", routeNumber);
            case TRANSFER -> "Transfer";
        };
    }

    public List<Double[]> getGeoJsonCoordinates() {
        if (routeGeometryWkt == null) return null;

        return parseWKTToGeoJSON(routeGeometryWkt);
    }


    private List<Double[]> parseWKTToGeoJSON(String wkt) {
        List<Double[]> coordinates = new ArrayList<>();

        if (wkt != null && wkt.startsWith("LINESTRING(")) {
            String coordsStr = wkt.substring(11, wkt.length() - 1);
            String[] points = coordsStr.split(",");

            for (String point : points) {
                String[] lonLat = point.trim().split(" ");
                if (lonLat.length == 2) {
                    try {
                        double longitude = Double.parseDouble(lonLat[0]);
                        double latitude = Double.parseDouble(lonLat[1]);
                        coordinates.add(new Double[]{latitude, longitude});
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse coordinate: {}", point);
                    }
                }
            }
        }

        return coordinates;
    }

    @Override
    public String toString() {
        return String.format("%s[%d min]: %s", type, durationMinutes, instruction);
    }
}