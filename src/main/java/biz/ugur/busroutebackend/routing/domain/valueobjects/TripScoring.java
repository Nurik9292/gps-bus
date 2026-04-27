package biz.ugur.busroutebackend.routing.domain.valueobjects;

public record TripScoring(
        int maxInitialWaitingMinutes,
        QualityCoefficients quality,
        ComfortCoefficients comfort,
        ReliabilityCoefficients reliability
) {

    public record QualityCoefficients(
            double speedWeight,
            double transferWeight,
            double walkingWeight,
            double comfortWeight,
            int speedBaselineMinutes,
            int transferPenalty,
            int walkingPenaltyPerMinute
    ) {}

    public record ComfortCoefficients(
            double base,
            double transferPenalty,
            int walkingGraceMinutes,
            double walkingPenaltyPerMinute,
            int travelGraceMinutes,
            double travelPenaltyPerMinute
    ) {}

    public record ReliabilityCoefficients(
            double base,
            double transferPenalty,
            int walkingGraceMinutes,
            double walkingPenaltyPerMinute,
            double floor
    ) {}

    public static TripScoring defaults() {
        return new TripScoring(
                60,
                new QualityCoefficients(0.4, 0.3, 0.2, 0.1, 100, 25, 3),
                new ComfortCoefficients(100.0, 15.0, 5, 2.0, 30, 0.5),
                new ReliabilityCoefficients(0.95, 0.05, 10, 0.01, 0.5)
        );
    }
}
