package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.interfaces.rest.routing.V1.request.TripSearchRequest;
import biz.ugur.busroutebackend.interfaces.rest.routing.V1.response.TripSearchResponse;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.response.ResponseBuilder;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.repository.TripPlanRepository;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.infrastructure.config.RouteSearchConfig;
import biz.ugur.busroutebackend.routing.infrastructure.config.SearchContextFactory;
import biz.ugur.busroutebackend.routing.infrastructure.services.ParallelRouteSearchService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Slf4j
public class SearchTripsUseCase extends BaseUseCase<Mono<TripSearchRequest>, TripSearchResponse> {

    private final ParallelRouteSearchService parallelSearchService;
    private final TripPlanRepository tripPlanRepository;
    private final ResponseBuilder responseBuilder;
    private final SearchContextFactory contextFactory;
    private final RouteSearchConfig config;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public SearchTripsUseCase(ParallelRouteSearchService parallelSearchService,
                              TripPlanRepository tripPlanRepository,
                              ResponseBuilder responseBuilder,
                              CorrelationContextService correlationService,
                              SearchContextFactory contextFactory,
                              RouteSearchConfig config,
                              EventBus eventBus,
                              ReactiveRedisTemplate<String, Object> redisTemplate) {
        super(correlationService, eventBus);
        this.parallelSearchService = parallelSearchService;
        this.tripPlanRepository = tripPlanRepository;
        this.responseBuilder = responseBuilder;
        this.contextFactory = contextFactory;
        this.config = config;
        this.redisTemplate = redisTemplate;
    }


    @Override
    protected Mono<TripSearchResponse> process(Mono<TripSearchRequest> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "routing";
    }

