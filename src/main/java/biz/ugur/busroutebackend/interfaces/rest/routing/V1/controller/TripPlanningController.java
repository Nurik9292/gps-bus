package biz.ugur.busroutebackend.interfaces.rest.routing.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.routing.V1.request.TripSearchRequest;
import biz.ugur.busroutebackend.interfaces.rest.routing.V1.response.TripSearchResponse;
import biz.ugur.busroutebackend.routing.application.usecase.SearchTripsUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ROUTING;

@RestController
@RequestMapping(V1_ROUTING)
@CrossOrigin(origins = "*")
public class TripPlanningController extends BaseController {

    private final SearchTripsUseCase searchTripsUseCase;
    private final BusStopRepository busStopRepository;

    public TripPlanningController(SearchTripsUseCase searchTripsUseCase,
                                  BusStopRepository busStopRepository,
                                  MessageSource messageSource) {
        super(messageSource);
        this.searchTripsUseCase = searchTripsUseCase;
        this.busStopRepository = busStopRepository;
    }

    @Override
    protected String getControllerName() {
        return TripPlanningController.class.getSimpleName();
    }

    @PostMapping("/search")
    public Mono<ResponseEntity<ApiResponse<TripSearchResponse>>> searchTrips(@Valid @RequestBody TripSearchRequest request) {
        return ok(Mono.just(request)
                .as(searchTripsUseCase::execute));
    }

    @GetMapping("/nearby-stops")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getNearbyStops(
            @RequestParam @DecimalMin("35.0") @DecimalMax("43.0") Double lat,
            @RequestParam @DecimalMin("52.0") @DecimalMax("67.0") Double lon,
            @RequestParam(defaultValue = "1.0") @Min(1) @Max(20) Double radiusKm) {

        return ok(busStopRepository.findStopsWithinRadius(lat, lon, radiusKm)
                .map(stop -> Map.of(
                        "stop_id", stop.getId().getValue(),
                        "stop_name", stop.getStopName(),
                        "stop_code", stop.getStopCode() != null ? stop.getStopCode() : "",
                        "latitude", stop.getLatitude().doubleValue(),
                        "longitude", stop.getLongitude().doubleValue(),
                        "is_major_stop", stop.getIsMajorStop(),
                        "distance_meters", Math.round(calculateDistance(lat, lon,
                                stop.getLatitude().doubleValue(), stop.getLongitude().doubleValue()))
                ))
                .collectList()
                .map(stops -> Map.of(
                        "status", "success",
                        "message", String.format("Found %d nearby stops", stops.size()),
                        "stops", stops,
                        "search_location", Map.of("latitude", lat, "longitude", lon),
                        "search_radius_km", radiusKm
                ))
                .defaultIfEmpty(Map.of(
                        "status", "success",
                        "message", "No stops found in the specified radius",
                        "stops", java.util.List.of(),
                        "search_location", Map.of("latitude", lat, "longitude", lon),
                        "search_radius_km", radiusKm
                )));
    }


    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "Trip Planning Service",
                "timestamp", LocalDateTime.now().toString(),
                "version", "1.0.0",
                "features", List.of(
                        "Direct route search",
                        "Transfer route search",
                        "Real-time ETA calculation",
                        "Nearby stops discovery"
                )
        ));
    }

    @GetMapping("/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getStatistics() {
        return Mono.fromCallable(() -> Map.of(
                        "status", "success",
                        "statistics", Map.of(
                                "total_searches_today", 0,
                                "average_search_time_ms", 850,
                                "success_rate_percent", 95.2,
                                "most_popular_routes", List.of("29", "12", "7A"),
                                "average_options_per_search", 3.4
                        ),
                        "timestamp", LocalDateTime.now().toString()
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/validate-coordinates")
    public ResponseEntity<Map<String, Object>> validateCoordinates(@RequestBody Map<String, Double> coordinates) {
        Double lat = coordinates.get("latitude");
        Double lon = coordinates.get("longitude");

        if (lat == null || lon == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Latitude and longitude are required",
                    "valid", false
            ));
        }

        boolean isValid = lat >= 35.0 && lat <= 43.0 && lon >= 52.0 && lon <= 67.0;
        String message = isValid ?
                "Coordinates are within Turkmenistan bounds" :
                "Coordinates are outside Turkmenistan bounds";

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", message,
                "valid", isValid,
                "coordinates", Map.of("latitude", lat, "longitude", lon),
                "bounds", Map.of(
                        "min_latitude", 35.0,
                        "max_latitude", 43.0,
                        "min_longitude", 52.0,
                        "max_longitude", 67.0
                )
        ));
    }

    @GetMapping("/features")
    public ResponseEntity<Map<String, Object>> getFeatures() {
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "features", Map.of(
                        "direct_routes", Map.of(
                                "enabled", true,
                                "description", "Find routes without transfers",
                                "max_search_time_ms", 500
                        ),
                        "transfer_routes", Map.of(
                                "enabled", true,
                                "description", "Find routes with 1-2 transfers",
                                "max_transfers", 2,
                                "max_search_time_ms", 3000
                        ),
                        "real_time_eta", Map.of(
                                "enabled", true,
                                "description", "Real-time arrival estimation using GPS data",
                                "update_frequency_seconds", 30
                        ),
                        "walking_segments", Map.of(
                                "enabled", true,
                                "max_walking_distance_meters", 800,
                                "walking_speed_kmh", 5.0
                        ),
                        "cost_calculation", Map.of(
                                "enabled", true,
                                "base_fare_manat", 1.0,
                                "transfer_additional_fare", true
                        )
                ),
                "limitations", Map.of(
                        "geographical_bounds", "Turkmenistan only",
                        "max_trip_options", 5,
                        "max_trip_duration_hours", 4
                )
        ));
    }


    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLatRad = Math.toRadians(lat2 - lat1);
        double deltaLonRad = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLatRad/2) * Math.sin(deltaLatRad/2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLonRad/2) * Math.sin(deltaLonRad/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
    }
}