package biz.ugur.busroutebackend.admin.application.dto.dashboard;

public record DashboardStatistics(
        RouteStats routes,
        StopStats stops,
        VehicleStats vehicles,
        AdminStats admins,
        CityStats cities
) {

    public record RouteStats(long total, long active) {
        public static RouteStats empty() {
            return new RouteStats(0, 0);
        }
    }

    public record StopStats(long total, long active) {
        public static StopStats empty() {
            return new StopStats(0, 0);
        }
    }

    public record VehicleStats(long total, long active, long inMotion) {
        public static VehicleStats empty() {
            return new VehicleStats(0, 0, 0);
        }
    }

    public record AdminStats(long total, long active) {
        public static AdminStats empty() {
            return new AdminStats(0, 0);
        }
    }

    public record CityStats(long total, long active) {
        public static CityStats empty() {
            return new CityStats(0, 0);
        }
    }

    public static DashboardStatistics empty() {
        return new DashboardStatistics(
                RouteStats.empty(),
                StopStats.empty(),
                VehicleStats.empty(),
                AdminStats.empty(),
                CityStats.empty()
        );
    }
}
