package biz.ugur.busroutebackend.routing.infrastructure.config;

import biz.ugur.busroutebackend.interfaces.rest.routing.V1.request.TripSearchRequest;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import org.springframework.stereotype.Component;

@Component
public class SearchContextFactory {

    public SearchContext createFromRequest(TripSearchRequest request, String correlationId) {
        String searchId = generateSearchId(request);
        Coordinates fromLocation = createCoordinatesFromDTO(request.getFrom());
        Coordinates toLocation = createCoordinatesFromDTO(request.getTo());
        TripSearchCriteria criteria = createSearchCriteria(request.getPreferences());

        return new SearchContext(searchId, fromLocation, toLocation, criteria, System.currentTimeMillis());
    }

    private String generateSearchId(TripSearchRequest request) {
        return String.format("SEARCH_%d_%s",
                System.currentTimeMillis() % 100000,
                Integer.toHexString(request.hashCode()).substring(0, 4).toUpperCase());
    }

    private Coordinates createCoordinatesFromDTO(TripSearchRequest.LocationDTO dto) {
        return Coordinates.of(
                dto.getLatitude(),
                dto.getLongitude()
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