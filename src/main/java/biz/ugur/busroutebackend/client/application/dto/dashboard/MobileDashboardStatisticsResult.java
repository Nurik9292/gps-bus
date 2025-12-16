package biz.ugur.busroutebackend.client.application.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MobileDashboardStatisticsResult {

    private final long activeClients;
    private final long activeVehicles;

    public static MobileDashboardStatisticsResult fromStatistics(MobileDashboardStatistics stats) {
        return MobileDashboardStatisticsResult.builder()
                .activeClients(stats.activeClients())
                .activeVehicles(stats.activeVehicles())
                .build();
    }
}
