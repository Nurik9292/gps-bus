package biz.ugur.busroutebackend.place.domain.exceptions;

public class PlaceNotFoundException extends PlaceDomainException {

    public PlaceNotFoundException(String placeId) {
        super("place.not_found", "Place not found with ID: " + placeId);
    }

    public PlaceNotFoundException(String entityType, String id) {
        super("place.not_found", entityType + " not found with ID: " + id);
    }
}
