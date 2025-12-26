package biz.ugur.busroutebackend.routing.domain.constants;


public final class TripScoringConstants {

    private TripScoringConstants() {}

    public static final double SPEED_SCORE_WEIGHT = 0.4;
    public static final double TRANSFER_SCORE_WEIGHT = 0.3;
    public static final double WALKING_SCORE_WEIGHT = 0.2;
    public static final double COMFORT_SCORE_WEIGHT = 0.1;

    public static final int BASE_TRANSFER_PENALTY = 25;
    public static final int BASE_WALKING_PENALTY_MULTIPLIER = 3;

    public static final double BASE_RELIABILITY = 0.95;
    public static final double TRANSFER_RELIABILITY_PENALTY = 0.05;

    public static final int MAX_WALKING_MINUTES = 10;
    public static final double WALKING_PENALTY_PER_MINUTE = 0.01;
    public static final int MAX_TRANSFER_PENALTY_THRESHOLD = 1;
}
