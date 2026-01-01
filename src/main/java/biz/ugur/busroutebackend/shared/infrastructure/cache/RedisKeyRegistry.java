package biz.ugur.busroutebackend.shared.infrastructure.cache;

import java.time.Duration;
import java.time.Instant;


public final class RedisKeyRegistry {

    private RedisKeyRegistry() {
    }

    public static final class Gps {
        public static final String UPDATE_STATS_PREFIX = "gps:update:stats";
        public static final String HEALTH_PREFIX = "gps:health";
        public static final String HISTORY_PREFIX = "gps:history";

        public static final Duration STATS_TTL = Duration.ofDays(7);
        public static final Duration HEALTH_TTL = Duration.ofMinutes(10);
        public static final int STATS_CLEANUP_DAYS = 7;

        public static String updateStats(long epochSecond) {
            return UPDATE_STATS_PREFIX + ":" + epochSecond;
        }

        public static String updateStats(Instant instant) {
            return updateStats(instant.getEpochSecond());
        }

        public static String health(String providerName) {
            return HEALTH_PREFIX + ":" + providerName;
        }

        public static String health() {
            return HEALTH_PREFIX;
        }

        public static String history(String deviceId) {
            return HISTORY_PREFIX + ":" + deviceId;
        }

        public static String statsPattern() {
            return UPDATE_STATS_PREFIX + ":*";
        }

        private Gps() {
        }
    }

    public static final class Vehicle {
        public static final String POSITION_PREFIX = "vehicle:position";
        public static final String ROUTE_PREFIX = "vehicle:route";
        public static final String INFO_PREFIX = "vehicle:info";

        public static final Duration POSITION_TTL = Duration.ofMinutes(10);
        public static final Duration ROUTE_TTL = Duration.ofHours(24);
        public static final Duration UNASSIGN_ROUTE_TTL = Duration.ofMinutes(5);
        public static final Duration INFO_TTL = Duration.ofDays(30);

        public static String position(String vehicleId) {
            return POSITION_PREFIX + ":" + vehicleId;
        }

        public static String route(String vehicleId) {
            return ROUTE_PREFIX + ":" + vehicleId;
        }

        public static String info(String vehicleId) {
            return INFO_PREFIX + ":" + vehicleId;
        }

        private Vehicle() {
        }
    }

    public static final class Route {
        public static final String CACHE_PREFIX = "route:cache";
        public static final String STOPS_PREFIX = "route:stops";

        public static String cache(String routeId) {
            return CACHE_PREFIX + ":" + routeId;
        }

        public static String stops(String routeId) {
            return STOPS_PREFIX + ":" + routeId;
        }

        private Route() {
        }
    }

    public static final class Search {
        public static final String RESULT_PREFIX = "search:result";

        public static String result(String query) {
            return RESULT_PREFIX + ":" + query;
        }

        private Search() {
        }
    }
}
