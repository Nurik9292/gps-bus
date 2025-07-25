package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.routing.application.dto.RouteSegmentDTO;
import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import biz.ugur.busroutebackend.routing.application.dto.TripSearchRequest;
import biz.ugur.busroutebackend.routing.application.dto.TripSearchResponse;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.repository.TripPlanRepository;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.routing.domain.volumeojects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripOption;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripSearchCriteria;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SearchTripsUseCase - главный координатор поиска поездок
 *
 * Это главная точка входа для пользователей системы.
 * Координирует работу различных алгоритмов поиска и оптимизирует результаты.
 *
 * Стратегия поиска:
 * 1. Сначала ищем прямые маршруты (самые удобные)
 * 2. Если прямых маршрутов мало (< 3), добавляем варианты с пересадками
 * 3. Кэшируем популярные поиски для производительности
 * 4. Возвращаем топ-5 лучших вариантов
 *
 * Performance optimizations:
 * - Redis кэширование результатов поиска
 * - Параллельный поиск прямых и transfer маршрутов
 * - Интеллектуальная фильтрация неразумных вариантов
 * - Ограничение времени поиска (timeout 10 секунд)
 */
@Service
@Slf4j
public class SearchTripsUseCase implements UseCase<TripSearchRequest, Mono<TripSearchResponse>> {

