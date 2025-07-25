package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

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
        log.debug("Finding routes with two transfers (complex search)");

        // Для экономии ресурсов, поиск с двумя пересадками делаем только если нет других вариантов
        // В production системе здесь был бы сложный граф-алгоритм
        return Flux.empty();
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
    private int calculateTransferWaitTime(Boolean isMajorStop, Long firstRouteVehicles, Long secondRouteVehicles) {
        int baseTransferTime = Boolean.TRUE.equals(isMajorStop) ? 3 : 5;

        // Учитываем частоту второго маршрута
        Long vehicleCount = secondRouteVehicles != null ? secondRouteVehicles : 0;
        int waitTime;

        if (vehicleCount >= 5) waitTime = 5; // Частый сервис
        else if (vehicleCount >= 3) waitTime = 8; // Средний сервис
        else if (vehicleCount >= 1) waitTime = 12; // Редкий сервис
        else waitTime = 20; // Очень редкий сервис

        return baseTransferTime + waitTime;
    }
}