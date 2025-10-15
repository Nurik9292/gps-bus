package biz.ugur.busroutebackend.routing.application.dto;

import biz.ugur.busroutebackend.interfaces.rest.routing.V1.request.TripSearchRequest;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;

import java.util.UUID;

/**
 * Search context for trip planning.
 * Migrated from Location to Coordinates as part of geospatial module consolidation.
 */
public record SearchContext(
        String searchId,
        Coordinates fromLocation,
        Coordinates toLocation,
        TripSearchCriteria searchCriteria,
        long startTime
) {

    public static SearchContext of(Coordinates fromLocation, Coordinates toLocation, TripSearchCriteria searchCriteria) {
        return new SearchContext(UUID.randomUUID().toString(), fromLocation, toLocation, searchCriteria, System.currentTimeMillis());
    }

    public static SearchContext from(TripSearchRequest request, String correlationId) {
        String searchId = generateSearchId(request);
        Coordinates fromLocation = createCoordinatesFromDTO(request.getFrom());
        Coordinates toLocation = createCoordinatesFromDTO(request.getTo());
        TripSearchCriteria criteria = createSearchCriteria(request.getPreferences());
        return new SearchContext(searchId, fromLocation, toLocation, criteria, System.currentTimeMillis());
    }

    private static String generateSearchId(TripSearchRequest request) {
        return String.format("SEARCH_%d_%s",
                System.currentTimeMillis() % 100000,
                Integer.toHexString(request.hashCode()).substring(0, 4).toUpperCase());
    }

    private static Coordinates createCoordinatesFromDTO(TripSearchRequest.LocationDTO dto) {
        return Coordinates.of(
                dto.getLatitude(),
                dto.getLongitude()
        );
    }

    private static   TripSearchCriteria createSearchCriteria(TripSearchRequest.TripSearchPreferences preferences) {
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