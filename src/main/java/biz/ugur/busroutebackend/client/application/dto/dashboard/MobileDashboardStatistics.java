package biz.ugur.busroutebackend.client.application.dto.dashboard;

public record MobileDashboardStatistics(
        long activeClients,
        long activeVehicles
) {
    public static MobileDashboardStatistics empty() {
        return new MobileDashboardStatistics(0, 0);
    }
}