    private final FindDirectRoutesUseCase findDirectRoutesUseCase;
    private final FindRoutesWithTransfersUseCase findRoutesWithTransfersUseCase;
    private final TripPlanRepository tripPlanRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public SearchTripsUseCase(FindDirectRoutesUseCase findDirectRoutesUseCase,
                              FindRoutesWithTransfersUseCase findRoutesWithTransfersUseCase,
                              TripPlanRepository tripPlanRepository,
                              ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.findDirectRoutesUseCase = findDirectRoutesUseCase;
        this.findRoutesWithTransfersUseCase = findRoutesWithTransfersUseCase;
        this.tripPlanRepository = tripPlanRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<TripSearchResponse> execute(TripSearchRequest request) {
        log.info("Trip search request: from ({},{}) to ({},{})",
                request.getFrom().getLatitude(), request.getFrom().getLongitude(),
                request.getTo().getLatitude(), request.getTo().getLongitude());

        // Валидация входных данных
        if (!isValidRequest(request)) {
            return Mono.just(new TripSearchResponse("error", "Invalid search parameters", List.of()));
        }

        // Конвертируем DTO в domain objects
        Location fromLocation = createLocationFromDTO(request.getFrom());
        Location toLocation = createLocationFromDTO(request.getTo());
        TripSearchCriteria searchCriteria = createSearchCriteria(request.getPreferences());

        // Проверяем кэш для популярных поисков
        String cacheKey = createCacheKey(fromLocation, toLocation, searchCriteria);

        return checkCachedResults(cacheKey)
                .switchIfEmpty(
                        // Если нет в кэше, выполняем поиск
                        performTripSearch(fromLocation, toLocation, searchCriteria)
                                .flatMap(response -> {
                                    // Кэшируем результат если поиск успешный
                                    if ("success".equals(response.getStatus()) &&
                                            response.getTripOptions() != null &&
                                            !response.getTripOptions().isEmpty()) {

                                        return cacheSearchResult(cacheKey, response)
                                                .thenReturn(response);
                                    }
                                    return Mono.just(response);
                                })
                )
                .timeout(Duration.ofSeconds(10)) // Максимум 10 секунд на поиск
                .doOnSuccess(response -> logSearchResult(request, response))
                .doOnError(error -> log.error("Trip search failed", error))
                .onErrorReturn(new TripSearchResponse("error", "Trip search timeout or error", List.of()));
    }

    /**
     * Основная логика поиска поездок
     */
    private Mono<TripSearchResponse> performTripSearch(Location fromLocation, Location toLocation,
                                                       TripSearchCriteria searchCriteria) {

        // СТРАТЕГИЯ: Сначала прямые маршруты, потом с пересадками если нужно
        return findDirectRoutesUseCase.execute(
                        new FindDirectRoutesUseCase.Command(fromLocation, toLocation, searchCriteria)
                )
                .flatMap(directPlan -> {
                    int directOptionsCount = directPlan.getDirectOptions().size();

                    // Если найдено достаточно прямых маршрутов (3+), возвращаем их
                    if (directOptionsCount >= 3) {
                        log.info("Found {} direct routes, sufficient options available", directOptionsCount);
                        return savePlanAndCreateResponse(directPlan,
                                String.format("Found %d direct route options", directOptionsCount));
                    }

                    // Иначе ищем дополнительно маршруты с пересадками
                    log.info("Found only {} direct routes, searching for transfer options", directOptionsCount);

                    return findRoutesWithTransfersUseCase.execute(
                                    new FindRoutesWithTransfersUseCase.Command(fromLocation, toLocation, searchCriteria, directPlan)
                            )
                            .flatMap(finalPlan -> {
                                int totalOptions = finalPlan.getTripOptions().size();
                                int transferOptions = finalPlan.getTransferOptions().size();

                                String message;
                                if (totalOptions == 0) {
                                    message = "No routes found between these locations";
                                } else {
                                    message = String.format("Found %d route options (%d direct, %d with transfers)",
                                            totalOptions, directOptionsCount, transferOptions);
                                }

                                return savePlanAndCreateResponse(finalPlan, message);
                            });
                });
    }

    /**
     * Проверить кэшированные результаты
     */
    private Mono<TripSearchResponse> checkCachedResults(String cacheKey) {
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(TripSearchResponse.class)
                .doOnNext(cachedResponse -> {
                    log.debug("Found cached trip search result");
                    // Обновляем время поиска для кэшированного результата
                    cachedResponse.setSearchTime(LocalDateTime.now());
                });
    }

    /**
     * Кэшировать результат поиска
     */
    private Mono<Boolean> cacheSearchResult(String cacheKey, TripSearchResponse response) {
        // Кэшируем на 30 минут для популярных направлений
        return redisTemplate.opsForValue()
                .set(cacheKey, response, Duration.ofMinutes(30))
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Cached trip search result");
                    }
                });
    }

    /**
     * Сохранить план и создать ответ
     */
    private Mono<TripSearchResponse> savePlanAndCreateResponse(TripPlan tripPlan, String message) {
        return tripPlanRepository.save(tripPlan)
                .then(Mono.fromCallable(() -> {
                    // Берем лучшие 5 вариантов с интеллектуальной сортировкой
                    List<TripOptionDTO> options = selectBestOptions(tripPlan)
                            .stream()
                            .map(this::convertToDTO)
                            .collect(Collectors.toList());

                    String status = options.isEmpty() ? "no_routes" : "success";
                    return new TripSearchResponse(status, message, options);
                }))
                .doOnSuccess(response -> log.info("Trip search completed: {} options returned",
                        response.getTripOptions().size()));
    }

    /**
     * Выбрать лучшие варианты с разнообразием
     */
    private List<TripOption> selectBestOptions(TripPlan tripPlan) {
        List<TripOption> allOptions = tripPlan.getTripOptions();

        if (allOptions.size() <= 5) {
            return tripPlan.getBestOptions(5);
        }

        // Интеллектуальный отбор: разнообразие типов маршрутов
        List<TripOption> selectedOptions = new ArrayList<>();

        // 1. Обязательно включаем лучший прямой маршрут (если есть)
        tripPlan.getDirectOptions().stream()
                .findFirst()
                .ifPresent(selectedOptions::add);

        // 2. Обязательно включаем лучший с пересадками (если есть)
        tripPlan.getTransferOptions().stream()
                .findFirst()
                .ifPresent(option -> {
                    if (!selectedOptions.contains(option)) {
                        selectedOptions.add(option);
                    }
                });

        // 3. Добавляем самый быстрый
        TripOption fastest = tripPlan.getFastestOption();
        if (fastest != null && !selectedOptions.contains(fastest)) {
            selectedOptions.add(fastest);
        }

        // 4. Добавляем с наименьшими пересадками
        TripOption fewestTransfers = tripPlan.getOptionWithFewestTransfers();
        if (fewestTransfers != null && !selectedOptions.contains(fewestTransfers)) {
            selectedOptions.add(fewestTransfers);
        }

        // 5. Добавляем самый дешевый
        TripOption cheapest = tripPlan.getCheapestOption();
        if (cheapest != null && !selectedOptions.contains(cheapest)) {
            selectedOptions.add(cheapest);
        }

        // 6. Дополняем до 5 лучшими оставшимися
        tripPlan.getBestOptions(10).stream()
                .filter(option -> !selectedOptions.contains(option))
                .limit(5 - selectedOptions.size())
                .forEach(selectedOptions::add);

        return selectedOptions;
    }

    /**
     * Конвертировать domain object в DTO
     */
    private TripOptionDTO convertToDTO(TripOption tripOption) {
        List<RouteSegmentDTO> segments = tripOption.getRouteSegments()
                .stream()
                .map(this::convertSegmentToDTO)
                .collect(Collectors.toList());

        TripOptionDTO dto = new TripOptionDTO(
                tripOption.getOptionId(),
                tripOption.getTripType().name().toLowerCase(),
                tripOption.getSummary(),
                tripOption.getTotalTravelMinutes(),
                tripOption.getTotalWalkingMinutes(),
                tripOption.getTransfersCount(),
                segments
        );

        // Добавляем дополнительную информацию
        dto.setEstimatedDeparture(tripOption.getEstimatedDeparture());
        dto.setEstimatedArrival(tripOption.getEstimatedArrival());

        return dto;
    }

    /**
     * Конвертировать segment в DTO
     */
    private RouteSegmentDTO convertSegmentToDTO(RouteSegment segment) {
        RouteSegmentDTO dto = new RouteSegmentDTO(
                segment.getType().name().toLowerCase(),
                segment.getDetailedDescription(),
                segment.getDurationMinutes(),
                segment.getRouteNumber(),
                segment.getInstruction()
        );

        dto.setFromLocation(new RouteSegmentDTO.LocationPointDTO(
                segment.getFromLocation().getLatitude(),
                segment.getFromLocation().getLongitude(),
                segment.getFromLocation().getDescription()
        ));

        dto.setToLocation(new RouteSegmentDTO.LocationPointDTO(
                segment.getToLocation().getLatitude(),
                segment.getToLocation().getLongitude(),
                segment.getToLocation().getDescription()
        ));

        return dto;
    }

    // Вспомогательные методы

    private boolean isValidRequest(TripSearchRequest request) {
        if (request.getFrom() == null || request.getTo() == null) {
            return false;
        }

        // Проверяем границы Туркменистана
        if (!isLocationInTurkmenistan(request.getFrom()) || !isLocationInTurkmenistan(request.getTo())) {
            return false;
        }

        // Проверяем что точки не совпадают
        double distance = calculateDistance(request.getFrom(), request.getTo());
        return distance >= 100; // Минимум 100 метров
    }

    private boolean isLocationInTurkmenistan(TripSearchRequest.LocationDTO location) {
        return location.getLatitude() >= 35.0 && location.getLatitude() <= 43.0 &&
                location.getLongitude() >= 52.0 && location.getLongitude() <= 67.0;
    }

    private double calculateDistance(TripSearchRequest.LocationDTO from, TripSearchRequest.LocationDTO to) {
        // Haversine formula для расчета расстояния
        final int R = 6371000; // Радиус Земли в метрах

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

    private String createCacheKey(Location from, Location to, TripSearchCriteria criteria) {
        return String.format("trip_search:%.4f:%.4f:%.4f:%.4f:%d:%d:%s:%s",
                from.getLatitude(), from.getLongitude(),
                to.getLatitude(), to.getLongitude(),
                criteria.getMaxWalkingDistanceMeters(),
                criteria.getMaxTransfers(),
                criteria.isPrioritizeSpeed(),
                criteria.isPrioritizeFewerTransfers());
    }

    private void logSearchResult(TripSearchRequest request, TripSearchResponse response) {
        if (response.getTripOptions() != null) {
            log.info("Trip search from ({},{}) to ({},{}) completed: {} - {} options found",
                    request.getFrom().getLatitude(), request.getFrom().getLongitude(),
                    request.getTo().getLatitude(), request.getTo().getLongitude(),
                    response.getStatus(),
                    response.getTripOptions().size());
        }
    }
}