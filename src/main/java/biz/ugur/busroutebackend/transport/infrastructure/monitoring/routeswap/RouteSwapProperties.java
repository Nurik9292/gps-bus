package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "business.route-swap-detector")
public class RouteSwapProperties {

    public enum Mode {
        OFF, SHADOW, LIVE
    }

    private boolean enabled = false;
    private Mode mode = Mode.OFF;
    private int windowMinutes = 10;
    private int verdictWindows = 6;
    private int minWindowsForVerdict = 5;
    private int minMovingPointsPerWindow = 3;
    private double minSpeedKmh = 5.0;
    private double onRouteMeters = 100.0;
    private double offAssignedMeters = 300.0;
    private double competitorClearanceMeters = 250.0;
    private double swapAssignedCoverageMax = 0.3;
    private double swapForeignCoverageMin = 0.6;
    private int uniqueMinutesMin = 10;
    private int assignmentGraceMinutes = 60;
    private double familyMutualShare = 0.6;
    private double overlapToleranceMeters = 120.0;
    private int digestIntervalMinutes = 30;
    private String recipients = "";

    public List<String> recipientList() {
        if (recipients == null || recipients.isBlank()) {
            return List.of();
        }
        return Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
