 package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.dashboard;

import biz.ugur.busroutebackend.admin.application.dto.dashboard.DashboardStatisticsResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardStatisticsResponse {

    @JsonProperty("total_routes")
    private final long totalRoutes;

    @JsonProperty("active_routes")
    private final long activeRoutes;

    @JsonProperty("total_stops")
    private final long totalStops;

    @JsonProperty("active_stops")
    private final long activeStops;

    @JsonProperty("total_vehicles")
    private final long totalVehicles;

    @JsonProperty("active_vehicles")
    private final long activeVehicles;

    @JsonProperty("vehicles_in_motion")
    private final long vehiclesInMotion;

    @JsonProperty("total_admins")
    private final long totalAdmins;

    @JsonProperty("active_admins")
    private final long activeAdmins;

    @JsonProperty("total_cities")
    private final long totalCities;

    @JsonProperty("active_cities")
    private final long activeCities;

    public static DashboardStatisticsResponse fromResult(DashboardStatisticsResult result) {
        return DashboardStatisticsResponse.builder()
                .totalRoutes(result.getTotalRoutes())
                .activeRoutes(result.getActiveRoutes())
                .totalStops(result.getTotalStops())
                .activeStops(result.getActiveStops())
                .totalVehicles(result.getTotalVehicles())
                .activeVehicles(result.getActiveVehicles())
                .vehiclesInMotion(result.getVehiclesInMotion())
                .totalAdmins(result.getTotalAdmins())
                .activeAdmins(result.getActiveAdmins())
                .totalCities(result.getTotalCities())
                .activeCities(result.getActiveCities())
                .build();
    }
}
