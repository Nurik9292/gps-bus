package biz.ugur.busroutebackend.place.domain.repository;

import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceAutocompleteResult;
import reactor.core.publisher.Flux;

public interface PlaceSearchRepository {

    Flux<PlaceSearchResult> search(String query, String cityId, String category, int limit, double threshold);

    Flux<PlaceSearchResult> searchNearby(double lat, double lng, int radiusMeters, String category, int limit);

    Flux<PlaceAutocompleteResult> autocomplete(String query, String cityId, int limit);
}
