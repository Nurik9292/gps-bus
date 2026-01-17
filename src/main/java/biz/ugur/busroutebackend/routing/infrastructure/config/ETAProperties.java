package biz.ugur.busroutebackend.routing.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.eta")
public class ETAProperties {

    private PositionConfig position = new PositionConfig();
    private SpeedConfig speed = new SpeedConfig();
    private CacheConfig cache = new CacheConfig();
    private TrafficMultiplierConfig traffic = new TrafficMultiplierConfig();
    private StopConfig stop = new StopConfig();
    private FallbackConfig fallback = new FallbackConfig();
    private TransferConfig transfer = new TransferConfig();

    @Getter
    @Setter
    public static class PositionConfig {

        private int maxAgeMinutes = 10;

        private int atStopDistanceMeters = 200;

        private int maxEtaMinutes = 60;
    }

    @Getter
    @Setter
    public static class SpeedConfig {

        private double movingThresholdKmh = 5.0;

        private double morningRushKmh = 12.0;

        private double eveningRushKmh = 12.0;

        private double lunchTimeKmh = 18.0;

        private double normalKmh = 25.0;

        private double nightKmh = 30.0;
    }

    @Getter
    @Setter
    public static class CacheConfig {

        private int stopArrivalsTtlSeconds = 10;

        private int streamIntervalSeconds = 10;

        private int waitingTimeTtlMinutes = 10;

        private int travelTimeTtlMinutes = 30;
    }

    @Getter
    @Setter
    public static class TrafficMultiplierConfig {
        private double morningRush = 1.2;

        private double eveningRush = 1.4;

        private double daytime = 1.1;
        private double evening = 1.1;

        private double night = 0.9;

        private double weekendFactor = 0.8;

        public double getMultiplierForHour(int hour) {
            if (hour >= 7 && hour <= 9) {
                return morningRush;
            } else if (hour >= 17 && hour <= 19) {
                return eveningRush;
            } else if (hour >= 10 && hour <= 16) {
                return daytime;
            } else if (hour >= 20 && hour <= 22) {
                return evening;
            } else {
                return night;
            }
        }

        public double getAdjustedMultiplier(int hour, boolean isWeekend) {
            double baseMultiplier = getMultiplierForHour(hour);
            return isWeekend ? baseMultiplier * weekendFactor : baseMultiplier;
        }
    }

    @Getter
    @Setter
    public static class StopConfig {

        private int dwellTimeSeconds = 30;

        private int stoppedPenaltySeconds = 60;

        private int terminalWaitSeconds = 120;
    }

    @Getter
    @Setter
    public static class FallbackConfig {

        private int defaultTravelTimeMinutes = 15;

        private int minutesPerStop = 2;

        private int maxWaitingTimeMinutes = 30;

        private double routeDistanceCorrectionFactor = 1.3;
    }

    @Getter
    @Setter
    public static class TransferConfig {

        private int majorStopMinutes = 3;

        private int regularStopMinutes = 5;

        private int airportPenaltyMinutes = 3;

        private int marketPenaltyMinutes = 2;

        private int centerPenaltyMinutes = 2;
    }
}
