package biz.ugur.busroutebackend.interfaces.websocket;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

class SessionConfig {
    @Setter
    @Getter
    private String subscriptionType = "all"; // "all", "routes", "bounds"
    @Getter
    @Setter
    private Set<String> routeFilter;
    private Double northBound, southBound, eastBound, westBound;

    public void setBounds(double lat1, double lon1, double lat2, double lon2) {
        this.northBound = Math.max(lat1, lat2);
        this.southBound = Math.min(lat1, lat2);
        this.eastBound = Math.max(lon1, lon2);
        this.westBound = Math.min(lon1, lon2);
    }

    public boolean isInBounds(double latitude, double longitude) {
        return northBound != null && southBound != null && eastBound != null && westBound != null &&
                latitude >= southBound && latitude <= northBound &&
                longitude >= westBound && longitude <= eastBound;
    }

}