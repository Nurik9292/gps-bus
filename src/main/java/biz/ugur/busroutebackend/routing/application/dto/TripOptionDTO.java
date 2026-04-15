package biz.ugur.busroutebackend.routing.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TripOptionDTO {

    @JsonProperty("option_id")
    private String optionId;

    @JsonProperty("trip_type")
    private String tripType;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("total_travel_minutes")
    private int totalTravelMinutes;

    @JsonProperty("total_walking_minutes")
    private int totalWalkingMinutes;

    @JsonProperty("transfers_count")
    private int transfersCount;

    @JsonProperty("estimated_departure")
    private LocalDateTime estimatedDeparture;

    @JsonProperty("estimated_arrival")
    private LocalDateTime estimatedArrival;

    @JsonProperty("route_segments")
    private List<RouteSegmentDTO> routeSegments;

    @JsonProperty("cost_estimate")
    private CostEstimate costEstimate;

    @JsonProperty("nearest_bus")
    private NearestBusDTO nearestBus;

    @Data
    public static class NearestBusDTO {
        @JsonProperty("vehicle_id")
        private final String vehicleId;
        @JsonProperty("license_plate")
        private final String licensePlate;
        @JsonProperty("route_number")
        private final String routeNumber;
        @JsonProperty("eta_minutes")
        private final int etaMinutes;
        @JsonProperty("distance_meters")
        private final int distanceMeters;
    }

    public TripOptionDTO(String optionId, String tripType, String summary,
                         int totalTravelMinutes, int totalWalkingMinutes,
                         int transfersCount, List<RouteSegmentDTO> routeSegments) {
        this.optionId = optionId;
        this.tripType = tripType;
        this.summary = summary;
        this.totalTravelMinutes = totalTravelMinutes;
        this.totalWalkingMinutes = totalWalkingMinutes;
        this.transfersCount = transfersCount;
        this.routeSegments = routeSegments;
        this.estimatedDeparture = LocalDateTime.now().plusMinutes(5);
        this.estimatedArrival = estimatedDeparture.plusMinutes(totalTravelMinutes);
        this.costEstimate = new CostEstimate(transfersCount + 1);
    }

    @Data
    public static class CostEstimate {
        @JsonProperty("total_fare_manat")
        private double totalFareManat;

        @JsonProperty("number_of_fares")
        private int numberOfFares;

        public CostEstimate(int numberOfFares) {
            this.numberOfFares = numberOfFares;
            this.totalFareManat = numberOfFares * 1.0;
        }
    }
}