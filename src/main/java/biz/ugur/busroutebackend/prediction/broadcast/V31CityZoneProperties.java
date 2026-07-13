package biz.ugur.busroutebackend.prediction.broadcast;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.prediction.v31.cityzone")
public class V31CityZoneProperties {

    private Map<String, Zone> routes = Map.of();

    @Getter
    @Setter
    public static class Zone {
        private double dLat;
        private double dLon;
        private double pLat;
        private double pLon;
        private double rDeep = 300;
        private double rPlateau = 900;
        private double tDwellSec = 120;
        private int m = 5;
        private double gSec = 600;
        private double tCityExitMinSpanSec = 30;
        private int kExit = 5;
        private double deltaRMeters = 150;
        private double tPostBoundaryGuardSec = 300;
        private int kConfirmPostBoundary = 2;
        private double epsMidlineMeters = 300;
    }
}
