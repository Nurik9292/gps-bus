package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.geospatial.domain.constants.GeoConstants;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Live ETA calculation service with real-time data and traffic conditions.
 * Now uses centralized GeoConstants for all geospatial parameters.
 *
 * @since 1.5.0 (Phase 3 - migrated to use GeoConstants)
 */
@Service
@Slf4j
public class LiveETACalculationService implements ETACalculationService {

    private final VehicleRepository vehicleRepository;
    private final DatabaseClient databaseClient;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final DistanceCalculationService distanceService;

    // Use centralized constants from GeoConstants
    private static final double AVERAGE_WALKING_SPEED_KMH = GeoConstants.AVERAGE_WALKING_SPEED_KMH;
    private static final double AVERAGE_WALKING_SPEED_M_PER_MIN = GeoConstants.AVERAGE_WALKING_SPEED_M_PER_MIN;
    private static final int MAX_REASONABLE_WALKING_TIME = GeoConstants.MAX_WALKING_TIME_MINUTES;
    private static final int MIN_WALKING_TIME = GeoConstants.MIN_WALKING_TIME_MINUTES;

    public LiveETACalculationService(VehicleRepository vehicleRepository,
                                     DatabaseClient databaseClient,
                                     ReactiveRedisTemplate<String, Object> redisTemplate,
                                     DistanceCalculationService distanceService) {
        this.vehicleRepository = vehicleRepository;
        this.databaseClient = databaseClient;
        this.redisTemplate = redisTemplate;
        this.distanceService = distanceService;
    }

    @Override
    public Mono<LocalDateTime> calculateEstimatedArrival(String routeNumber, String fromStopName,
                                                         String toStopName, LocalDateTime departureTime) {
        log.debug("Calculating ETA for route {} from {} to {} departing at {}",
                routeNumber, fromStopName, toStopName, departureTime);

        return calculateTravelTimeMinutes(routeNumber, fromStopName, toStopName)
                .map(travelMinutes -> {
                    int waitingTime = calculateBaseWaitingTime(departureTime);
                    return departureTime.plusMinutes(waitingTime + travelMinutes);
                })
                .doOnNext(eta -> log.debug("Calculated ETA: {}", eta));
    }

    @Override
    public int calculateWalkingTimeMinutes(Coordinates from, Coordinates to) {
        double distanceMeters = distanceService.calculateDistance(
                from.getLatitudeAsDouble(), from.getLongitudeAsDouble(),
                to.getLatitudeAsDouble(), to.getLongitudeAsDouble()
        ).getMeters();

        // Базовый расчет времени ходьбы
        int walkingMinutes = (int) Math.ceil(distanceMeters / AVERAGE_WALKING_SPEED_M_PER_MIN);

        // Корректировки для условий города
        walkingMinutes = applyWalkingConditionsCorrection(walkingMinutes, distanceMeters);

        // Ограничиваем разумными пределами
        walkingMinutes = Math.max(MIN_WALKING_TIME, Math.min(MAX_REASONABLE_WALKING_TIME, walkingMinutes));

        log.trace("Walking time from {} to {}: {} minutes ({}m)",
                from, to, walkingMinutes, Math.round(distanceMeters));

        return walkingMinutes;
    }

    @Override
    public Mono<Integer> calculateWaitingTimeMinutes(String routeNumber, String stopName, LocalDateTime currentTime) {
        log.debug("Calculating wait time for route {} at stop {} at {}", routeNumber, stopName, currentTime);

        String cacheKey = String.format("wait_time:%s:%s:%d", routeNumber, stopName,
                currentTime.getHour());

        // Проверяем кэш для времени ожидания
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(Integer.class)
                .switchIfEmpty(
                        // Если нет в кэше, рассчитываем
                        calculateWaitingTimeFromData(routeNumber, stopName, currentTime)
                                .flatMap(waitTime ->
                                        // Кэшируем на 15 минут
                                        redisTemplate.opsForValue()
                                                .set(cacheKey, waitTime, Duration.ofMinutes(15))
                                                .thenReturn(waitTime)
                                )
                )
                .doOnNext(waitMinutes -> log.debug("Estimated wait time: {} minutes", waitMinutes));
    }

