package biz.ugur.busroutebackend.routing.infrastructure.config;

import biz.ugur.busroutebackend.interfaces.rest.routing.dto.request.TripSearchRequest;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripSearchCriteria;
import org.springframework.stereotype.Component;

@Component
public class SearchContextFactory {

    public SearchContext createFromRequest(TripSearchRequest request, String correlationId) {
        String searchId = generateSearchId(request);
        Location fromLocation = createLocationFromDTO(request.getFrom());
        Location toLocation = createLocationFromDTO(request.getTo());
        TripSearchCriteria criteria = createSearchCriteria(request.getPreferences());

        return new SearchContext(searchId, fromLocation, toLocation, criteria, System.currentTimeMillis());
    }

    private String generateSearchId(TripSearchRequest request) {
        return String.format("SEARCH_%d_%s",
                System.currentTimeMillis() % 100000,
                Integer.toHexString(request.hashCode()).substring(0, 4).toUpperCase());
    }

    private Location createLocationFromDTO(TripSearchRequest.LocationDTO dto) {
        return new Location(
                dto.getLatitude(),
                dto.getLongitude(),
                dto.getDescription() != null ? dto.getDescription() : "Location"
        );
    }

    private TripSearchCriteria createSearchCriteria(TripSearchRequest.TripSearchPreferences preferences) {
        if (preferences == null) {
            return TripSearchCriteria.defaultCriteria();
        }

        return new TripSearchCriteria(
                preferences.getMaxWalkingDistanceMeters() != null ?
                        preferences.getMaxWalkingDistanceMeters() : 800,
                preferences.getMaxTransfers() != null ?
                        preferences.getMaxTransfers() : 2,
                preferences.getPrioritizeSpeed() != null ?
                        preferences.getPrioritizeSpeed() : true,
                preferences.getPrioritizeFewerTransfers() != null ?
                        preferences.getPrioritizeFewerTransfers() : true
        );
    }
}