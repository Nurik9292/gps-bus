package biz.ugur.busroutebackend.routing.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "business.routing.trip-scoring")
public class TripScoringProperties {

    private int maxInitialWaitingMinutes = 60;

    private QualityScore qualityScore = new QualityScore();
    private ComfortScore comfortScore = new ComfortScore();
    private ReliabilityScore reliabilityScore = new ReliabilityScore();

    @Getter
    @Setter
    public static class QualityScore {
        private double speedWeight = 0.4;
        private double transferWeight = 0.3;
        private double walkingWeight = 0.2;
        private double comfortWeight = 0.1;
        private int speedBaselineMinutes = 100;
        private int transferPenalty = 25;
        private int walkingPenaltyPerMinute = 3;
    }

    @Getter
    @Setter
    public static class ComfortScore {
        private double base = 100.0;
        private double transferPenalty = 15.0;
        private int walkingGraceMinutes = 5;
        private double walkingPenaltyPerMinute = 2.0;
        private int travelGraceMinutes = 30;
        private double travelPenaltyPerMinute = 0.5;
    }

    @Getter
    @Setter
    public static class ReliabilityScore {
        private double base = 0.95;
        private double transferPenalty = 0.05;
        private int walkingGraceMinutes = 10;
        private double walkingPenaltyPerMinute = 0.01;
        private double floor = 0.5;
    }
}
