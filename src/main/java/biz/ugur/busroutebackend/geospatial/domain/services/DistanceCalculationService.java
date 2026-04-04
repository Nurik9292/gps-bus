package biz.ugur.busroutebackend.geospatial.domain.services;

import biz.ugur.busroutebackend.geospatial.domain.constants.GeoConstants;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Distance;


public class DistanceCalculationService {

    public Distance calculateDistance(Coordinates from, Coordinates to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }

        double distance = haversineDistance(
            from.getLatitudeAsDouble(),
            from.getLongitudeAsDouble(),
            to.getLatitudeAsDouble(),
            to.getLongitudeAsDouble()
        );

        return Distance.ofMeters(distance);
    }

    public Distance calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double distance = haversineDistance(lat1, lon1, lat2, lon2);
        return Distance.ofMeters(distance);
    }


    public static double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLatRad = Math.toRadians(lat2 - lat1);
        double deltaLonRad = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return GeoConstants.EARTH_RADIUS_METERS * c;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        return haversineDistanceMeters(lat1, lon1, lat2, lon2);
    }

    public boolean isWithinRadius(Coordinates from, Coordinates to, Distance radius) {
        Distance actualDistance = calculateDistance(from, to);
        return actualDistance.isWithinRadius(radius);
    }

    public boolean isWithinRadius(Coordinates from, Coordinates to, double radiusMeters) {
        return isWithinRadius(from, to, Distance.ofMeters(radiusMeters));
    }

    public boolean isNearby(Coordinates from, Coordinates to) {
        return isWithinRadius(from, to, GeoConstants.DEFAULT_SEARCH_RADIUS_METERS);
    }


    public int calculateWalkingTimeMinutes(Distance distance) {
        double minutes = distance.getMeters() / GeoConstants.AVERAGE_WALKING_SPEED_M_PER_MIN;
        return (int) Math.ceil(minutes);
    }

    public int calculateWalkingTimeMinutes(Coordinates from, Coordinates to) {
        Distance distance = calculateDistance(from, to);
        return calculateWalkingTimeMinutes(distance);
    }

    public int calculateUrbanWalkingTimeMinutes(Coordinates from, Coordinates to,
                                                 int urbanCorrectionMin) {
        int baseTime = calculateWalkingTimeMinutes(from, to);
        int correction = Math.max(
            GeoConstants.MIN_URBAN_CORRECTION_MINUTES,
            Math.min(urbanCorrectionMin, GeoConstants.MAX_URBAN_CORRECTION_MINUTES)
        );
        return baseTime + correction;
    }

    public boolean isWalkable(Distance distance) {
        int walkTime = calculateWalkingTimeMinutes(distance);
        return walkTime <= GeoConstants.REASONABLE_WALKING_TIME_MINUTES;
    }

    public boolean isWalkable(Coordinates from, Coordinates to) {
        Distance distance = calculateDistance(from, to);
        return isWalkable(distance);
    }


    public Coordinates findClosest(Coordinates from, Iterable<Coordinates> candidates) {
        Coordinates closest = null;
        Distance minDistance = null;

        for (Coordinates candidate : candidates) {
            Distance distance = calculateDistance(from, candidate);
            if (minDistance == null || distance.isLessThan(minDistance)) {
                minDistance = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    public Distance calculatePathDistance(Iterable<Coordinates> path) {
        double totalMeters = 0;
        Coordinates previous = null;
        int pointCount = 0;

        for (Coordinates current : path) {
            pointCount++;
            if (previous != null) {
                totalMeters += haversineDistance(
                    previous.getLatitudeAsDouble(),
                    previous.getLongitudeAsDouble(),
                    current.getLatitudeAsDouble(),
                    current.getLongitudeAsDouble()
                );
            }
            previous = current;
        }

        if (pointCount < 2) {
            throw new IllegalArgumentException(
                "Path must contain at least 2 points, found: " + pointCount
            );
        }

        return Distance.ofMeters(totalMeters);
    }


    public Distance calculatePointToLineDistance(Coordinates point,
                                                  Coordinates lineStart,
                                                  Coordinates lineEnd) {

        double lat1 = Math.toRadians(lineStart.getLatitudeAsDouble());
        double lon1 = Math.toRadians(lineStart.getLongitudeAsDouble());
        double lat2 = Math.toRadians(lineEnd.getLatitudeAsDouble());
        double lon2 = Math.toRadians(lineEnd.getLongitudeAsDouble());
        double latP = Math.toRadians(point.getLatitudeAsDouble());
        double lonP = Math.toRadians(point.getLongitudeAsDouble());

        double d13 = greatCircleDistance(lat1, lon1, latP, lonP);
        double d23 = greatCircleDistance(lat2, lon2, latP, lonP);
        double d12 = greatCircleDistance(lat1, lon1, lat2, lon2);

        if (d12 < 0.001) { 
            return Distance.ofMeters(d13 * GeoConstants.EARTH_RADIUS_METERS);
        }

        double bearing13 = bearing(lat1, lon1, latP, lonP);
        double bearing12 = bearing(lat1, lon1, lat2, lon2);
        double crossTrackDistance = Math.asin(
            Math.sin(d13) * Math.sin(bearing13 - bearing12)
        ) * GeoConstants.EARTH_RADIUS_METERS;

        double alongTrackDistance = Math.acos(
            Math.cos(d13) / Math.cos(crossTrackDistance / GeoConstants.EARTH_RADIUS_METERS)
        ) * GeoConstants.EARTH_RADIUS_METERS;

        double segmentLength = d12 * GeoConstants.EARTH_RADIUS_METERS;
        if (alongTrackDistance >= 0 && alongTrackDistance <= segmentLength) {
            return Distance.ofMeters(Math.abs(crossTrackDistance));
        } else {
            return Distance.ofMeters(Math.min(
                d13 * GeoConstants.EARTH_RADIUS_METERS,
                d23 * GeoConstants.EARTH_RADIUS_METERS
            ));
        }
    }

    private double greatCircleDistance(double lat1, double lon1, double lat2, double lon2) {
        double deltaLat = lat2 - lat1;
        double deltaLon = lon2 - lon1;

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double bearing(double lat1, double lon1, double lat2, double lon2) {
        double deltaLon = lon2 - lon1;

        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                   Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);

        return Math.atan2(y, x);
    }

    public double calculateBearing(Coordinates from, Coordinates to) {
        return from.bearingTo(to);
    }

    public String getCardinalDirection(Coordinates from, Coordinates to) {
        return from.getCardinalDirectionTo(to);
    }
}
