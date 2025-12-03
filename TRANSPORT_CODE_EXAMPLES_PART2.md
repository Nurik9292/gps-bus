# Transport Refactoring: Code Examples Part 2 (Before → After)

**Продолжение документа** [TRANSPORT_CODE_EXAMPLES_BEFORE_AFTER.md](./TRANSPORT_CODE_EXAMPLES_BEFORE_AFTER.md)

---

<a name="problem-4"></a>
## 🔴 Проблема #4: Cross-BC Dependency + SRP Violation

### ❌ ТЕКУЩИЙ КОД (Проблемный)

**Файл:** `transport/infrastructure/services/BusStopRealTimeServiceImpl.java` (356 строк)

```java
package biz.ugur.busroutebackend.transport.infrastructure.services;

// ❌❌❌ CRITICAL: Импорт из admin bounded context!
import biz.ugur.busroutebackend.admin.domain.exceptions.BusStopException;

import biz.ugur.busroutebackend.interfaces.rest.transport.V1.response.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.application.dto.BusArrivalInfo;
import biz.ugur.busroutebackend.transport.application.dto.NearbyStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.application.services.BusStopRealTimeService;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Slf4j
public class BusStopRealTimeServiceImpl implements BusStopRealTimeService {

    private final BusStopRepository busStopRepository;
    private final DatabaseClient databaseClient;  // ❌ Прямой доступ к БД
    private final ReactiveRedisTemplate<String, Object> redisTemplate;  // ❌ Прямое кэширование
    private final ObjectMapper objectMapper;

    public BusStopRealTimeServiceImpl(
            BusStopRepository busStopRepository,
            DatabaseClient databaseClient,
            ReactiveRedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        this.busStopRepository = busStopRepository;
        this.databaseClient = databaseClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ❌ SRP VIOLATION: Этот метод делает ВСЕ:
    // 1. Получение данных из БД
    // 2. Кэширование в Redis
    // 3. Логирование производительности
    // 4. Расчет ETA
    public Mono<BusStopArrivalsResponse> getStopArrivals(String stopId) {
        String cacheKey = "stop_arrivals:" + stopId;
        long startTime = System.currentTimeMillis();

        // ❌ Прямая работа с Redis
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(BusStopArrivalsResponse.class)
                .doOnNext(cached -> log.debug("Cache HIT for stop {}", stopId))
                .switchIfEmpty(
                        calculateStopArrivals(stopId)
                                .flatMap(response -> {
                                    long calculationTime = System.currentTimeMillis() - startTime;

                                    // ❌ Логирование производительности в БД
                                    logETAPerformance(stopId, response.getArrivals().size(),
                                            0, calculationTime, false);

                                    // ❌ Прямое кэширование
                                    return redisTemplate.opsForValue()
                                            .set(cacheKey, response, Duration.ofSeconds(15))
                                            .thenReturn(response);
                                })
                                .doOnNext(calculated -> log.debug("Cache MISS for stop {}", stopId))
                );
    }

    // ❌ Логирование производительности в БД - это не ответственность этого сервиса!
    private void logETAPerformance(String stopId, int routesCount, int vehiclesProcessed,
                                   long calculationTimeMs, boolean cacheHit) {
        if (calculationTimeMs > 100) {
            String performanceSql = """
            SELECT log_eta_performance(:stopId, :routesCount, :vehiclesProcessed,
                                       :calculationTime, :cacheHit)
            """;

            databaseClient.sql(performanceSql)
                    .bind("stopId", stopId)
                    .bind("routesCount", routesCount)
                    .bind("vehiclesProcessed", vehiclesProcessed)
                    .bind("calculationTime", (int) calculationTimeMs)
                    .bind("cacheHit", cacheHit)
                    .then()
                    .subscribe(  // ❌ Fire-and-forget!
                            result -> log.debug("Logged ETA performance"),
                            error -> log.warn("Failed to log ETA performance: {}", error.getMessage())
                    );
        }
    }

    private Mono<BusStopArrivalsResponse> calculateStopArrivals(String stopId) {
        // ❌ Использование admin exception!
        return busStopRepository.findById(BusStopId.of(stopId))
                .switchIfEmpty(Mono.error(new BusStopException(
                    "BUS_STOP_EXCEPTION",
                    "Stop not found: " + stopId
                ) {}))
                .flatMap(busStop -> {
                    return findArrivingVehicles(busStop)
                            .collectList()
                            .map(arrivals -> new BusStopArrivalsResponse(
                                    busStop.getId().getValue(),
                                    busStop.getStopName(),
                                    busStop.getLatitude().doubleValue(),
                                    busStop.getLongitude().doubleValue(),
                                    arrivals,
                                    LocalDateTime.now()
                            ));
                });
    }

    // ❌ 150+ строк SQL с бизнес-логикой (rush hours, скорости)
    private Flux<BusArrivalInfo> findArrivingVehicles(BusStop targetStop) {
        String sql = """
        WITH
        target_stop_routes AS (
            SELECT DISTINCT
                rs.route_id,
                rs.direction,
                rs.stop_sequence as target_sequence,
                rs.distance_from_start_meters as target_distance,
                br.route_number,
                br.route_name,
                br.route_color
            FROM route_stops rs
            JOIN bus_routes br ON rs.route_id = br.id
            WHERE rs.stop_id = :stopId
            AND br.is_active = true
        ),

        route_vehicles AS (
            SELECT
                v.id as vehicle_id,
                v.license_plate,
                v.current_latitude,
                v.current_longitude,
                v.speed_kmh,
                v.is_in_motion,
                v.last_position_update,
                v.course,
                tsr.route_id,
                tsr.direction,
                tsr.target_sequence,
                tsr.target_distance,
                tsr.route_number,
                tsr.route_name,
                tsr.route_color,
                ST_Distance(
                    ST_Point(v.current_longitude, v.current_latitude)::geography,
                    ST_Point(:stopLon, :stopLat)::geography
                ) as distance_to_stop
            FROM vehicles v
            JOIN target_stop_routes tsr ON v.assigned_route_id = tsr.route_id
            WHERE v.is_active = true
            AND v.last_position_update > CURRENT_TIMESTAMP - INTERVAL '24 hours'
        ),

        vehicles_with_eta AS (
            SELECT
                vcs.*,
                CASE
                    WHEN vcs.current_sequence < vcs.target_sequence THEN
                        CASE
                            WHEN vcs.speed_kmh > 5 THEN
                                GREATEST(1, ROUND((vcs.target_distance - vcs.current_distance) / (vcs.speed_kmh * 1000.0 / 60.0))::integer)

                            -- ❌❌❌ HARDCODED RUSH HOURS!
                            WHEN EXTRACT(hour FROM CURRENT_TIMESTAMP) BETWEEN 7 AND 9 THEN
                                GREATEST(2, ROUND((vcs.target_distance - vcs.current_distance) / (12.0 * 1000.0 / 60.0))::integer)

                            WHEN EXTRACT(hour FROM CURRENT_TIMESTAMP) BETWEEN 17 AND 19 THEN
                                GREATEST(2, ROUND((vcs.target_distance - vcs.current_distance) / (12.0 * 1000.0 / 60.0))::integer)

                            WHEN EXTRACT(hour FROM CURRENT_TIMESTAMP) BETWEEN 12 AND 14 THEN
                                GREATEST(1, ROUND((vcs.target_distance - vcs.current_distance) / (18.0 * 1000.0 / 60.0))::integer)

                            ELSE
                                GREATEST(1, ROUND((vcs.target_distance - vcs.current_distance) / (25.0 * 1000.0 / 60.0))::integer)
                        END

                    WHEN vcs.current_sequence = vcs.target_sequence AND vcs.distance_to_current_stop < 200 THEN 1

                    ELSE NULL
                END as calculated_eta,

                CASE
                    WHEN vcs.current_sequence < vcs.target_sequence THEN 'approaching'
                    WHEN vcs.current_sequence = vcs.target_sequence AND vcs.distance_to_current_stop < 200 THEN 'at_stop'
                    ELSE 'passed'
                END as calculated_status
            FROM vehicle_current_stops vcs
        )

        SELECT DISTINCT ON (route_number)
            vwe.vehicle_id,
            vwe.license_plate,
            vwe.route_id,
            vwe.route_number,
            vwe.route_name,
            vwe.route_color,
            vwe.calculated_eta as estimated_arrival_minutes,
            vwe.calculated_status as arrival_status,
            vwe.current_latitude,
            vwe.current_longitude,
            vwe.speed_kmh,
            vwe.is_in_motion,
            vwe.current_stop_name,
            vwe.course
        FROM vehicles_with_eta vwe
        WHERE vwe.calculated_eta IS NOT NULL
        AND vwe.calculated_eta > 0
        AND vwe.calculated_eta < 120
        ORDER BY vwe.route_number, vwe.calculated_eta
        """;

        return databaseClient.sql(sql)
                .bind("stopId", targetStop.getId().getValue())
                .bind("stopLat", targetStop.getLatitude().doubleValue())
                .bind("stopLon", targetStop.getLongitude().doubleValue())
                .map((row, metadata) -> {
                    // Маппинг результатов...
                    return new BusArrivalInfo(...);
                })
                .all();
    }
}
```