    @Override
    public Mono<Integer> calculateTravelTimeMinutes(String routeNumber, String fromStopName, String toStopName) {
        log.debug("Calculating travel time for route {} from {} to {}", routeNumber, fromStopName, toStopName);

        String cacheKey = String.format("travel_time:%s:%s:%s", routeNumber, fromStopName, toStopName);

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(Integer.class)
                .switchIfEmpty(
                        calculateTravelTimeFromDatabase(routeNumber, fromStopName, toStopName)
                                .flatMap(travelTime ->
                                        redisTemplate.opsForValue()
                                                .set(cacheKey, travelTime, Duration.ofHours(1))
                                                .thenReturn(travelTime)
                                )
                )
                .doOnNext(travelMinutes -> log.debug("Estimated travel time: {} minutes", travelMinutes));
    }

    @Override
    public int calculateTransferTimeMinutes(String stopName, boolean isMajorStop) {
        // Базовое время пересадки
        int baseTransferTime = isMajorStop ? 3 : 5;

        // Дополнительные корректировки на основе типа остановки
        int additionalTime = 0;

        if (stopName != null) {
            String lowerStopName = stopName.toLowerCase();

            // Крупные транспортные узлы требуют больше времени
            if (lowerStopName.contains("аэропорт") || lowerStopName.contains("airport")) {
                additionalTime += 3;
            } else if (lowerStopName.contains("базар") || lowerStopName.contains("рынок") ||
                    lowerStopName.contains("bazaar") || lowerStopName.contains("market")) {
                additionalTime += 2;
            } else if (lowerStopName.contains("центр") || lowerStopName.contains("center")) {
                additionalTime += 2;
            }
        }

        int totalTransferTime = baseTransferTime + additionalTime;

        log.trace("Transfer time at {} (major: {}): {} minutes", stopName, isMajorStop, totalTransferTime);
        return totalTransferTime;
    }

    // Приватные методы для расчетов

    /**
     * Рассчитать время ожидания на основе real-time данных и статистики
     */
    private Mono<Integer> calculateWaitingTimeFromData(String routeNumber, String stopName, LocalDateTime currentTime) {
        // Сначала пытаемся получить real-time данные о автобусах
        return getVehicleBasedWaitingTime(routeNumber, stopName)
                .switchIfEmpty(
                        // Если нет real-time данных, используем статистические модели
                        getStatisticalWaitingTime(routeNumber, currentTime)
                )
                .switchIfEmpty(
                        // Fallback на базовое время по расписанию
                        getFrequencyBasedWaitingTime(routeNumber, currentTime)
                );
    }

    /**
     * Время ожидания на основе текущих позиций автобусов
     */
    private Mono<Integer> getVehicleBasedWaitingTime(String routeNumber, String stopName) {
        return databaseClient.sql("""
            WITH route_vehicles AS (
                SELECT 
                    v.id, v.current_latitude, v.current_longitude, 
                    v.speed_kmh, v.is_in_motion, v.last_position_update,
                    ST_Distance(
                        ST_Point(v.current_longitude, v.current_latitude)::geography,
                        ST_Point(bs.longitude, bs.latitude)::geography
                    ) as distance_to_stop
                FROM vehicles v
                JOIN bus_routes br ON v.assigned_route_id = br.id
                JOIN route_stops rs ON br.id = rs.route_id
                JOIN bus_stops bs ON rs.stop_id = bs.id
                WHERE br.route_number = :routeNumber
                AND bs.stop_name ILIKE :stopName
                AND v.is_active = true
                AND v.last_position_update > CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                ORDER BY distance_to_stop
                LIMIT 3
            )
            SELECT 
                CASE 
                    WHEN COUNT(*) = 0 THEN NULL
                    WHEN MIN(distance_to_stop) < 500 THEN 2  -- Автобус очень близко
                    WHEN MIN(distance_to_stop) < 1000 THEN 5 -- Автобус близко
                    WHEN AVG(speed_kmh) > 10 THEN 8         -- Автобусы движутся
                    ELSE 12                                  -- Автобусы стоят или медленно едут
                END as estimated_wait_minutes
            FROM route_vehicles
            """)
                .bind("routeNumber", routeNumber)
                .bind("stopName", "%" + stopName + "%")
                .map(row -> row.get("estimated_wait_minutes", Integer.class))
                .one()
                .filter(waitTime -> waitTime != null)
                .doOnNext(waitTime -> log.debug("Vehicle-based wait time for route {}: {} minutes",
                        routeNumber, waitTime));
    }

