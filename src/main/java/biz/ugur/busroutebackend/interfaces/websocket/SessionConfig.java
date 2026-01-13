package biz.ugur.busroutebackend.interfaces.websocket;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Log4j2
@ToString(exclude = "lastActivityTime")
@EqualsAndHashCode(exclude = "lastActivityTime")
class SessionConfig {

    @Setter
    @Getter
    private String subscriptionType = "all"; // "all", "routes", "bounds"

    @Getter
    private Set<String> routeFilter;

    private Double northBound, southBound, eastBound, westBound;

    @Getter
    private volatile Instant lastActivityTime;

    @Getter
    @Setter
    private String clientIp;

    public SessionConfig() {
        this.lastActivityTime = Instant.now();
    }

    public void touch() {
        this.lastActivityTime = Instant.now();
    }

    public boolean isExpired(Duration timeout) {
        if (lastActivityTime == null) {
            return true;
        }
        return Instant.now().isAfter(lastActivityTime.plus(timeout));
    }

    public Duration getInactiveDuration() {
        if (lastActivityTime == null) {
            return Duration.ofDays(1);
        }
        return Duration.between(lastActivityTime, Instant.now());
    }

    public void setBounds(double lat1, double lon1, double lat2, double lon2) {
        try {
            this.northBound = Math.max(lat1, lat2);
            this.southBound = Math.min(lat1, lat2);
            this.eastBound = Math.max(lon1, lon2);
            this.westBound = Math.min(lon1, lon2);
            touch();
        } catch (Exception e) {
            log.warn("Error setting bounds [{},{},{},{}]: {}",
                    lat1, lon1, lat2, lon2, e.getMessage());
            this.northBound = this.southBound = this.eastBound = this.westBound = null;
        }
    }

    public void setRouteFilter(Set<String> routeFilter) {
        this.routeFilter = routeFilter;
        touch();
    }

    public boolean isInBounds(double latitude, double longitude) {
        if (northBound == null || southBound == null || eastBound == null || westBound == null) {
            log.debug("Bounds not set (null values): N={}, S={}, E={}, W={}",
                    northBound, southBound, eastBound, westBound);
            return false;
        }

        if (Double.isNaN(latitude) || Double.isNaN(longitude) ||
                Double.isInfinite(latitude) || Double.isInfinite(longitude)) {
            log.debug("Invalid coordinates: lat={}, lon={}", latitude, longitude);
            return false;
        }

        try {
            boolean result = latitude >= southBound && latitude <= northBound &&
                    longitude >= westBound && longitude <= eastBound;

            log.trace("Bounds check: lat={}, lon={} -> {}", latitude, longitude, result);
            return result;

        } catch (Exception e) {
            log.warn("Error in bounds check for lat={}, lon={}: {}",
                    latitude, longitude, e.getMessage());
            return false;
        }
    }

    public String getBoundsString() {
        if (northBound == null || southBound == null || eastBound == null || westBound == null) {
            return "null";
        }
        return String.format("[%s,%s,%s,%s]", southBound, westBound, northBound, eastBound);
    }

    public boolean isValid() {
        if (subscriptionType == null) {
            return false;
        }

        return switch (subscriptionType) {
            case "all" -> true;
            case "routes" -> routeFilter != null && !routeFilter.isEmpty();
            case "bounds" -> northBound != null && southBound != null &&
                    eastBound != null && westBound != null;
            default -> false;
        };
    }
}