**Проблемы в одном классе (356 строк):**
1. 🔴 **CRITICAL:** Cross-BC dependency (`admin.domain.exceptions.BusStopException`)
2. ❌ **SRP Violation:** 5+ ответственностей в одном классе
3. ❌ **Business Logic in SQL:** Rush hours, speeds hardcoded
4. ❌ **Infrastructure Concerns Mixed:** Redis + DB + Logging
5. ❌ **No Logging:** Нет логирования ошибок
6. ❌ **Fire-and-Forget:** `.subscribe()` без обработки ошибок
7. ❌ **Hard to Test:** Complex SQL, static times, no mocks

### ✅ РЕФАКТОРИНГ (Решение)

**Шаг 1: Создать transport exception**

**Файл:** `transport/domain/exceptions/BusStopNotFoundException.java` (НОВЫЙ)

```java
package biz.ugur.busroutebackend.transport.domain.exceptions;

/**
 * ✅ Transport BC exception - НЕ зависит от других BC
 */
public class BusStopNotFoundException extends TransportDomainException {

    private final String stopId;

    public BusStopNotFoundException(String stopId) {
        super(
            "BUS_STOP_NOT_FOUND",
            String.format("Bus stop not found: %s", stopId)
        );
        this.stopId = stopId;
    }

    public String getStopId() {
        return stopId;
    }
}
```