    /**
     * Статистическое время ожидания на основе исторических данных
     */
    private Mono<Integer> getStatisticalWaitingTime(String routeNumber, LocalDateTime currentTime) {
        return databaseClient.sql("""
            SELECT 
                CASE 
                    WHEN route_number IN ('1', '2', '3', '4', '5') THEN 6    -- Основные маршруты
                    WHEN route_number IN ('7', '12', '29', '30') THEN 8      -- Популярные маршруты  
                    WHEN route_number SIMILAR TO '%[A-Z]' THEN 12            -- Экспресс маршруты
                    ELSE 10                                                   -- Обычные маршруты
                END as base_wait_time,
                CASE 
                    WHEN :hour BETWEEN 7 AND 9 THEN -2    -- Час пик утром - чаще
                    WHEN :hour BETWEEN 17 AND 19 THEN -2  -- Час пик вечером - чаще
                    WHEN :hour BETWEEN 22 AND 6 THEN 5    -- Ночь - реже
                    ELSE 0                                 -- Обычное время
                END as time_adjustment
            FROM bus_routes 
            WHERE route_number = :routeNumber 
            AND is_active = true
            LIMIT 1
            """)
                .bind("routeNumber", routeNumber)
                .bind("hour", currentTime.getHour())
                .map(row -> {
                    Integer baseTime = row.get("base_wait_time", Integer.class);
                    Integer adjustment = row.get("time_adjustment", Integer.class);
                    return (baseTime != null ? baseTime : 10) + (adjustment != null ? adjustment : 0);
                })
                .one()
                .doOnNext(waitTime -> log.debug("Statistical wait time for route {}: {} minutes",
                        routeNumber, waitTime));
    }

    /**
     * Время ожидания на основе теоретической частоты маршрута
     */
    private Mono<Integer> getFrequencyBasedWaitingTime(String routeNumber, LocalDateTime currentTime) {
        return Mono.fromCallable(() -> {
            int hour = currentTime.getHour();
            int dayOfWeek = currentTime.getDayOfWeek().getValue();

            // Базовое время в зависимости от времени дня
            int baseWaitTime;
            if (hour >= 6 && hour <= 9) {
                baseWaitTime = 8; // Утренний час пик
            } else if (hour >= 17 && hour <= 20) {
                baseWaitTime = 10; // Вечерний час пик
            } else if (hour >= 22 || hour <= 5) {
                baseWaitTime = 25; // Ночное время
            } else {
                baseWaitTime = 15; // Обычные часы
            }

            // Корректировка на выходные
            if (dayOfWeek >= 6) { // Суббота и воскресенье
                baseWaitTime += 5;
            }

            return Math.min(baseWaitTime, 30); // Максимум 30 минут ожидания
        });
    }

    /**
     * Рассчитать время поездки из базы данных
     */
    private Mono<Integer> calculateTravelTimeFromDatabase(String routeNumber, String fromStopName, String toStopName) {
        return databaseClient.sql("""
            WITH route_segments AS (
                SELECT 
                    rs1.stop_sequence as from_sequence,
                    rs2.stop_sequence as to_sequence,
                    rs1.direction,
                    ABS(rs2.stop_sequence - rs1.stop_sequence) as stops_count,
                    ABS(rs2.distance_from_start_meters - rs1.distance_from_start_meters) as distance_meters,
                    rs2.estimated_travel_time_minutes - rs1.estimated_travel_time_minutes as scheduled_time
                FROM route_stops rs1
                JOIN route_stops rs2 ON rs1.route_id = rs2.route_id 
                                    AND rs1.direction = rs2.direction
                                    AND rs1.stop_sequence < rs2.stop_sequence
                JOIN bus_routes br ON rs1.route_id = br.id
                JOIN bus_stops bs1 ON rs1.stop_id = bs1.id
                JOIN bus_stops bs2 ON rs2.stop_id = bs2.id
                WHERE br.route_number = :routeNumber
                AND bs1.stop_name ILIKE :fromStopName
                AND bs2.stop_name ILIKE :toStopName
                AND br.is_active = true
                ORDER BY stops_count
                LIMIT 1
            )
            SELECT 
                COALESCE(scheduled_time, stops_count * 2) as estimated_minutes,
                stops_count,
                distance_meters
            FROM route_segments
            """)
                .bind("routeNumber", routeNumber)
                .bind("fromStopName", "%" + fromStopName + "%")
                .bind("toStopName", "%" + toStopName + "%")
                .map(row -> {
                    Integer estimatedMinutes = row.get("estimated_minutes", Integer.class);
                    Integer stopsCount = row.get("stops_count", Integer.class);
                    Integer distanceMeters = row.get("distance_meters", Integer.class);

                    // Если есть точное время из расписания, используем его
                    if (estimatedMinutes != null && estimatedMinutes > 0) {
                        return adjustForTrafficConditions(estimatedMinutes);
                    }

                    // Иначе рассчитываем: 2 минуты на остановку + время на расстояние
                    int calculatedTime = (stopsCount != null ? stopsCount * 2 : 10);

                    // Добавляем время на расстояние (20 км/ч средняя скорость в городе)
                    if (distanceMeters != null && distanceMeters > 0) {
                        int distanceTime = (int) Math.ceil(distanceMeters / 1000.0 * 3); // 20 км/ч = 3 мин/км
                        calculatedTime = Math.max(calculatedTime, distanceTime);
                    }

                    return adjustForTrafficConditions(calculatedTime);
                })
                .one()
                .switchIfEmpty(Mono.fromCallable(() -> {
                    // Fallback: если маршрут не найден, возвращаем базовое время
                    log.warn("No route data found for {} from {} to {}, using fallback",
                            routeNumber, fromStopName, toStopName);
                    return 15; // 15 минут по умолчанию
                }));
    }

