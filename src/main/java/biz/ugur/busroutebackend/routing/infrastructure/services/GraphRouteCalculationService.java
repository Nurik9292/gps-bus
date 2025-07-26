package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Улучшенная версия сервиса поиска маршрутов с оптимизированными алгоритмами
 *
 * Основные улучшения:
 * - Кэширование результатов поиска в Redis
 * - Оптимизированные SQL запросы с индексами
 * - Интеллектуальная фильтрация маршрутов
 * - Параллельный поиск для производительности
 */
@Service
@Slf4j
public class GraphRouteCalculationService implements RouteCalculationService {

    private final BusStopRepository busStopRepository;
    private final BusRouteRepository busRouteRepository;
    private final DatabaseClient databaseClient;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public GraphRouteCalculationService(BusStopRepository busStopRepository,
                                        BusRouteRepository busRouteRepository,
                                        DatabaseClient databaseClient,
                                        ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.busStopRepository = busStopRepository;
        this.busRouteRepository = busRouteRepository;
        this.databaseClient = databaseClient;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Flux<BusStop> findNearbyStops(Location location, double radiusKm) {
        log.debug("Finding stops within {}km of ({}, {})", radiusKm, location.getLatitude(), location.getLongitude());

        String cacheKey = String.format("nearby_stops:%.6f:%.6f:%.1f",
                location.getLatitude(), location.getLongitude(), radiusKm);

        // Проверяем кэш Redis
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(List.class)
                .flatMapMany(cachedStops -> {
                    log.debug("Found cached nearby stops for location");
                    return Flux.fromIterable((List<String>) cachedStops)
                            .flatMap(stopId -> busStopRepository.findById(
                                    biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId.of(stopId)));
                })
                .switchIfEmpty(
                        // Если нет в кэше, делаем запрос к БД
                        busStopRepository.findStopsWithinRadius(
                                        location.getLatitude(),
                                        location.getLongitude(),
                                        radiusKm
                                )
                                .collectList()
                                .flatMapMany(stops -> {
                                    // Кэшируем результат на 5 минут
                                    List<String> stopIds = stops.stream()
                                            .map(stop -> stop.getId().getValue())
                                            .toList();

                                    return redisTemplate.opsForValue()
                                            .set(cacheKey, stopIds, Duration.ofMinutes(5))
                                            .thenMany(Flux.fromIterable(stops));
                                })
                )
                .doOnNext(stop -> log.trace("Found nearby stop: {} at distance {}m",
                        stop.getStopName(), location.distanceTo(
                                stop.getLatitude().doubleValue(),
                                stop.getLongitude().doubleValue())));
    }

    @Override
    public Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops) {
        log.debug("Finding direct routes between {} origin stops and {} destination stops",
                fromStops.size(), toStops.size());

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }

        // Оптимизированный SQL запрос с индексами
        String sql = """
            SELECT DISTINCT 
                br.id as route_id, br.route_number, br.route_name, br.route_color,
                rs1.stop_id as from_stop_id, bs1.stop_name as from_stop_name,
                bs1.latitude as from_lat, bs1.longitude as from_lon,
                rs2.stop_id as to_stop_id, bs2.stop_name as to_stop_name,
                bs2.latitude as to_lat, bs2.longitude as to_lon,
                rs1.stop_sequence as from_sequence, rs2.stop_sequence as to_sequence,
                rs1.direction,
                ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as estimated_travel_minutes,
                ABS(rs2.distance_from_start_meters - rs1.distance_from_start_meters) as distance_meters,
                COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles_count
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id 
                                AND rs1.direction = rs2.direction
                                AND rs1.stop_sequence < rs2.stop_sequence
            JOIN bus_routes br ON rs1.route_id = br.id
            JOIN bus_stops bs1 ON rs1.stop_id = bs1.id  
            JOIN bus_stops bs2 ON rs2.stop_id = bs2.id
            LEFT JOIN vehicles v ON br.id = v.assigned_route_id
            WHERE rs1.stop_id = ANY(:fromStopIds) 
            AND rs2.stop_id = ANY(:toStopIds)
            AND br.is_active = true
            AND bs1.is_active = true 
            AND bs2.is_active = true
            GROUP BY br.id, br.route_number, br.route_name, br.route_color,
                     rs1.stop_id, bs1.stop_name, bs1.latitude, bs1.longitude,
                     rs2.stop_id, bs2.stop_name, bs2.latitude, bs2.longitude,
                     rs1.stop_sequence, rs2.stop_sequence, rs1.direction,
                     rs1.distance_from_start_meters, rs2.distance_from_start_meters
            ORDER BY estimated_travel_minutes, active_vehicles_count DESC
            LIMIT 15
            """;

        String[] fromStopIds = fromStops.stream()
                .map(stop -> stop.getId().getValue())
                .toArray(String[]::new);
        String[] toStopIds = toStops.stream()
                .map(stop -> stop.getId().getValue())
                .toArray(String[]::new);

