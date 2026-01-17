package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.Getter;

import java.util.Objects;

@Getter
public class StopWithRouteDistance extends ValueObject {

    private final String stopId;
    private final String stopName;
    private final Double latitude;
    private final Double longitude;
    private final Integer stopSequence;
    private final Double distanceOnRouteMeters;
    private final Double directDistanceMeters;
    private final Integer direction;

    public StopWithRouteDistance(
            String stopId,
            String stopName,
            Double latitude,
            Double longitude,
            Integer stopSequence,
            Double distanceOnRouteMeters,
            Double directDistanceMeters,
            Integer direction) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.stopSequence = stopSequence;
        this.distanceOnRouteMeters = distanceOnRouteMeters;
        this.directDistanceMeters = directDistanceMeters;
        this.direction = direction;
    }

    public double getEffectiveDistance(double correctionFactor) {
        if (hasRouteDistance()) {
            return distanceOnRouteMeters;
        }
        return directDistanceMeters != null ? directDistanceMeters * correctionFactor : 0.0;
    }

    public boolean hasRouteDistance() {
        return distanceOnRouteMeters != null && distanceOnRouteMeters > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StopWithRouteDistance that)) return false;
        return Objects.equals(stopId, that.stopId) &&
                Objects.equals(stopName, that.stopName) &&
                Objects.equals(latitude, that.latitude) &&
                Objects.equals(longitude, that.longitude) &&
                Objects.equals(stopSequence, that.stopSequence) &&
                Objects.equals(distanceOnRouteMeters, that.distanceOnRouteMeters) &&
                Objects.equals(directDistanceMeters, that.directDistanceMeters) &&
                Objects.equals(direction, that.direction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopId, stopName, latitude, longitude,
                stopSequence, distanceOnRouteMeters, directDistanceMeters, direction);
    }

    @Override
    public String toString() {
        return "StopWithRouteDistance{" +
                "stopId='" + stopId + '\'' +
                ", stopName='" + stopName + '\'' +
                ", stopSequence=" + stopSequence +
                ", distanceOnRouteMeters=" + distanceOnRouteMeters +
                ", directDistanceMeters=" + directDistanceMeters +
                ", direction=" + direction +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String stopId;
        private String stopName;
        private Double latitude;
        private Double longitude;
        private Integer stopSequence;
        private Double distanceOnRouteMeters;
        private Double directDistanceMeters;
        private Integer direction;

        public Builder stopId(String stopId) {
            this.stopId = stopId;
            return this;
        }

        public Builder stopName(String stopName) {
            this.stopName = stopName;
            return this;
        }

        public Builder latitude(Double latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(Double longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder stopSequence(Integer stopSequence) {
            this.stopSequence = stopSequence;
            return this;
        }

        public Builder distanceOnRouteMeters(Double distanceOnRouteMeters) {
            this.distanceOnRouteMeters = distanceOnRouteMeters;
            return this;
        }

        public Builder directDistanceMeters(Double directDistanceMeters) {
            this.directDistanceMeters = directDistanceMeters;
            return this;
        }

        public Builder direction(Integer direction) {
            this.direction = direction;
            return this;
        }

        public StopWithRouteDistance build() {
            return new StopWithRouteDistance(
                    stopId, stopName, latitude, longitude,
                    stopSequence, distanceOnRouteMeters, directDistanceMeters, direction
            );
        }
    }
}
