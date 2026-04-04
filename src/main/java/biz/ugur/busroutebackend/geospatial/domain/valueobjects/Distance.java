package biz.ugur.busroutebackend.geospatial.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import biz.ugur.busroutebackend.geospatial.domain.constants.GeoConstants;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class Distance extends ValueObject implements Comparable<Distance> {

    private final double meters;

    private Distance(double meters) {
        if (meters < 0) {
            throw new IllegalArgumentException(
                String.format("Distance cannot be negative: %.2f meters", meters)
            );
        }
        if (Double.isNaN(meters) || Double.isInfinite(meters)) {
            throw new IllegalArgumentException("Distance must be a finite number");
        }
        this.meters = meters;
    }


    public static Distance ofMeters(double meters) {
        return new Distance(meters);
    }

    public static Distance ofKilometers(double kilometers) {
        return new Distance(kilometers * GeoConstants.METERS_PER_KILOMETER);
    }

    public static Distance ofMiles(double miles) {
        return new Distance(miles * GeoConstants.METERS_PER_MILE);
    }

    public static Distance zero() {
        return new Distance(0.0);
    }

    public double toKilometers() {
        return meters / GeoConstants.METERS_PER_KILOMETER;
    }

    public double toMiles() {
        return meters / GeoConstants.METERS_PER_MILE;
    }

    public double toCentimeters() {
        return meters * 100.0;
    }

    public double toFeet() {
        return meters * 3.28084;
    }

    public int toMetersInt() {
        return (int) Math.round(meters);
    }

    public boolean isLessThan(Distance other) {
        return this.meters < other.meters;
    }

    public boolean isLessThanOrEqual(Distance other) {
        return this.meters <= other.meters;
    }

    public boolean isGreaterThan(Distance other) {
        return this.meters > other.meters;
    }

    public boolean isGreaterThanOrEqual(Distance other) {
        return this.meters >= other.meters;
    }

    public boolean isWithinRadius(Distance radius) {
        return this.meters <= radius.meters;
    }

    public boolean isNegligible() {
        return meters < 1.0;
    }

    public boolean isWalkable() {
        return isWalkable(2000.0); 
    }

    public boolean isWalkable(double maxWalkableMeters) {
        return meters <= maxWalkableMeters;
    }

    public Distance add(Distance other) {
        return Distance.ofMeters(this.meters + other.meters);
    }

    public Distance subtract(Distance other) {
        double result = this.meters - other.meters;
        if (result < 0) {
            throw new IllegalArgumentException(
                String.format("Cannot subtract %.2fm from %.2fm (would be negative)",
                    other.meters, this.meters)
            );
        }
        return Distance.ofMeters(result);
    }

    public Distance multiply(double factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Factor cannot be negative: " + factor);
        }
        return Distance.ofMeters(this.meters * factor);
    }

    public Distance divide(double divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("Divisor must be positive: " + divisor);
        }
        return Distance.ofMeters(this.meters / divisor);
    }

    public Distance min(Distance other) {
        return this.meters <= other.meters ? this : other;
    }

    public Distance max(Distance other) {
        return this.meters >= other.meters ? this : other;
    }

    public int toWalkingTimeMinutes() {
        return (int) Math.ceil(meters / GeoConstants.AVERAGE_WALKING_SPEED_M_PER_MIN);
    }

    public int toWalkingTimeMinutes(double speedKmh) {
        double speedMPerMin = (speedKmh * 1000.0) / 60.0;
        return (int) Math.ceil(meters / speedMPerMin);
    }

    public int toTravelTimeMinutes(double speedKmh) {
        double hours = toKilometers() / speedKmh;
        return (int) Math.ceil(hours * 60.0);
    }

    @Override
    public String toString() {
        if (meters < 1000) {
            return String.format("%.0fm", meters);
        } else if (meters < 10000) {
            return String.format("%.1fkm", toKilometers());
        } else {
            return String.format("%.0fkm", toKilometers());
        }
    }

    public String toFormattedString(int decimalPlaces) {
        String format;
        if (meters < 1000) {
            format = String.format("%%.%dfm", decimalPlaces);
            return String.format(format, meters);
        } else {
            format = String.format("%%.%dfkm", decimalPlaces);
            return String.format(format, toKilometers());
        }
    }

    public String toStringWithWalkingTime() {
        int walkMinutes = toWalkingTimeMinutes();
        if (walkMinutes <= 1) {
            return toString();
        }
        return String.format("%s (~%d min walk)", toString(), walkMinutes);
    }

    public String toMilesString() {
        double miles = toMiles();
        if (miles < 0.1) {
            return String.format("%.0f ft", toFeet());
        } else {
            return String.format("%.1f mi", miles);
        }
    }


    @Override
    public int compareTo(Distance other) {
        return Double.compare(this.meters, other.meters);
    }

    public static Distance sum(Distance... distances) {
        double total = 0;
        for (Distance d : distances) {
            total += d.meters;
        }
        return Distance.ofMeters(total);
    }

    public static Distance min(Distance... distances) {
        if (distances.length == 0) {
            throw new IllegalArgumentException("At least one distance required");
        }
        Distance min = distances[0];
        for (int i = 1; i < distances.length; i++) {
            if (distances[i].meters < min.meters) {
                min = distances[i];
            }
        }
        return min;
    }

    public static Distance max(Distance... distances) {
        if (distances.length == 0) {
            throw new IllegalArgumentException("At least one distance required");
        }
        Distance max = distances[0];
        for (int i = 1; i < distances.length; i++) {
            if (distances[i].meters > max.meters) {
                max = distances[i];
            }
        }
        return max;
    }

    public static Distance average(Distance... distances) {
        if (distances.length == 0) {
            throw new IllegalArgumentException("At least one distance required");
        }
        double total = 0;
        for (Distance d : distances) {
            total += d.meters;
        }
        return Distance.ofMeters(total / distances.length);
    }
}