**Шаг 2: Domain Service для ETA calculation**

**Файл:** `transport/domain/services/ETACalculationService.java` (НОВЫЙ)

```java
package biz.ugur.busroutebackend.transport.domain.services;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Distance;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;

/**
 * ✅ Domain Service для расчета ETA (Estimated Time of Arrival).
 *
 * Бизнес-логика:
 * - Расчет времени прибытия на основе скорости и расстояния
 * - Учет rush hours
 * - Учет текущей скорости транспорта
 */
@Component
public class ETACalculationService {

    private final DistanceCalculationService distanceService;
    private final ETAConfiguration config;

    public ETACalculationService(
            DistanceCalculationService distanceService,
            ETAConfiguration config) {
        this.distanceService = distanceService;
        this.config = config;
    }

    /**
     * ✅ Рассчитать ETA на основе текущей позиции и целевой позиции
     */
    public Duration calculateETA(
            Coordinates currentPosition,
            Coordinates targetPosition,
            LocalTime currentTime,
            double currentSpeedKmh) {

        // Рассчитываем расстояние
        Distance distance = distanceService.calculateDistance(
            currentPosition.getLatitudeAsDouble(),
            currentPosition.getLongitudeAsDouble(),
            targetPosition.getLatitudeAsDouble(),
            targetPosition.getLongitudeAsDouble()
        );

        // Определяем скорость для расчета
        double effectiveSpeedKmh = determineEffectiveSpeed(currentSpeedKmh, currentTime);

        // Рассчитываем время в минутах
        double durationMinutes = (distance.getKilometers() / effectiveSpeedKmh) * 60.0;

        // Минимум 1 минута
        long minutes = Math.max(1, Math.round(durationMinutes));

        return Duration.ofMinutes(minutes);
    }

    /**
     * ✅ Рассчитать ETA на основе расстояния по маршруту
     */
    public Duration calculateETAByDistance(
            double distanceMeters,
            LocalTime currentTime,
            double currentSpeedKmh) {

        double effectiveSpeedKmh = determineEffectiveSpeed(currentSpeedKmh, currentTime);

        // Время = Расстояние / Скорость
        double durationMinutes = (distanceMeters / 1000.0) / effectiveSpeedKmh * 60.0;

        long minutes = Math.max(1, Math.round(durationMinutes));

        return Duration.ofMinutes(minutes);
    }

    /**
     * ✅ Определить эффективную скорость с учетом текущей скорости и времени суток
     */
    private double determineEffectiveSpeed(double currentSpeedKmh, LocalTime currentTime) {
        // Если автобус движется - используем текущую скорость
        if (currentSpeedKmh > config.getMinMovingSpeedKmh()) {
            return currentSpeedKmh;
        }

        // Если автобус стоит - используем среднюю скорость для времени суток
        return getAverageSpeedForTime(currentTime);
    }

    /**
     * ✅ Получить среднюю скорость для времени суток (rush hours учтены)
     */
    private double getAverageSpeedForTime(LocalTime time) {
        int hour = time.getHour();

        // Утренний rush hour
        if (config.isMorningRushHour(hour)) {
            return config.getRushHourSpeedKmh();
        }

        // Обеденное время
        if (config.isLunchTime(hour)) {
            return config.getLunchTimeSpeedKmh();
        }

        // Вечерний rush hour
        if (config.isEveningRushHour(hour)) {
            return config.getRushHourSpeedKmh();
        }

        // Обычное время
        return config.getNormalSpeedKmh();
    }

    /**
     * ✅ Проверить, находится ли транспорт на остановке
     */
    public boolean isAtStop(Coordinates vehiclePosition, Coordinates stopPosition) {
        Distance distance = distanceService.calculateDistance(
            vehiclePosition.getLatitudeAsDouble(),
            vehiclePosition.getLongitudeAsDouble(),
            stopPosition.getLatitudeAsDouble(),
            stopPosition.getLongitudeAsDouble()
        );

        return distance.getMeters() <= config.getAtStopToleranceMeters();
    }
}
```

