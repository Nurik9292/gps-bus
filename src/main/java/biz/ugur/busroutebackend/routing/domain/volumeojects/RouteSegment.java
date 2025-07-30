package biz.ugur.busroutebackend.routing.domain.volumeojects;

import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;


@Log4j2
@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteSegment extends ValueObject {

    private final SegmentType type;
    private final Location fromLocation;
    private final Location toLocation;
    private final int durationMinutes;
    private final String routeNumber;
    private final String instruction;
    private final String detailedDescription;

    private  String routeGeometryWkt;
    private  Integer totalDistanceMeters;
    private  List<Double[]> walkingPath;

    public RouteSegment(SegmentType type, Location fromLocation, Location toLocation,
                        int durationMinutes, String routeNumber, String instruction) {
        this.type = type;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.durationMinutes = durationMinutes;
        this.routeNumber = routeNumber;
        this.instruction = instruction;
        this.detailedDescription = generateDetailedDescription();
    }

    public RouteSegment(SegmentType type, Location fromLocation, Location toLocation,
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

    public static RouteSegment walkingSegment(Location from, Location to, int minutes) {
        String instruction = String.format("Walk %d minutes to %s", minutes, to.getDescription());
        return new RouteSegment(SegmentType.WALKING, from, to, minutes, null, instruction);
    }

    public static RouteSegment busRideSegment(Location from, Location to, int minutes, String routeNumber) {
        String instruction = String.format("Take bus %s from %s to %s (%d min)",
                routeNumber, from.getDescription(), to.getDescription(), minutes);
        return new RouteSegment(SegmentType.BUS_RIDE, from, to, minutes, routeNumber, instruction);
    }

    public static RouteSegment transferSegment(Location transferLocation, int waitMinutes) {
        String instruction = String.format("Transfer at %s (wait %d min)",
                transferLocation.getDescription(), waitMinutes);
        return new RouteSegment(SegmentType.TRANSFER, transferLocation, transferLocation,
                waitMinutes, null, instruction);
    }

    public static RouteSegment busRideSegmentWithGeometry(Location from, Location to,
                                                          int durationMinutes, String routeNumber,
                                                          String routeGeometryWkt, Integer distanceMeters) {
        return new RouteSegment(
                SegmentType.BUS_RIDE,
                from,
                to,
                durationMinutes,
                routeNumber,
                String.format("Take bus %s from %s to %s (%d min)",
                        routeNumber, from.getDescription(), to.getDescription(), durationMinutes),
                routeGeometryWkt,
                distanceMeters
        );
    }

    private String generateDetailedDescription() {
        return switch (type) {
            case WALKING -> String.format("Walk %.0fm from %s to %s",
                    fromLocation.distanceTo(toLocation), fromLocation.getDescription(), toLocation.getDescription());
            case BUS_RIDE -> String.format("Bus route %s: %s → %s",
                    routeNumber, fromLocation.getDescription(), toLocation.getDescription());
            case TRANSFER -> String.format("Transfer at %s", fromLocation.getDescription());
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
                        coordinates.add(new Double[]{
                                Double.parseDouble(lonLat[0]),
                                Double.parseDouble(lonLat[1])
                        });
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