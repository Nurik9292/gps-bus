package biz.ugur.busroutebackend.shared.infrastructure.cache;

import java.time.Duration;
import java.time.Instant;


public final class RedisKeyRegistry {

    private RedisKeyRegistry() {
    }

    public static final class CatalogSearch {
        public static final String QUERY_PREFIX = "catalog-search:q";

        public static String query(String queryHash, int limit) {
            return QUERY_PREFIX + ":" + queryHash + ":" + limit;
        }

        public static String pattern() {
            return QUERY_PREFIX + ":*";
        }

        private CatalogSearch() {
        }
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

    public static final class Place {
        public static final String SEARCH_PREFIX = "place:search";
        public static final String AUTOCOMPLETE_PREFIX = "place:autocomplete";
        public static final String DETAIL_PREFIX = "place:detail";
        public static final String STREET_ALIAS_MAP = "place:street:alias:map";

        public static final Duration SEARCH_TTL = Duration.ofMinutes(5);
        public static final Duration DETAIL_TTL = Duration.ofMinutes(30);
        public static final Duration AUTOCOMPLETE_TTL = Duration.ofMinutes(3);
        public static final Duration STREET_ALIASES_TTL = Duration.ofHours(1);

        public static String search(String key) {
            return SEARCH_PREFIX + ":" + key;
        }

        public static String autocomplete(String key) {
            return AUTOCOMPLETE_PREFIX + ":" + key;
        }

        public static String detail(String placeId) {
            return DETAIL_PREFIX + ":" + placeId;
        }

        private Place() {
        }
    }

    public static final class Analytics {
        public static final String ROUTING_OVERVIEW_PREFIX = "analytics:routing:overview";
        public static final String ROUTING_HEATMAP_PREFIX = "analytics:routing:heatmap";
        public static final String ROUTING_OD_PAIRS_PREFIX = "analytics:routing:od-pairs";

        public static final Duration OVERVIEW_TTL = Duration.ofMinutes(5);
        public static final Duration HEATMAP_TTL = Duration.ofMinutes(15);
        public static final Duration OD_PAIRS_TTL = Duration.ofMinutes(15);

        public static String routingOverview(String period) {
            return ROUTING_OVERVIEW_PREFIX + ":" + period;
        }

        public static String routingHeatmap(String pointType) {
            return ROUTING_HEATMAP_PREFIX + ":" + pointType;
        }

        public static String routingOdPairs(int limit) {
            return ROUTING_OD_PAIRS_PREFIX + ":" + limit;
        }

        private Analytics() {
        }
    }

    public static final class Prediction {
        private static final String STATE_PREFIX = "prediction:state";
        public static final Duration STATE_TTL = Duration.ofMinutes(5);

        public static String state(String vehicleId) {
            return STATE_PREFIX + ":" + vehicleId;
        }

        public static String statePattern() {
            return STATE_PREFIX + ":*";
        }

        private Prediction() {
        }
    }
}