    /**
     * Корректировка времени ходьбы с учетом городских условий
     */
    private int applyWalkingConditionsCorrection(int baseWalkingTime, double distanceMeters) {
        // Для очень коротких расстояний добавляем время на переходы и светофоры
        if (distanceMeters < 200) {
            return baseWalkingTime + 1; // +1 минута для коротких переходов
        }

        // Для средних расстояний добавляем время на пересечение дорог
        if (distanceMeters < 500) {
            return baseWalkingTime + 2; // +2 минуты для пересечений
        }

        // Для длинных расстояний добавляем время на сложную навигацию
        if (distanceMeters > 800) {
            return baseWalkingTime + 3; // +3 минуты для сложной навигации
        }

        return baseWalkingTime + 1; // Базовая корректировка для городских условий
    }

    /**
     * Корректировка времени поездки с учетом пробок.
     * Now uses centralized traffic multipliers from GeoConstants.
     */
    private int adjustForTrafficConditions(int baseTime) {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int dayOfWeek = now.getDayOfWeek().getValue();

        // Используем централизованные коэффициенты пробок из GeoConstants
        double trafficMultiplier = GeoConstants.TRAFFIC_MULTIPLIER_NO_TRAFFIC;

        // Час пик утром (7-9)
        if (hour >= 7 && hour <= 9) {
            trafficMultiplier = GeoConstants.TRAFFIC_MULTIPLIER_MODERATE;
        }
        // Час пик вечером (17-19)
        else if (hour >= 17 && hour <= 19) {
            trafficMultiplier = GeoConstants.TRAFFIC_MULTIPLIER_HEAVY; // Вечерние пробки обычно хуже
        }
        // Дневное время (10-16)
        else if (hour >= 10 && hour <= 16) {
            trafficMultiplier = GeoConstants.TRAFFIC_MULTIPLIER_LIGHT;
        }
        // Вечернее время (20-22)
        else if (hour >= 20 && hour <= 22) {
            trafficMultiplier = GeoConstants.TRAFFIC_MULTIPLIER_LIGHT;
        }

        // Выходные дни - меньше пробок
        if (dayOfWeek >= 6) {
            trafficMultiplier *= 0.8;
        }

        return (int) Math.ceil(baseTime * trafficMultiplier);
    }

    /**
     * Базовое время ожидания в зависимости от времени дня
     */
    private int calculateBaseWaitingTime(LocalDateTime departureTime) {
        int hour = departureTime.getHour();

        if (hour >= 6 && hour <= 9) {
            return 5; // Утренний час пик - частые автобусы
        } else if (hour >= 17 && hour <= 20) {
            return 7; // Вечерний час пик
        } else if (hour >= 22 || hour <= 5) {
            return 15; // Ночное время - редкие автобусы
        } else {
            return 10; // Обычное время
        }
    }
}