package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusStopArrivalsResponse {
    private String stopId;
    private String stopName;
    private Double latitude;
    private Double longitude;
    private List<BusArrivalInfo> arrivals;
    private LocalDateTime lastUpdated;

    public BusStopArrivalsResponse() {}

    public BusStopArrivalsResponse(String stopId, String stopName, Double latitude, Double longitude,
                                   List<BusArrivalInfo> arrivals, LocalDateTime lastUpdated) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.arrivals = arrivals != null ? arrivals : List.of();
        this.lastUpdated = lastUpdated;
    }

    public BusArrivalInfo getNextBus() {
        return arrivals.stream()
                .filter(bus -> "approaching".equals(bus.getArrivalStatus()))
                .min((b1, b2) -> Integer.compare(b1.getEstimatedArrivalMinutes(), b2.getEstimatedArrivalMinutes()))
                .orElse(null);
    }

    public List<BusArrivalInfo> getBusesWithinMinutes(int minutes) {
        return arrivals.stream()
                .filter(bus -> bus.getEstimatedArrivalMinutes() <= minutes)
                .sorted((b1, b2) -> Integer.compare(b1.getEstimatedArrivalMinutes(), b2.getEstimatedArrivalMinutes()))
                .collect(Collectors.toList());
    }

}