**Шаг 3: Configuration для ETA**

**Файл:** `transport/domain/services/ETAConfiguration.java` (НОВЫЙ)

```java
package biz.ugur.busroutebackend.transport.domain.services;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * ✅ Конфигурация для ETA расчетов - из application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "business.transport.eta")
@Getter
@Setter
public class ETAConfiguration {

    // Rush hours
    private List<Integer> morningRushHours = List.of(7, 8, 9);
    private List<Integer> lunchHours = List.of(12, 13, 14);
    private List<Integer> eveningRushHours = List.of(17, 18, 19);

    // Speeds (km/h)
    private double rushHourSpeedKmh = 12.0;
    private double lunchTimeSpeedKmh = 18.0;
    private double normalSpeedKmh = 25.0;
    private double minMovingSpeedKmh = 5.0;

    // Tolerances
    private double atStopToleranceMeters = 200.0;
    private long maxETAMinutes = 120;

    public boolean isMorningRushHour(int hour) {
        return morningRushHours.contains(hour);
    }

    public boolean isLunchTime(int hour) {
        return lunchHours.contains(hour);
    }

    public boolean isEveningRushHour(int hour) {
        return eveningRushHours.contains(hour);
    }
}
```

**application.yml:**
```yaml
business:
  transport:
    eta:
      morning-rush-hours: [7, 8, 9]
      lunch-hours: [12, 13, 14]
      evening-rush-hours: [17, 18, 19]
      rush-hour-speed-kmh: 12.0
      lunch-time-speed-kmh: 18.0
      normal-speed-kmh: 25.0
      min-moving-speed-kmh: 5.0
      at-stop-tolerance-meters: 200.0
      max-eta-minutes: 120
```

