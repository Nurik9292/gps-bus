package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;

import biz.ugur.busroutebackend.client.application.dto.dashboard.MobileDashboardStatisticsResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MobileDashboardStatisticsResponse {

    private final long activeClients;

    private final long activeVehicles;

    public static MobileDashboardStatisticsResponse fromResult(MobileDashboardStatisticsResult result) {
        return MobileDashboardStatisticsResponse.builder()
                .activeClients(result.getActiveClients())
                .activeVehicles(result.getActiveVehicles())
                .build();
    }
}
