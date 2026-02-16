package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.place.application.dto.*;
import biz.ugur.busroutebackend.place.application.usecase.*;
import biz.ugur.busroutebackend.place.domain.model.PlaceCategory;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_PLACES;

@RestController
@RequestMapping(V1_MOBILE_PLACES)
@CrossOrigin("*")
public class MobilePlaceApiController extends BaseController {

    private final SearchPlacesUseCase searchPlacesUseCase;
    private final GetPlaceByIdUseCase getPlaceByIdUseCase;
    private final FindNearbyPlacesUseCase findNearbyPlacesUseCase;
    private final AutocompletePlacesUseCase autocompletePlacesUseCase;

    public MobilePlaceApiController(SearchPlacesUseCase searchPlacesUseCase,
                                    GetPlaceByIdUseCase getPlaceByIdUseCase,
                                    FindNearbyPlacesUseCase findNearbyPlacesUseCase,
                                    AutocompletePlacesUseCase autocompletePlacesUseCase,
                                    MessageSource messageSource) {
        super(messageSource);
        this.searchPlacesUseCase = searchPlacesUseCase;
        this.getPlaceByIdUseCase = getPlaceByIdUseCase;
        this.findNearbyPlacesUseCase = findNearbyPlacesUseCase;
        this.autocompletePlacesUseCase = autocompletePlacesUseCase;
    }

    @Override
    protected String getControllerName() {
        return MobilePlaceApiController.class.getSimpleName();
    }

    @GetMapping("/search")
    public Mono<ResponseEntity<ApiResponse<List<PlaceSearchResult>>>> searchPlaces(
            @RequestParam("q") String query,
            @RequestParam(required = false) String cityId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String lang) {

        return ok(Mono.just(new PlaceSearchQuery(query, cityId, category, limit, lang))
                .as(searchPlacesUseCase::execute));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<PlaceResult>>> getPlaceById(@PathVariable String id) {
        return ok(Mono.just(id).as(getPlaceByIdUseCase::execute));
    }

    @GetMapping("/nearby")
    public Mono<ResponseEntity<ApiResponse<List<PlaceSearchResult>>>> findNearbyPlaces(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") int radius,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "20") int limit) {

        return ok(Mono.just(new PlaceNearbyQuery(lat, lng, radius, category, limit))
                .as(findNearbyPlacesUseCase::execute));
    }

    @GetMapping("/categories")
    public Mono<ResponseEntity<ApiResponse<List<String>>>> getCategories() {
        List<String> categories = Arrays.stream(PlaceCategory.values())
                .map(PlaceCategory::name)
                .toList();
        return ok(Mono.just(categories));
    }

    @GetMapping("/autocomplete")
    public Mono<ResponseEntity<ApiResponse<List<PlaceAutocompleteResult>>>> autocomplete(
            @RequestParam("q") String query,
            @RequestParam(required = false) String cityId,
            @RequestParam(defaultValue = "10") int limit) {

        return ok(Mono.just(new PlaceSearchQuery(query, cityId, null, limit, null))
                .as(autocompletePlacesUseCase::execute));
    }
}