**Шаг 4: Query Service для arriving vehicles**

**Файл:** `transport/infrastructure/query/BusStopArrivalQuery.java` (НОВЫЙ)

```java
package biz.ugur.busroutebackend.transport.infrastructure.query;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * ✅ Infrastructure Query - только SQL, без бизнес-логики
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusStopArrivalQuery {

    private final DatabaseClient databaseClient;

    public Flux<VehicleArrivalData> findArrivingVehicles(
            BusStopId stopId,
            Coordinates stopPosition,
            Duration timeWindow) {

        // ✅ SQL БЕЗ бизнес-логики (rush hours, ETA calculation)
        String sql = """
        WITH target_stop_routes AS (
            SELECT DISTINCT
                rs.route_id,
                rs.direction,
                rs.stop_sequence as target_sequence,
                rs.distance_from_start_meters as target_distance,
                br.route_number,
                br.route_name,
                br.route_color
            FROM route_stops rs
            JOIN bus_routes br ON rs.route_id = br.id
            WHERE rs.stop_id = :stopId
            AND br.is_active = true
        ),

        route_vehicles AS (
            SELECT
                v.id as vehicle_id,
                v.license_plate,
                v.current_latitude,
                v.current_longitude,
                v.speed_kmh,
                v.is_in_motion,
                v.course,
                tsr.route_id,
                tsr.direction,
                tsr.target_sequence,
                tsr.target_distance,
                tsr.route_number,
                tsr.route_name,
                tsr.route_color,
                ST_Distance(
                    ST_Point(v.current_longitude, v.current_latitude)::geography,
                    ST_Point(:stopLon, :stopLat)::geography
                ) as distance_to_stop_meters
            FROM vehicles v
            JOIN target_stop_routes tsr ON v.assigned_route_id = tsr.route_id
            WHERE v.is_active = true
            AND v.last_position_update > CURRENT_TIMESTAMP - INTERVAL '24 hours'
            AND v.current_latitude IS NOT NULL
            AND v.current_longitude IS NOT NULL
        ),

        vehicle_current_stops AS (
            SELECT
                rv.*,
                rs_nearest.stop_sequence as current_sequence,
                rs_nearest.distance_from_start_meters as current_distance
            FROM route_vehicles rv
            JOIN LATERAL (
                SELECT rs.*
                FROM route_stops rs
                JOIN bus_stops bs ON rs.stop_id = bs.id
                WHERE rs.route_id = rv.route_id
                AND rs.direction = rv.direction
                ORDER BY ST_Distance(
                    ST_Point(rv.current_longitude, rv.current_latitude)::geography,
                    ST_Point(bs.longitude, bs.latitude)::geography
                )
                LIMIT 1
            ) rs_nearest ON true
            WHERE rv.current_sequence <= rv.target_sequence
        )

        SELECT DISTINCT ON (route_number)
            vcs.vehicle_id,
            vcs.license_plate,
            vcs.route_id,
            vcs.route_number,
            vcs.route_name,
            vcs.route_color,
            vcs.current_latitude,
            vcs.current_longitude,
            vcs.speed_kmh,
            vcs.is_in_motion,
            vcs.course,
            vcs.current_sequence,
            vcs.target_sequence,
            vcs.current_distance,
            vcs.target_distance,
            vcs.distance_to_stop_meters
        FROM vehicle_current_stops vcs
        ORDER BY vcs.route_number, vcs.current_distance DESC
        """;

        log.debug("Querying arriving vehicles for stop: {}", stopId.getValue());

        return databaseClient.sql(sql)
                .bind("stopId", stopId.getValue())
                .bind("stopLat", stopPosition.getLatitudeAsDouble())
                .bind("stopLon", stopPosition.getLongitudeAsDouble())
                .map((row, metadata) -> mapRow(row, metadata))
                .all()
                .doOnComplete(() ->
                    log.debug("Completed query for stop: {}", stopId.getValue()));
    }

    private VehicleArrivalData mapRow(Row row, RowMetadata metadata) {
        return VehicleArrivalData.builder()
                .vehicleId(row.get("vehicle_id", UUID.class))
                .licensePlate(row.get("license_plate", String.class))
                .routeId(row.get("route_id", UUID.class))
                .routeNumber(row.get("route_number", String.class))
                .routeName(row.get("route_name", String.class))
                .routeColor(row.get("route_color", String.class))
                .currentLatitude(row.get("current_latitude", BigDecimal.class))
                .currentLongitude(row.get("current_longitude", BigDecimal.class))
                .speedKmh(row.get("speed_kmh", Double.class))
                .isInMotion(row.get("is_in_motion", Boolean.class))
                .course(row.get("course", Double.class))
                .currentSequence(row.get("current_sequence", Integer.class))
                .targetSequence(row.get("target_sequence", Integer.class))
                .currentDistanceMeters(row.get("current_distance", Integer.class))
                .targetDistanceMeters(row.get("target_distance", Integer.class))
                .distanceToStopMeters(row.get("distance_to_stop_meters", Double.class))
                .build();
    }
}
```

