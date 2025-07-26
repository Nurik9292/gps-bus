package biz.ugur.busroutebackend.transport.application.dto;

import biz.ugur.busroutebackend.interfaces.rest.transport.dto.response.BusStopArrivalsResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NearbyStopArrivalsResponse {
    private String stopId;
    private String stopName;
    private Double distanceMeters;
    private BusStopArrivalsResponse arrivals;

    public NearbyStopArrivalsResponse(String stopId,
                                      String stopName,
                                      Double distanceMeters,
                                      BusStopArrivalsResponse arrivals) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.distanceMeters = distanceMeters;
        this.arrivals = arrivals;
    }
}