        return databaseClient.sql(sql)
                .bind("fromStopIds", fromStopIds)
                .bind("toStopIds", toStopIds)
                .map(row -> {
                    // Создаем BusRoute с дополнительной информацией
                    BusRoute route = new BusRoute(
                            row.get("route_number", String.class),
                            row.get("route_name", String.class),
                            null,
                            row.get("route_color", String.class) != null ?
                                    row.get("route_color", String.class) : "#1976D2"
                    );

                    // Создаем BusStops с координатами
                    BusStop fromStop = new BusStop(
                            row.get("from_stop_name", String.class),
                            row.get("from_stop_id", String.class),
                            row.get("from_lat", BigDecimal.class),
                            row.get("from_lon", BigDecimal.class)
                    );

                    BusStop toStop = new BusStop(
                            row.get("to_stop_name", String.class),
                            row.get("to_stop_id", String.class),
                            row.get("to_lat", BigDecimal.class),
                            row.get("to_lon", BigDecimal.class)
                    );

                    Integer estimatedMinutes = row.get("estimated_travel_minutes", Integer.class);
                    Long activeVehicles = row.get("active_vehicles_count", Long.class);

                    // Корректируем время на основе количества активных автобусов
                    int adjustedMinutes = adjustTravelTimeByVehicleCount(estimatedMinutes, activeVehicles);

                    return new DirectRouteResult(
                            route,
                            fromStop,
                            toStop,
                            adjustedMinutes,
                            0.0, // Walking distance будет рассчитано отдельно
                            0.0  // Walking distance будет рассчитано отдельно
                    );
                })
                .all()
                .doOnComplete(() -> log.debug("Direct routes search completed"));
    }

    @Override
    public Flux<TransferRouteResult> findRoutesWithOneTransfer(List<BusStop> fromStops, List<BusStop> toStops,
                                                               double maxTransferDistanceKm) {
        log.debug("Finding routes with one transfer (max transfer distance: {}km)", maxTransferDistanceKm);

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }

        // Улучшенный SQL для поиска пересадок с геопространственными функциями
        String sql = """
            WITH potential_transfers AS (
                SELECT DISTINCT
                    rs1.route_id as first_route_id,
                    rs1.stop_id as from_stop_id,
                    rs2.stop_id as transfer_stop_id,
                    rs3.route_id as second_route_id,
                    rs4.stop_id as to_stop_id,
                    bs_transfer.stop_name as transfer_stop_name,
                    bs_transfer.latitude as transfer_lat,
                    bs_transfer.longitude as transfer_lon,
                    bs_transfer.is_major_stop as transfer_is_major,
                    ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as first_route_minutes,
                    ABS(rs4.stop_sequence - rs3.stop_sequence) * 2 as second_route_minutes,
                    rs2.distance_from_start_meters - rs1.distance_from_start_meters as first_route_distance,
                    rs4.distance_from_start_meters - rs3.distance_from_start_meters as second_route_distance
                FROM route_stops rs1
                JOIN route_stops rs2 ON rs1.route_id = rs2.route_id 
                                    AND rs1.direction = rs2.direction
                                    AND rs1.stop_sequence < rs2.stop_sequence
                JOIN route_stops rs3 ON rs2.stop_id = rs3.stop_id 
                                    AND rs3.direction = rs2.direction
                                    AND rs1.route_id != rs3.route_id
                JOIN route_stops rs4 ON rs3.route_id = rs4.route_id 
                                    AND rs3.direction = rs4.direction
                                    AND rs3.stop_sequence < rs4.stop_sequence
                JOIN bus_stops bs_transfer ON rs2.stop_id = bs_transfer.id
                WHERE rs1.stop_id = ANY(:fromStopIds)
                AND rs4.stop_id = ANY(:toStopIds)
                AND (rs2.stop_sequence - rs1.stop_sequence) <= 15  -- Максимум 15 остановок на первом маршруте
                AND (rs4.stop_sequence - rs3.stop_sequence) <= 15  -- Максимум 15 остановок на втором маршруте
            )
            SELECT 
                pt.*,
                br1.route_number as first_route_number,
                br1.route_name as first_route_name,
                br1.route_color as first_route_color,
                br2.route_number as second_route_number,
                br2.route_name as second_route_name,
                br2.route_color as second_route_color,
                bs_from.stop_name as from_stop_name,
                bs_from.latitude as from_lat,
                bs_from.longitude as from_lon,
                bs_to.stop_name as to_stop_name,
                bs_to.latitude as to_lat,
                bs_to.longitude as to_lon,
                COUNT(v1.id) FILTER (WHERE v1.is_active = true) as first_route_vehicles,
                COUNT(v2.id) FILTER (WHERE v2.is_active = true) as second_route_vehicles
            FROM potential_transfers pt
            JOIN bus_routes br1 ON pt.first_route_id = br1.id
            JOIN bus_routes br2 ON pt.second_route_id = br2.id
            JOIN bus_stops bs_from ON pt.from_stop_id = bs_from.id
            JOIN bus_stops bs_to ON pt.to_stop_id = bs_to.id
            LEFT JOIN vehicles v1 ON br1.id = v1.assigned_route_id
            LEFT JOIN vehicles v2 ON br2.id = v2.assigned_route_id
            WHERE br1.is_active = true AND br2.is_active = true
            GROUP BY pt.first_route_id, pt.from_stop_id, pt.transfer_stop_id, pt.second_route_id, pt.to_stop_id,
                     pt.transfer_stop_name, pt.transfer_lat, pt.transfer_lon, pt.transfer_is_major,
                     pt.first_route_minutes, pt.second_route_minutes, pt.first_route_distance, pt.second_route_distance,
                     br1.route_number, br1.route_name, br1.route_color,
                     br2.route_number, br2.route_name, br2.route_color,
                     bs_from.stop_name, bs_from.latitude, bs_from.longitude,
                     bs_to.stop_name, bs_to.latitude, bs_to.longitude
            ORDER BY (pt.first_route_minutes + pt.second_route_minutes), 
                     (first_route_vehicles + second_route_vehicles) DESC
            LIMIT 12
            """;

        String[] fromStopIds = fromStops.stream()
                .map(stop -> stop.getId().getValue())
                .toArray(String[]::new);
        String[] toStopIds = toStops.stream()
                .map(stop -> stop.getId().getValue())
                .toArray(String[]::new);

        return databaseClient.sql(sql)
                .bind("fromStopIds", fromStopIds)
                .bind("toStopIds", toStopIds)
                .map(row -> {
                    // Создаем первый маршрут
                    BusRoute firstRoute = new BusRoute(
                            row.get("first_route_number", String.class),
                            row.get("first_route_name", String.class),
                            null,
                            row.get("first_route_color", String.class) != null ?
                                    row.get("first_route_color", String.class) : "#1976D2"
                    );

                    // Создаем второй маршрут
                    BusRoute secondRoute = new BusRoute(
                            row.get("second_route_number", String.class),
                            row.get("second_route_name", String.class),
                            null,
                            row.get("second_route_color", String.class) != null ?
                                    row.get("second_route_color", String.class) : "#1976D2"
                    );

                    // Создаем остановки
                    BusStop fromStop = new BusStop(
                            row.get("from_stop_name", String.class),
                            row.get("from_stop_id", String.class),
                            row.get("from_lat", BigDecimal.class),
                            row.get("from_lon", BigDecimal.class)
                    );

                    BusStop transferStop = new BusStop(
                            row.get("transfer_stop_name", String.class),
                            row.get("transfer_stop_id", String.class),
                            row.get("transfer_lat", BigDecimal.class),
                            row.get("transfer_lon", BigDecimal.class)
                    );

                    // Устанавливаем свойства остановки пересадки
                    transferStop = new BusStop(
                            transferStop.getId(),
                            transferStop.getStopName(),
                            transferStop.getStopCode(),
                            transferStop.getLatitude(),
                            transferStop.getLongitude(),
                            true, // is_active
                            row.get("transfer_is_major", Boolean.class), // is_major_stop
                            false // has_shelter
                    );

                    BusStop toStop = new BusStop(
                            row.get("to_stop_name", String.class),
                            row.get("to_stop_id", String.class),
                            row.get("to_lat", BigDecimal.class),
                            row.get("to_lon", BigDecimal.class)
                    );

                    // Корректируем время на основе количества автобусов
                    Integer firstRouteMinutes = row.get("first_route_minutes", Integer.class);
                    Integer secondRouteMinutes = row.get("second_route_minutes", Integer.class);
                    Long firstRouteVehicles = row.get("first_route_vehicles", Long.class);
                    Long secondRouteVehicles = row.get("second_route_vehicles", Long.class);

                    int adjustedFirstMinutes = adjustTravelTimeByVehicleCount(firstRouteMinutes, firstRouteVehicles);
                    int adjustedSecondMinutes = adjustTravelTimeByVehicleCount(secondRouteMinutes, secondRouteVehicles);

                    // Рассчитываем время ожидания на пересадке
                    int transferWaitTime = calculateTransferWaitTime(transferStop.getIsMajorStop(),
                            firstRouteVehicles, secondRouteVehicles);

                    return new TransferRouteResult(
                            firstRoute, fromStop, transferStop,
                            secondRoute, toStop,
                            adjustedFirstMinutes,
                            transferWaitTime,
                            adjustedSecondMinutes,
                            0.0, 0.0 // Walking distances будут рассчитаны отдельно
                    );
                })
                .all()
                .doOnComplete(() -> log.debug("One transfer routes search completed"));
    }

    @Override
    public Flux<TwoTransferRouteResult> findRoutesWithTwoTransfers(List<BusStop> fromStops, List<BusStop> toStops,
                                                                   double maxTransferDistanceKm) {
        long startTime = System.currentTimeMillis();

        log.debug("Starting two-transfer search: {} origin stops, {} destination stops, max distance: {}km",
                fromStops.size(), toStops.size(), maxTransferDistanceKm);

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            return Flux.empty();
        }

        double optimalDistance = getOptimalTransferDistance(fromStops, toStops, maxTransferDistanceKm);

        // Создаем type-safe кэш ключ
        String cacheKey = String.format("two_transfers:v2:%s:%s:%.1f",
                fromStops.stream().map(s -> s.getId().getValue()).sorted().collect(Collectors.joining(",")),
                toStops.stream().map(s -> s.getId().getValue()).sorted().collect(Collectors.joining(",")),
                optimalDistance);

        return getCachedTwoTransferResults(cacheKey)
                .switchIfEmpty(
                        performTwoTransferSearch(fromStops, toStops, optimalDistance)
                                .filter(this::isTwoTransferRouteReasonable)
                                .collectList()
                                .flatMapMany(results -> {
                                    long searchTime = System.currentTimeMillis() - startTime;
                                    logTwoTransferSearchStats(fromStops, toStops, results, searchTime);

                                    return cacheTwoTransferResults(cacheKey, results)
                                            .thenMany(Flux.fromIterable(results))
                                            .onErrorResume(e -> {
                                                log.warn("Failed to cache two-transfer results: {}", e.getMessage());
                                                return Flux.fromIterable(results); // Возвращаем результаты даже при ошибке кэширования
                                            });
                                })
                )
                .onErrorResume(throwable -> {
                    log.error("Error in two-transfer search: {}", throwable.getMessage(), throwable);
                    return Flux.empty();
                })
                .doOnComplete(() -> {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log.debug("Two-transfer search completed in {}ms", totalTime);
                });
    }


    /**
     * Type-safe получение результатов из кэша с JSON десериализацией
     */
    private Flux<TwoTransferRouteResult> getCachedTwoTransferResults(String cacheKey) {
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(String.class) // Кэшируем как JSON строку
                .flatMapMany(jsonString -> {
                    try {
                        log.debug("Found cached two-transfer routes");

                        // Десериализуем JSON в типизированный список
                        ObjectMapper objectMapper = new ObjectMapper();
                        TypeReference<List<TwoTransferRouteResultDTO>> typeRef = new TypeReference<>() {};
                        List<TwoTransferRouteResultDTO> dtoList = objectMapper.readValue(jsonString, typeRef);

                        // Конвертируем DTO обратно в domain objects
                        List<TwoTransferRouteResult> results = dtoList.stream()
                                .map(this::convertDTOToTwoTransferResult)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        return Flux.fromIterable(results);

                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize cached two-transfer results: {}", e.getMessage());
                        // Если десериализация не удалась, удаляем некорректный кэш
                        return redisTemplate.delete(cacheKey).thenMany(Flux.empty());
                    }
                })
                .onErrorResume(e -> {
                    log.debug("No valid cached results found: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Type-safe сохранение результатов в кэш с JSON сериализацией
     */
    private Mono<Boolean> cacheTwoTransferResults(String cacheKey, List<TwoTransferRouteResult> results) {
        if (results.isEmpty()) {
            return Mono.just(false);
        }

        return Mono.fromCallable(() -> {
                    try {
                        // Конвертируем domain objects в DTO для сериализации
                        List<TwoTransferRouteResultDTO> dtoList = results.stream()
                                .map(this::convertTwoTransferResultToDTO)
                                .collect(Collectors.toList());

                        // Сериализуем в JSON
                        ObjectMapper objectMapper = new ObjectMapper();
                        return objectMapper.writeValueAsString(dtoList);

                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize two-transfer results for caching: {}", e.getMessage());
                        throw new RuntimeException("Cache serialization failed", e);
                    }
                })
                .flatMap(jsonString ->
                        redisTemplate.opsForValue()
                                .set(cacheKey, jsonString, Duration.ofMinutes(10))
                                .doOnSuccess(success -> {
                                    if (Boolean.TRUE.equals(success)) {
                                        log.debug("Cached {} two-transfer results", results.size());
                                    }
                                })
                )
                .onErrorReturn(false);
    }



    private Flux<TwoTransferRouteResult> performTwoTransferSearch(List<BusStop> fromStops, List<BusStop> toStops,
                                                                  double maxTransferDistanceKm) {

        // Сложный SQL с тремя уровнями CTE для оптимизации
        String sql = """
        WITH 
        -- Первый уровень: находим все возможные первые сегменты (A → transfer1)
        first_segments AS (
            SELECT DISTINCT
                rs1.route_id as first_route_id,
                rs1.stop_id as from_stop_id,
                rs2.stop_id as first_transfer_stop_id,
                rs1.direction as first_direction,
                bs_t1.stop_name as first_transfer_name,
                bs_t1.latitude as first_transfer_lat,
                bs_t1.longitude as first_transfer_lon,
                bs_t1.is_major_stop as first_transfer_is_major,
                ABS(rs2.stop_sequence - rs1.stop_sequence) * 2 as first_route_minutes,
                ABS(rs2.distance_from_start_meters - rs1.distance_from_start_meters) as first_route_distance
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id 
                                AND rs1.direction = rs2.direction
                                AND rs1.stop_sequence < rs2.stop_sequence
                                AND (rs2.stop_sequence - rs1.stop_sequence) <= 12  -- Максимум 12 остановок
            JOIN bus_stops bs_t1 ON rs2.stop_id = bs_t1.id
            JOIN bus_routes br1 ON rs1.route_id = br1.id
            WHERE rs1.stop_id = ANY(:fromStopIds)
            AND br1.is_active = true
            AND bs_t1.is_active = true
        ),
        
        -- Второй уровень: находим вторые сегменты (transfer1 → transfer2)
        second_segments AS (
            SELECT DISTINCT
                fs.first_route_id,
                fs.from_stop_id,
                fs.first_transfer_stop_id,
                fs.first_transfer_name,
                fs.first_transfer_lat,
                fs.first_transfer_lon,
                fs.first_transfer_is_major,
                fs.first_route_minutes,
                rs3.route_id as second_route_id,
                rs4.stop_id as second_transfer_stop_id,
                rs3.direction as second_direction,
                bs_t2.stop_name as second_transfer_name,
                bs_t2.latitude as second_transfer_lat,
                bs_t2.longitude as second_transfer_lon,
                bs_t2.is_major_stop as second_transfer_is_major,
                ABS(rs4.stop_sequence - rs3.stop_sequence) * 2 as second_route_minutes,
                ABS(rs4.distance_from_start_meters - rs3.distance_from_start_meters) as second_route_distance,
                -- Рассчитываем расстояние между первой и второй пересадкой
                ST_Distance(
                    ST_Point(fs.first_transfer_lon, fs.first_transfer_lat)::geography,
                    ST_Point(bs_t2.longitude, bs_t2.latitude)::geography
                ) as transfer_distance_meters
            FROM first_segments fs
            JOIN route_stops rs3 ON fs.first_transfer_stop_id = rs3.stop_id
            JOIN route_stops rs4 ON rs3.route_id = rs4.route_id 
                                AND rs3.direction = rs4.direction
                                AND rs3.stop_sequence < rs4.stop_sequence
                                AND (rs4.stop_sequence - rs3.stop_sequence) <= 10  -- Максимум 10 остановок для средних сегментов
            JOIN bus_stops bs_t2 ON rs4.stop_id = bs_t2.id
            JOIN bus_routes br2 ON rs3.route_id = br2.id
            WHERE fs.first_route_id != rs3.route_id  -- Разные маршруты
            AND br2.is_active = true
            AND bs_t2.is_active = true
            -- Ограничиваем расстояние между пересадками
            AND ST_Distance(
                ST_Point(fs.first_transfer_lon, fs.first_transfer_lat)::geography,
                ST_Point(bs_t2.longitude, bs_t2.latitude)::geography
            ) <= :maxTransferDistanceMeters
        ),
        
        -- Третий уровень: находим финальные сегменты (transfer2 → B)
        complete_routes AS (
            SELECT DISTINCT
                ss.*,
                rs5.route_id as third_route_id,
                rs6.stop_id as to_stop_id,
                rs5.direction as third_direction,
                ABS(rs6.stop_sequence - rs5.stop_sequence) * 2 as third_route_minutes,
                ABS(rs6.distance_from_start_meters - rs5.distance_from_start_meters) as third_route_distance
            FROM second_segments ss
            JOIN route_stops rs5 ON ss.second_transfer_stop_id = rs5.stop_id
            JOIN route_stops rs6 ON rs5.route_id = rs6.route_id 
                                AND rs5.direction = rs6.direction
                                AND rs5.stop_sequence < rs6.stop_sequence
                                AND (rs6.stop_sequence - rs5.stop_sequence) <= 12  -- Максимум 12 остановок для финального сегмента
            JOIN bus_routes br3 ON rs5.route_id = br3.id
            WHERE ss.second_route_id != rs5.route_id  -- Третий маршрут отличается от второго
            AND rs6.stop_id = ANY(:toStopIds)
            AND br3.is_active = true
        )
        
        -- Финальный SELECT с дополнительной информацией о маршрутах
        SELECT 
            cr.*,
            br1.route_number as first_route_number,
            br1.route_name as first_route_name,
            br1.route_color as first_route_color,
            br2.route_number as second_route_number,
            br2.route_name as second_route_name,
            br2.route_color as second_route_color,
            br3.route_number as third_route_number,
            br3.route_name as third_route_name,
            br3.route_color as third_route_color,
            bs_from.stop_name as from_stop_name,
            bs_from.latitude as from_lat,
            bs_from.longitude as from_lon,
            bs_to.stop_name as to_stop_name,
            bs_to.latitude as to_lat,
            bs_to.longitude as to_lon,
            COUNT(v1.id) FILTER (WHERE v1.is_active = true) as first_route_vehicles,
            COUNT(v2.id) FILTER (WHERE v2.is_active = true) as second_route_vehicles,
            COUNT(v3.id) FILTER (WHERE v3.is_active = true) as third_route_vehicles,
            -- Общее время поездки для сортировки
            (cr.first_route_minutes + cr.second_route_minutes + cr.third_route_minutes) as total_travel_time
        FROM complete_routes cr
        JOIN bus_routes br1 ON cr.first_route_id = br1.id
        JOIN bus_routes br2 ON cr.second_route_id = br2.id
        JOIN bus_routes br3 ON cr.third_route_id = br3.id
        JOIN bus_stops bs_from ON cr.from_stop_id = bs_from.id
        JOIN bus_stops bs_to ON cr.to_stop_id = bs_to.id
        LEFT JOIN vehicles v1 ON br1.id = v1.assigned_route_id
        LEFT JOIN vehicles v2 ON br2.id = v2.assigned_route_id
        LEFT JOIN vehicles v3 ON br3.id = v3.assigned_route_id
        WHERE 
            -- Фильтруем неразумные маршруты
            (cr.first_route_minutes + cr.second_route_minutes + cr.third_route_minutes) <= 90  -- Максимум 90 минут общего времени
            AND cr.first_route_minutes >= 3   -- Минимум 3 минуты на каждый сегмент
            AND cr.second_route_minutes >= 3
            AND cr.third_route_minutes >= 3
        GROUP BY 
            cr.first_route_id, cr.from_stop_id, cr.first_transfer_stop_id, cr.second_route_id, 
            cr.second_transfer_stop_id, cr.third_route_id, cr.to_stop_id,
            cr.first_transfer_name, cr.first_transfer_lat, cr.first_transfer_lon, cr.first_transfer_is_major,
            cr.second_transfer_name, cr.second_transfer_lat, cr.second_transfer_lon, cr.second_transfer_is_major,
            cr.first_route_minutes, cr.second_route_minutes, cr.third_route_minutes, cr.transfer_distance_meters,
            br1.route_number, br1.route_name, br1.route_color,
            br2.route_number, br2.route_name, br2.route_color,
            br3.route_number, br3.route_name, br3.route_color,
            bs_from.stop_name, bs_from.latitude, bs_from.longitude,
            bs_to.stop_name, bs_to.latitude, bs_to.longitude
        ORDER BY 
            -- Сортировка по качеству маршрута
            total_travel_time,  -- Сначала самые быстрые
            (first_route_vehicles + second_route_vehicles + third_route_vehicles) DESC,  -- Потом с большим количеством автобусов
            (CASE WHEN cr.first_transfer_is_major THEN 0 ELSE 1 END + 
             CASE WHEN cr.second_transfer_is_major THEN 0 ELSE 1 END)  -- Приоритет крупным остановкам
        LIMIT 6  -- Ограничиваем количество для производительности (два перевода = сложный поиск)
        """;

        String[] fromStopIds = fromStops.stream()
                .map(stop -> stop.getId().getValue())
                .toArray(String[]::new);
        String[] toStopIds = toStops.stream()
                .map(stop -> stop.getId().getValue())
                .toArray(String[]::new);

        return databaseClient.sql(sql)
                .bind("fromStopIds", fromStopIds)
                .bind("toStopIds", toStopIds)
                .bind("maxTransferDistanceMeters", maxTransferDistanceKm * 1000) // Конвертируем км в метры
                .map(row -> {
                    try {
                        // Создаем три маршрута
                        BusRoute firstRoute = new BusRoute(
                                row.get("first_route_number", String.class),
                                row.get("first_route_name", String.class),
                                null,
                                row.get("first_route_color", String.class) != null ?
                                        row.get("first_route_color", String.class) : "#1976D2"
                        );

                        BusRoute secondRoute = new BusRoute(
                                row.get("second_route_number", String.class),
                                row.get("second_route_name", String.class),
                                null,
                                row.get("second_route_color", String.class) != null ?
                                        row.get("second_route_color", String.class) : "#4CAF50"
                        );

                        BusRoute thirdRoute = new BusRoute(
                                row.get("third_route_number", String.class),
                                row.get("third_route_name", String.class),
                                null,
                                row.get("third_route_color", String.class) != null ?
                                        row.get("third_route_color", String.class) : "#FF9800"
                        );

                        // Создаем остановки
                        BusStop fromStop = new BusStop(
                                row.get("from_stop_name", String.class),
                                row.get("from_stop_id", String.class),
                                row.get("from_lat", BigDecimal.class),
                                row.get("from_lon", BigDecimal.class)
                        );

                        BusStop firstTransferStop = new BusStop(
                                row.get("first_transfer_name", String.class),
                                row.get("first_transfer_stop_id", String.class),
                                row.get("first_transfer_lat", BigDecimal.class),
                                row.get("first_transfer_lon", BigDecimal.class)
                        );

                        // Устанавливаем is_major_stop для первой пересадки
                        firstTransferStop = new BusStop(
                                firstTransferStop.getId(),
                                firstTransferStop.getStopName(),
                                firstTransferStop.getStopCode(),
                                firstTransferStop.getLatitude(),
                                firstTransferStop.getLongitude(),
                                true, // is_active
                                row.get("first_transfer_is_major", Boolean.class), // is_major_stop
                                false // has_shelter
                        );

                        BusStop secondTransferStop = new BusStop(
                                row.get("second_transfer_name", String.class),
                                row.get("second_transfer_stop_id", String.class),
                                row.get("second_transfer_lat", BigDecimal.class),
                                row.get("second_transfer_lon", BigDecimal.class)
                        );

                        // Устанавливаем is_major_stop для второй пересадки
                        secondTransferStop = new BusStop(
                                secondTransferStop.getId(),
                                secondTransferStop.getStopName(),
                                secondTransferStop.getStopCode(),
                                secondTransferStop.getLatitude(),
                                secondTransferStop.getLongitude(),
                                true, // is_active
                                row.get("second_transfer_is_major", Boolean.class), // is_major_stop
                                false // has_shelter
                        );

                        BusStop toStop = new BusStop(
                                row.get("to_stop_name", String.class),
                                row.get("to_stop_id", String.class),
                                row.get("to_lat", BigDecimal.class),
                                row.get("to_lon", BigDecimal.class)
                        );

                        // Получаем времена поездки и корректируем их
                        Integer firstRouteMinutes = row.get("first_route_minutes", Integer.class);
                        Integer secondRouteMinutes = row.get("second_route_minutes", Integer.class);
                        Integer thirdRouteMinutes = row.get("third_route_minutes", Integer.class);

                        Long firstRouteVehicles = row.get("first_route_vehicles", Long.class);
                        Long secondRouteVehicles = row.get("second_route_vehicles", Long.class);
                        Long thirdRouteVehicles = row.get("third_route_vehicles", Long.class);

                        int adjustedFirstMinutes = adjustTravelTimeByVehicleCount(firstRouteMinutes, firstRouteVehicles);
                        int adjustedSecondMinutes = adjustTravelTimeByVehicleCount(secondRouteMinutes, secondRouteVehicles);
                        int adjustedThirdMinutes = adjustTravelTimeByVehicleCount(thirdRouteMinutes, thirdRouteVehicles);

                        // Рассчитываем времена ожидания на пересадках
                        int firstTransferWaitTime = calculateTransferWaitTime(
                                firstTransferStop.getIsMajorStop(), firstRouteVehicles, secondRouteVehicles);
                        int secondTransferWaitTime = calculateTransferWaitTime(
                                secondTransferStop.getIsMajorStop(), secondRouteVehicles, thirdRouteVehicles);

                        return new TwoTransferRouteResult(
                                firstRoute, fromStop, firstTransferStop,
                                secondRoute, secondTransferStop,
                                thirdRoute, toStop,
                                adjustedFirstMinutes,
                                firstTransferWaitTime,
                                adjustedSecondMinutes,
                                secondTransferWaitTime,
                                adjustedThirdMinutes,
                                0.0, 0.0 // Walking distances будут рассчитаны отдельно
                        );

                    } catch (Exception e) {
                        log.error("Error creating TwoTransferRouteResult: {}", e.getMessage(), e);
                        return null;
                    }
                })
                .all()
                .filter(Objects::nonNull)
                .doOnNext(result -> {
                    if (result != null) {
                        log.debug("Found two-transfer route: {}-{}-{} ({} + {} + {} = {} minutes)",
                                result.firstRoute().getRouteNumber(),
                                result.secondRoute().getRouteNumber(),
                                result.thirdRoute().getRouteNumber(),
                                result.firstRouteTravelMinutes(),
                                result.secondRouteTravelMinutes(),
                                result.thirdRouteTravelMinutes(),
                                result.firstRouteTravelMinutes() + result.secondRouteTravelMinutes() + result.thirdRouteTravelMinutes());
                    }
                });
    }

    @Override
    public Mono<Boolean> areStopsConnected(BusStop stop1, BusStop stop2) {
        String cacheKey = String.format("stops_connected:%s:%s",
                stop1.getId().getValue(), stop2.getId().getValue());

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(Boolean.class)
                .switchIfEmpty(
                        checkStopsConnectionInDatabase(stop1, stop2)
                                .flatMap(connected ->
                                        redisTemplate.opsForValue()
                                                .set(cacheKey, connected, Duration.ofHours(1))
                                                .thenReturn(connected)
                                )
                );
    }

    @Override
    public Flux<BusRoute> getConnectingRoutes(BusStop fromStop, BusStop toStop) {
        String sql = """
            SELECT DISTINCT br.*
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id 
                                AND rs1.direction = rs2.direction
                                AND rs1.stop_sequence < rs2.stop_sequence
            JOIN bus_routes br ON rs1.route_id = br.id
            WHERE rs1.stop_id = :fromStopId 
            AND rs2.stop_id = :toStopId
            AND br.is_active = true
            ORDER BY ABS(rs2.stop_sequence - rs1.stop_sequence)
            """;

        return databaseClient.sql(sql)
                .bind("fromStopId", fromStop.getId().getValue())
                .bind("toStopId", toStop.getId().getValue())
                .map(row -> new BusRoute(
                        row.get("route_number", String.class),
                        row.get("route_name", String.class),
                        row.get("route_name_tm", String.class),
                        row.get("route_color", String.class)
                ))
                .all();
    }

    // Приватные вспомогательные методы

    private Mono<Boolean> checkStopsConnectionInDatabase(BusStop stop1, BusStop stop2) {
        String sql = """
            SELECT COUNT(*) > 0 as connected
            FROM route_stops rs1
            JOIN route_stops rs2 ON rs1.route_id = rs2.route_id AND rs1.direction = rs2.direction
            WHERE rs1.stop_id = :stop1Id AND rs2.stop_id = :stop2Id
            """;

        return databaseClient.sql(sql)
                .bind("stop1Id", stop1.getId().getValue())
                .bind("stop2Id", stop2.getId().getValue())
                .map(row -> row.get("connected", Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    /**
     * Корректирует время поездки на основе количества активных автобусов на маршруте
     */
    private int adjustTravelTimeByVehicleCount(Integer baseMinutes, Long vehicleCount) {
        if (baseMinutes == null) return 20; // Дефолтное время
        if (vehicleCount == null || vehicleCount == 0) return baseMinutes + 10; // Добавляем время ожидания

        // Больше автобусов = меньше время ожидания = быстрее общее время
        if (vehicleCount >= 5) return baseMinutes; // Частый сервис
        if (vehicleCount >= 3) return baseMinutes + 2; // Хороший сервис
        if (vehicleCount >= 1) return baseMinutes + 5; // Редкий сервис

        return baseMinutes + 10; // Очень редкий сервис
    }

    /**
     * Рассчитывает время ожидания на пересадке
     */
    private int calculateTransferWaitTime(Boolean isMajorStop, Long fromRouteVehicles, Long toRouteVehicles) {
        // Базовое время пересадки
        int baseTransferTime = Boolean.TRUE.equals(isMajorStop) ? 3 : 5;

        // Учитываем частоту исходящего маршрута (с которого пересаживаемся)
        long fromVehicleCount = fromRouteVehicles != null ? fromRouteVehicles : 0;

        // Учитываем частоту целевого маршрута (на который пересаживаемся)
        long toVehicleCount = toRouteVehicles != null ? toRouteVehicles : 0;

        int waitTime;
        if (toVehicleCount >= 5) {
            waitTime = 4; // Очень частый сервис
        } else if (toVehicleCount >= 3) {
            waitTime = 7; // Хороший сервис
        } else if (toVehicleCount >= 1) {
            waitTime = 12; // Редкий сервис
        } else {
            waitTime = 18; // Очень редкий сервис
        }

        // Корректировка в зависимости от частоты исходящего маршрута
        // (более частые маршруты дают больше гибкости во времени пересадки)
        if (fromVehicleCount >= 5) {
            waitTime -= 2; // Можем подгадать время пересадки
        } else if (fromVehicleCount >= 3) {
            waitTime -= 1;
        }

        // Дополнительное время для двух пересадок (больше неопределенности)
        int uncertaintyTime = 2;

        return Math.max(baseTransferTime + waitTime + uncertaintyTime, 5); // Минимум 5 минут
    }

    private boolean isTwoTransferRouteReasonable(TwoTransferRouteResult route) {
        // Общее время поездки не должно превышать 2 часа
        int totalTime = route.firstRouteTravelMinutes() +
                route.firstTransferWaitMinutes() +
                route.secondRouteTravelMinutes() +
                route.secondTransferWaitMinutes() +
                route.thirdRouteTravelMinutes();

        if (totalTime > 120) {
            log.debug("Two-transfer route rejected: total time {} minutes", totalTime);
            return false;
        }

        // Каждое ожидание не должно быть слишком долгим
        if (route.firstTransferWaitMinutes() > 20 || route.secondTransferWaitMinutes() > 20) {
            log.debug("Two-transfer route rejected: wait times too long ({}, {})",
                    route.firstTransferWaitMinutes(), route.secondTransferWaitMinutes());
            return false;
        }

        // Каждый сегмент поездки должен быть значимым
        if (route.firstRouteTravelMinutes() < 3 ||
                route.secondRouteTravelMinutes() < 3 ||
                route.thirdRouteTravelMinutes() < 3) {
            log.debug("Two-transfer route rejected: segments too short ({}, {}, {})",
                    route.firstRouteTravelMinutes(), route.secondRouteTravelMinutes(), route.thirdRouteTravelMinutes());
            return false;
        }

        // Проверяем что маршруты разные
        if (route.firstRoute().getRouteNumber().equals(route.secondRoute().getRouteNumber()) ||
                route.secondRoute().getRouteNumber().equals(route.thirdRoute().getRouteNumber()) ||
                route.firstRoute().getRouteNumber().equals(route.thirdRoute().getRouteNumber())) {
            log.debug("Two-transfer route rejected: duplicate routes {}-{}-{}",
                    route.firstRoute().getRouteNumber(), route.secondRoute().getRouteNumber(), route.thirdRoute().getRouteNumber());
            return false;
        }

        return true;
    }

    private double getOptimalTransferDistance(List<BusStop> fromStops, List<BusStop> toStops, double maxDistance) {
        // Если остановок много, можем быть более селективными
        if (fromStops.size() > 6 && toStops.size() > 6) {
            return Math.min(maxDistance, 0.3); // 300м для хорошего выбора остановок
        }

        // Если остановок мало, увеличиваем радиус поиска
        if (fromStops.size() <= 3 || toStops.size() <= 3) {
            return Math.min(maxDistance, 0.5); // 500м для ограниченного выбора
        }

        return Math.min(maxDistance, 0.4); // 400м по умолчанию
    }

    /**
     * Оценивает качество маршрута с двумя пересадками для приоритизации
     */
    private double calculateTwoTransferRouteScore(TwoTransferRouteResult route) {
        // Базовая оценка - обратная к общему времени (меньше времени = лучше)
        int totalTime = route.firstRouteTravelMinutes() +
                route.firstTransferWaitMinutes() +
                route.secondRouteTravelMinutes() +
                route.secondTransferWaitMinutes() +
                route.thirdRouteTravelMinutes();

        double timeScore = 100.0 / totalTime; // Нормализуем по времени

        // Бонус за крупные остановки пересадки
        double transferStopBonus = 0;
        if (Boolean.TRUE.equals(route.firstTransferStop().getIsMajorStop())) {
            transferStopBonus += 0.1;
        }
        if (Boolean.TRUE.equals(route.secondTransferStop().getIsMajorStop())) {
            transferStopBonus += 0.1;
        }

        // Бонус за маршруты с большим количеством автобусов (через метаданные, если доступны)
        double frequencyBonus = 0.05; // Базовый бонус

        // Штраф за очень долгие ожидания
        double waitPenalty = 0;
        if (route.firstTransferWaitMinutes() > 15) {
            waitPenalty += 0.1;
        }
        if (route.secondTransferWaitMinutes() > 15) {
            waitPenalty += 0.1;
        }

        return timeScore + transferStopBonus + frequencyBonus - waitPenalty;
    }

    /**
     * Проверка доступности метода поиска с двумя пересадками
     * Можно вызвать для диагностики
     */
    public Mono<Boolean> isTwoTransferSearchAvailable() {
        String testSql = "SELECT ST_Distance(ST_Point(0, 0)::geography, ST_Point(1, 1)::geography) AS test_distance";

        return databaseClient.sql(testSql)
                .map(row -> row.get("test_distance", Double.class))
                .one()
                .map(distance -> distance != null && distance > 0)
                .onErrorReturn(false)
                .doOnNext(available -> {
                    if (available) {
                        log.info("Two-transfer search is available with PostGIS support");
                    } else {
                        log.warn("Two-transfer search may not work properly - PostGIS functions unavailable");
                    }
                });
    }
    private void logTwoTransferSearchStats(List<BusStop> fromStops, List<BusStop> toStops,
                                           List<TwoTransferRouteResult> results,
                                           long searchTimeMs) {
        if (results.isEmpty()) {
            log.info("Two-transfer search: {} origin stops × {} destination stops → 0 results in {}ms",
                    fromStops.size(), toStops.size(), searchTimeMs);
        } else {
            TwoTransferRouteResult best = results.get(0);
            int bestTotalTime = best.firstRouteTravelMinutes() + best.firstTransferWaitMinutes() +
                    best.secondRouteTravelMinutes() + best.secondTransferWaitMinutes() +
                    best.thirdRouteTravelMinutes();

            log.info("Two-transfer search: {} origin stops × {} destination stops → {} results in {}ms. Best: {}-{}-{} ({}min)",
                    fromStops.size(), toStops.size(), results.size(), searchTimeMs,
                    best.firstRoute().getRouteNumber(), best.secondRoute().getRouteNumber(),
                    best.thirdRoute().getRouteNumber(), bestTotalTime);
        }
    }


    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwoTransferRouteResultDTO {
        public RouteDTO firstRoute;
        public BusStopDTO fromStop;
        public BusStopDTO firstTransferStop;
        public RouteDTO secondRoute;
        public BusStopDTO secondTransferStop;
        public RouteDTO thirdRoute;
        public BusStopDTO toStop;
        public int firstRouteTravelMinutes;
        public int firstTransferWaitMinutes;
        public int secondRouteTravelMinutes;
        public int secondTransferWaitMinutes;
        public int thirdRouteTravelMinutes;
        public double walkingDistanceToStart;
        public double walkingDistanceFromEnd;

        // Конструкторы, геттеры, сеттеры
        public TwoTransferRouteResultDTO() {}

        public TwoTransferRouteResultDTO(RouteDTO firstRoute,
                                         BusStopDTO fromStop,
                                         BusStopDTO firstTransferStop,
                                         RouteDTO secondRoute,
                                         BusStopDTO secondTransferStop,
                                         RouteDTO thirdRoute,
                                         BusStopDTO toStop,
                                         int firstRouteTravelMinutes,
                                         int firstTransferWaitMinutes,
                                         int secondRouteTravelMinutes,
                                         int secondTransferWaitMinutes,
                                         int thirdRouteTravelMinutes,
                                         double walkingDistanceToStart,
                                         double walkingDistanceFromEnd) {
            this.firstRoute = firstRoute;
            this.fromStop = fromStop;
            this.firstTransferStop = firstTransferStop;
            this.secondRoute = secondRoute;
            this.secondTransferStop = secondTransferStop;
            this.thirdRoute = thirdRoute;
            this.toStop = toStop;
            this.firstRouteTravelMinutes = firstRouteTravelMinutes;
            this.firstTransferWaitMinutes = firstTransferWaitMinutes;
            this.secondRouteTravelMinutes = secondRouteTravelMinutes;
            this.secondTransferWaitMinutes = secondTransferWaitMinutes;
            this.thirdRouteTravelMinutes = thirdRouteTravelMinutes;
            this.walkingDistanceToStart = walkingDistanceToStart;
            this.walkingDistanceFromEnd = walkingDistanceFromEnd;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouteDTO {
        public String routeNumber;
        public String routeName;
        public String routeColor;

        public RouteDTO() {}

        public RouteDTO(String routeNumber, String routeName, String routeColor) {
            this.routeNumber = routeNumber;
            this.routeName = routeName;
            this.routeColor = routeColor;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BusStopDTO {
        public String id;
        public String stopName;
        public double latitude;
        public double longitude;
        public boolean isMajorStop;

        public BusStopDTO() {}

        public BusStopDTO(String id, String stopName, double latitude, double longitude, boolean isMajorStop) {
            this.id = id;
            this.stopName = stopName;
            this.latitude = latitude;
            this.longitude = longitude;
            this.isMajorStop = isMajorStop;
        }
    }



    /**
     * Конвертер Domain Object → DTO
     */
    private TwoTransferRouteResultDTO convertTwoTransferResultToDTO(TwoTransferRouteResult result) {
        TwoTransferRouteResultDTO dto = new TwoTransferRouteResultDTO();

        dto.firstRoute = new RouteDTO(
                result.firstRoute().getRouteNumber(),
                result.firstRoute().getRouteName(),
                result.firstRoute().getRouteColor()
        );

        dto.secondRoute = new RouteDTO(
                result.secondRoute().getRouteNumber(),
                result.secondRoute().getRouteName(),
                result.secondRoute().getRouteColor()
        );

        dto.thirdRoute = new RouteDTO(
                result.thirdRoute().getRouteNumber(),
                result.thirdRoute().getRouteName(),
                result.thirdRoute().getRouteColor()
        );

        dto.fromStop = new BusStopDTO(
                result.fromStop().getId().getValue(),
                result.fromStop().getStopName(),
                result.fromStop().getLatitude().doubleValue(),
                result.fromStop().getLongitude().doubleValue(),
                result.fromStop().getIsMajorStop()
        );

        dto.firstTransferStop = new BusStopDTO(
                result.firstTransferStop().getId().getValue(),
                result.firstTransferStop().getStopName(),
                result.firstTransferStop().getLatitude().doubleValue(),
                result.firstTransferStop().getLongitude().doubleValue(),
                result.firstTransferStop().getIsMajorStop()
        );

        dto.secondTransferStop = new BusStopDTO(
                result.secondTransferStop().getId().getValue(),
                result.secondTransferStop().getStopName(),
                result.secondTransferStop().getLatitude().doubleValue(),
                result.secondTransferStop().getLongitude().doubleValue(),
                result.secondTransferStop().getIsMajorStop()
        );

        dto.toStop = new BusStopDTO(
                result.toStop().getId().getValue(),
                result.toStop().getStopName(),
                result.toStop().getLatitude().doubleValue(),
                result.toStop().getLongitude().doubleValue(),
                result.toStop().getIsMajorStop()
        );

        dto.firstRouteTravelMinutes = result.firstRouteTravelMinutes();
        dto.firstTransferWaitMinutes = result.firstTransferWaitMinutes();
        dto.secondRouteTravelMinutes = result.secondRouteTravelMinutes();
        dto.secondTransferWaitMinutes = result.secondTransferWaitMinutes();
        dto.thirdRouteTravelMinutes = result.thirdRouteTravelMinutes();
        dto.walkingDistanceToStart = result.walkingDistanceToStart();
        dto.walkingDistanceFromEnd = result.walkingDistanceFromEnd();

        return dto;
    }

    /**
     * Конвертер DTO → Domain Object
     */
    private TwoTransferRouteResult convertDTOToTwoTransferResult(TwoTransferRouteResultDTO dto) {
        try {
            BusRoute firstRoute = new BusRoute(
                    dto.firstRoute.routeNumber,
                    dto.firstRoute.routeName,
                    null,
                    dto.firstRoute.routeColor
            );

            BusRoute secondRoute = new BusRoute(
                    dto.secondRoute.routeNumber,
                    dto.secondRoute.routeName,
                    null,
                    dto.secondRoute.routeColor
            );

            BusRoute thirdRoute = new BusRoute(
                    dto.thirdRoute.routeNumber,
                    dto.thirdRoute.routeName,
                    null,
                    dto.thirdRoute.routeColor
            );

            BusStop fromStop = new BusStop(
                    dto.fromStop.stopName,
                    dto.fromStop.id,
                    BigDecimal.valueOf(dto.fromStop.latitude),
                    BigDecimal.valueOf(dto.fromStop.longitude)
            );

            BusStop firstTransferStop = new BusStop(
                    dto.firstTransferStop.stopName,
                    dto.firstTransferStop.id,
                    BigDecimal.valueOf(dto.firstTransferStop.latitude),
                    BigDecimal.valueOf(dto.firstTransferStop.longitude)
            );

            BusStop secondTransferStop = new BusStop(
                    dto.secondTransferStop.stopName,
                    dto.secondTransferStop.id,
                    BigDecimal.valueOf(dto.secondTransferStop.latitude),
                    BigDecimal.valueOf(dto.secondTransferStop.longitude)
            );

            BusStop toStop = new BusStop(
                    dto.toStop.stopName,
                    dto.toStop.id,
                    BigDecimal.valueOf(dto.toStop.latitude),
                    BigDecimal.valueOf(dto.toStop.longitude)
            );

            return new TwoTransferRouteResult(
                    firstRoute, fromStop, firstTransferStop,
                    secondRoute, secondTransferStop,
                    thirdRoute, toStop,
                    dto.firstRouteTravelMinutes,
                    dto.firstTransferWaitMinutes,
                    dto.secondRouteTravelMinutes,
                    dto.secondTransferWaitMinutes,
                    dto.thirdRouteTravelMinutes,
                    dto.walkingDistanceToStart,
                    dto.walkingDistanceFromEnd
            );

        } catch (Exception e) {
            log.warn("Failed to convert DTO to TwoTransferRouteResult: {}", e.getMessage());
            return null;
        }
    }

    public record TwoTransferSearchStats(
            Long totalSearches,
            Double averageTimeMs,
            Long cacheHits,
            Long cacheMisses
    ) {
        public double getCacheHitRate() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total * 100.0 : 0.0;
        }
    }
}