**Файл:** `transport/infrastructure/query/VehicleArrivalData.java` (НОВЫЙ)

```java
@Value
@Builder
public class VehicleArrivalData {
    UUID vehicleId;
    String licensePlate;
    UUID routeId;
    String routeNumber;
    String routeName;
    String routeColor;
    BigDecimal currentLatitude;
    BigDecimal currentLongitude;
    Double speedKmh;
    Boolean isInMotion;
    Double course;
    Integer currentSequence;
    Integer targetSequence;
    Integer currentDistanceMeters;
    Integer targetDistanceMeters;
    Double distanceToStopMeters;

    public Coordinates getCurrentPosition() {
        return Coordinates.of(
            currentLatitude.doubleValue(),
            currentLongitude.doubleValue()
        );
    }

    public double getRemainingDistanceMeters() {
        return targetDistanceMeters - currentDistanceMeters;
    }
}
```

**Шаг 5: Cache Service**

**Файл:** `transport/infrastructure/cache/ArrivalCacheService.java` (НОВЫЙ)

```java
package biz.ugur.busroutebackend.transport.infrastructure.cache;

import biz.ugur.busroutebackend.interfaces.rest.transport.V1.response.BusStopArrivalsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * ✅ Отдельный сервис для кэширования - Single Responsibility
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArrivalCacheService {

    private static final String CACHE_KEY_PREFIX = "stop_arrivals:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public Mono<BusStopArrivalsResponse> get(String stopId) {
        String cacheKey = CACHE_KEY_PREFIX + stopId;

        log.debug("Checking cache for stop: {}", stopId);

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(BusStopArrivalsResponse.class)
                .doOnNext(cached ->
                    log.debug("Cache HIT for stop: {}", stopId))
                .doOnError(error ->
                    log.warn("Failed to get from cache for stop: {}", stopId, error));
    }

    public Mono<Void> cache(String stopId, BusStopArrivalsResponse response) {
        String cacheKey = CACHE_KEY_PREFIX + stopId;

        log.debug("Caching arrivals for stop: {}, {} routes", stopId, response.getArrivals().size());

        return redisTemplate.opsForValue()
                .set(cacheKey, response, CACHE_TTL)
                .then()
                .doOnSuccess(v ->
                    log.debug("Cached arrivals for stop: {}", stopId))
                .doOnError(error ->
                    log.error("Failed to cache arrivals for stop: {}", stopId, error));
    }

    public Mono<Void> invalidate(String stopId) {
        String cacheKey = CACHE_KEY_PREFIX + stopId;

        log.debug("Invalidating cache for stop: {}", stopId);

        return redisTemplate.delete(cacheKey)
                .then()
                .doOnSuccess(v ->
                    log.debug("Invalidated cache for stop: {}", stopId));
    }
}
```

