package biz.ugur.busroutebackend.admin.application.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardStatisticsResult {

    private final long totalRoutes;
    private final long activeRoutes;
    private final long totalStops;
    private final long activeStops;
    private final long totalVehicles;
    private final long activeVehicles;
    private final long vehiclesInMotion;
    private final long totalAdmins;
    private final long activeAdmins;
    private final long totalCities;
    private final long activeCities;

    public static DashboardStatisticsResult fromStatistics(DashboardStatistics stats) {
        return DashboardStatisticsResult.builder()
                .totalRoutes(stats.routes().total())
                .activeRoutes(stats.routes().active())
                .totalStops(stats.stops().total())
                .activeStops(stats.stops().active())
                .totalVehicles(stats.vehicles().total())
                .activeVehicles(stats.vehicles().active())
                .vehiclesInMotion(stats.vehicles().inMotion())
                .totalAdmins(stats.admins().total())
                .activeAdmins(stats.admins().active())
                .totalCities(stats.cities().total())
                .activeCities(stats.cities().active())
                .build();
    }
}