    private Mono<TripSearchResponse> processInternal(TripSearchRequest request) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    SearchContext context = contextFactory.createFromRequest(request, correlationId.toString());
                    return validateAndSearch(request, context);
                });
    }

    private Mono<TripSearchResponse> validateAndSearch(TripSearchRequest request, SearchContext context) {
        ValidationResult validation = validateRequest(request);

        if (!validation.isValid()) {
            return responseBuilder.createErrorResponse(validation.getErrorMessage());
        }

        String cacheKey = createCacheKey(context);

        return checkCacheFirst(cacheKey, context)
                .switchIfEmpty(performSearchAndCache(context, cacheKey))
                .timeout(config.getTotalSearchTimeout())
                .onErrorResume(error -> handleSearchError(error, context));
    }

    private Mono<TripSearchResponse> checkCacheFirst(String cacheKey, SearchContext context) {
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(TripSearchResponse.class)
                .timeout(config.getCacheTimeout())
                .doOnNext(cachedResponse -> {
                    cachedResponse.setSearchTime(LocalDateTime.now());
                })
                .onErrorResume(error -> {
                    return Mono.empty();
                });
    }

    private Mono<TripSearchResponse> performSearchAndCache(SearchContext context, String cacheKey) {
        return parallelSearchService.searchAllRoutes(context)
                .flatMap(tripPlan -> saveTripPlan(tripPlan, context))
                .flatMap(tripPlan -> responseBuilder.createSuccessResponse(tripPlan, context))
                .flatMap(response -> cacheIfSuccessful(cacheKey, response, context))
                .doOnSuccess(response -> logSearchResult(context, response));
    }

    private Mono<TripPlan> saveTripPlan(TripPlan tripPlan, SearchContext context) {
        return tripPlanRepository.save(tripPlan)
                .timeout(Duration.ofSeconds(3))
                .doOnError(error -> log.error("[{}] Failed to save trip plan: {}",
                        context.searchId(), error.getMessage()))
                .onErrorResume(error -> {
                    log.warn("[{}] Continuing without saving trip plan", context.searchId());
                    return Mono.just(tripPlan);
                });
    }

    private Mono<TripSearchResponse> cacheIfSuccessful(String cacheKey, TripSearchResponse response,
                                                       SearchContext context) {
        if (shouldCacheResponse(response)) {
            return redisTemplate.opsForValue()
                    .set(cacheKey, response, config.getCacheTtl())
                    .timeout(Duration.ofSeconds(1))
                    .doOnSuccess(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            log.debug("[{}] Cached search result", context.searchId());
                        }
                    })
                    .onErrorResume(error -> {
                        log.warn("[{}] Failed to cache result: {}", context.searchId(), error.getMessage());
                        return Mono.just(false);
                    })
                    .thenReturn(response);
        }
        return Mono.just(response);
    }

    private boolean shouldCacheResponse(TripSearchResponse response) {
        return "success".equals(response.getStatus()) &&
                response.getTripOptions() != null &&
                !response.getTripOptions().isEmpty();
    }

    private ValidationResult validateRequest(TripSearchRequest request) {
        if (request.getFrom() == null || request.getTo() == null) {
            return ValidationResult.invalid("From and To locations are required");
        }

        if (!isLocationInTurkmenistan(request.getFrom()) || !isLocationInTurkmenistan(request.getTo())) {
            return ValidationResult.invalid("Locations must be within Turkmenistan");
        }

        double distance = calculateDistance(request.getFrom(), request.getTo());
        if (distance < 100) {
            return ValidationResult.invalid("Minimum distance is 100 meters");
        }

        return ValidationResult.valid();
    }

    private boolean isLocationInTurkmenistan(TripSearchRequest.LocationDTO location) {
        return location.getLatitude() >= 35.0 && location.getLatitude() <= 43.0 &&
                location.getLongitude() >= 52.0 && location.getLongitude() <= 67.0;
    }

    private double calculateDistance(TripSearchRequest.LocationDTO from, TripSearchRequest.LocationDTO to) {
        final int R = 6371000;

        double lat1Rad = Math.toRadians(from.getLatitude());
        double lat2Rad = Math.toRadians(to.getLatitude());
        double deltaLatRad = Math.toRadians(to.getLatitude() - from.getLatitude());
        double deltaLonRad = Math.toRadians(to.getLongitude() - from.getLongitude());

        double a = Math.sin(deltaLatRad/2) * Math.sin(deltaLatRad/2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLonRad/2) * Math.sin(deltaLonRad/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
    }

    private String createCacheKey(SearchContext context) {
        Coordinates from = context.fromLocation();
        Coordinates to = context.toLocation();
        TripSearchCriteria criteria = context.searchCriteria();

        return String.format("trip_search:%.4f:%.4f:%.4f:%.4f:%d:%d:%s:%s",
                from.getLatitudeAsDouble(), from.getLongitudeAsDouble(),
                to.getLatitudeAsDouble(), to.getLongitudeAsDouble(),
                criteria.getMaxWalkingDistanceMeters(),
                criteria.getMaxTransfers(),
                criteria.isPrioritizeSpeed(),
                criteria.isPrioritizeFewerTransfers());
    }

    private void logSearchResult(SearchContext context, TripSearchResponse response) {
        long duration = System.currentTimeMillis() - context.startTime();

        if (response.getTripOptions() != null) {
            log.info("[{}] Trip search completed in {}ms: {} - {} options found",
                    context.searchId(), duration, response.getStatus(), response.getTripOptions().size());
        }
    }

    private Mono<TripSearchResponse> handleSearchError(Throwable error, SearchContext context) {
        long duration = System.currentTimeMillis() - context.startTime();

        if (error instanceof biz.ugur.busroutebackend.routing.domain.exceptions.TripPlanningException tripEx) {
            log.warn("[{}] Trip planning error after {}ms: {} - {}",
                    context.searchId(), duration,
                    tripEx.getPlanningErrorType(), tripEx.getMessage());
            return responseBuilder.createErrorResponseFromException(tripEx);
        }

        // ТЕХНИЧЕСКИЕ ОШИБКИ → пробрасываем в GlobalExceptionHandler (HTTP 5xx)
        // НЕ преобразуем в TripPlanningException!
        log.error("[{}] Technical error during trip search after {}ms: {}",
                context.searchId(), duration, error.getMessage(), error);

        return Mono.error(error);
    }


    @Getter
    private static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

    }
}