**Шаг 6: Refactored Application Service**

**Файл:** `transport/infrastructure/services/BusStopRealTimeServiceImpl.java` (ПОСЛЕ рефакторинга)

```java
package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.interfaces.rest.transport.V1.response.BusStopArrivalsResponse;
import biz.ugur.busroutebackend.transport.application.dto.BusArrivalInfo;
import biz.ugur.busroutebackend.transport.application.services.BusStopRealTimeService;
import biz.ugur.busroutebackend.transport.domain.exceptions.BusStopNotFoundException;  // ✅ Transport exception!
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.services.ETACalculationService;  // ✅ Domain service
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.infrastructure.cache.ArrivalCacheService;  // ✅ Cache service
import biz.ugur.busroutebackend.transport.infrastructure.query.BusStopArrivalQuery;  // ✅ Query service
import biz.ugur.busroutebackend.transport.infrastructure.query.VehicleArrivalData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * ✅ Рефакторнутый сервис - только координация (Orchestration)
 *
 * Ответственности:
 * - Координация запроса (cache → query → calculate → cache)
 * - Обработка ошибок
 * - Логирование
 *
 * Делегирует:
 * - Кэширование → ArrivalCacheService
 * - SQL запросы → BusStopArrivalQuery
 * - ETA расчет → ETACalculationService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusStopRealTimeServiceImpl implements BusStopRealTimeService {

    private final BusStopRepository busStopRepository;
    private final ArrivalCacheService cacheService;  // ✅ Dependency injection
    private final BusStopArrivalQuery arrivalQuery;  // ✅ Dependency injection
    private final ETACalculationService etaService;  // ✅ Dependency injection

    @Override
    public Mono<BusStopArrivalsResponse> getStopArrivals(String stopId) {
        log.debug("Fetching arrivals for stop: {}", stopId);

        return cacheService.get(stopId)
                .switchIfEmpty(
                    calculateArrivals(stopId)
                        .flatMap(response ->
                            cacheService.cache(stopId, response)
                                .thenReturn(response)
                        )
                )
                .doOnSuccess(response ->
                    log.debug("Fetched arrivals for stop: {}, {} routes",
                        stopId, response.getArrivals().size()))
                .doOnError(error ->
                    log.error("Failed to fetch arrivals for stop: {}", stopId, error));
    }

    private Mono<BusStopArrivalsResponse> calculateArrivals(String stopId) {
        return busStopRepository.findById(BusStopId.of(stopId))
                // ✅ Transport exception!
                .switchIfEmpty(Mono.error(new BusStopNotFoundException(stopId)))
                .flatMap(busStop -> {
                    Coordinates stopPosition = busStop.toCoordinates();

                    return arrivalQuery.findArrivingVehicles(
                                busStop.getId(),
                                stopPosition,
                                Duration.ofHours(2)
                            )
                            .flatMap(vehicleData ->
                                enrichWithETA(vehicleData, stopPosition)
                            )
                            .collectList()
                            .map(arrivals -> new BusStopArrivalsResponse(
                                    busStop.getId().getValue(),
                                    busStop.getStopName(),
                                    busStop.getLatitude().doubleValue(),
                                    busStop.getLongitude().doubleValue(),
                                    arrivals,
                                    LocalDateTime.now()
                            ));
                });
    }

    private Mono<BusArrivalInfo> enrichWithETA(
            VehicleArrivalData vehicleData,
            Coordinates stopPosition) {

        // ✅ Используем domain service для расчета ETA
        Duration eta = etaService.calculateETAByDistance(
            vehicleData.getRemainingDistanceMeters(),
            LocalTime.now(),
            vehicleData.getSpeedKmh()
        );

        // Определяем статус
        String status;
        if (etaService.isAtStop(vehicleData.getCurrentPosition(), stopPosition)) {
            status = "at_stop";
        } else if (vehicleData.getCurrentSequence() < vehicleData.getTargetSequence()) {
            status = "approaching";
        } else {
            status = "passed";
        }

        return Mono.just(new BusArrivalInfo(
                vehicleData.getVehicleId().toString(),
                vehicleData.getLicensePlate(),
                vehicleData.getRouteId().toString(),
                vehicleData.getRouteNumber(),
                vehicleData.getRouteName(),
                vehicleData.getRouteColor(),
                (int) eta.toMinutes(),
                status,
                vehicleData.getCurrentLatitude().doubleValue(),
                vehicleData.getCurrentLongitude().doubleValue(),
                vehicleData.getSpeedKmh(),
                vehicleData.getIsInMotion(),
                vehicleData.getCourse()
        ));
    }
}
```

### 📊 Сравнение: До vs После

| Аспект | До (❌) | После (✅) |
|--------|---------|------------|
| **Lines of Code** | 356 строк | ~100 строк |
| **Cross-BC Dependencies** | 1 (admin exception) | 0 |
| **Responsibilities** | 5+ (SQL, cache, ETA, logging, etc.) | 1 (orchestration) |
| **Business Logic** | В SQL (hardcoded) | В Domain Service (configurable) |
| **Testability** | Сложно (все вместе) | Легко (каждый компонент отдельно) |
| **Configuration** | Hardcoded | application.yml |
| **Error Handling** | Fire-and-forget | Proper reactive chains |
| **Logging** | Минимальное | Comprehensive |
| **Separation of Concerns** | Нет | Да (Query/Cache/Domain services) |

### 💡 Преимущества Рефакторинга

1. **Single Responsibility:**
   ```java
   // ❌ БЫЛО: Один класс делает все
   BusStopRealTimeServiceImpl (356 строк)
   - SQL queries
   - ETA calculation
   - Caching
   - Performance logging

   // ✅ СТАЛО: Каждый класс - одна ответственность
   BusStopRealTimeServiceImpl (100 строк) - orchestration
   BusStopArrivalQuery - SQL queries
   ETACalculationService - ETA calculation
   ArrivalCacheService - caching
   ```

2. **Testability:**
   ```java
   // ✅ Легко тестировать ETA расчет отдельно
   @Test
   void shouldCalculateETADuringRushHour() {
       ETAConfiguration config = new ETAConfiguration();
       config.setMorningRushHours(List.of(7, 8, 9));
       config.setRushHourSpeedKmh(12.0);

       ETACalculationService service = new ETACalculationService(
           mockDistanceService,
           config
       );

       Duration eta = service.calculateETAByDistance(
           5000,  // 5km
           LocalTime.of(8, 0),  // Rush hour
           0  // Vehicle stopped
       );

       // 5km / 12 km/h = 25 minutes
       assertThat(eta.toMinutes()).isEqualTo(25);
   }
   ```

3. **Configuration:**
   ```yaml
   # ✅ Легко изменить business rules без изменения кода
   business:
     transport:
       eta:
         morning-rush-hours: [7, 8, 9]
         rush-hour-speed-kmh: 12.0
   ```

4. **No Cross-BC Dependencies:**
   ```java
   // ❌ БЫЛО
   import biz.ugur.busroutebackend.admin.domain.exceptions.BusStopException;

   // ✅ СТАЛО
   import biz.ugur.busroutebackend.transport.domain.exceptions.BusStopNotFoundException;
   ```

---

**Продолжение с Problem #5 и #6?**
