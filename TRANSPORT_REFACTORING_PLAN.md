# Transport Bounded Context - Detailed Refactoring Plan

**Date:** 2025-10-30
**Reference Implementation:** Banner Bounded Context
**Target:** Transport Bounded Context
**Principles:** DDD + Clean Architecture + SOLID

---

## 📋 Executive Summary

The transport bounded context requires comprehensive refactoring to align with DDD/Clean Architecture/SOLID principles established in the banner bounded context. This document provides a detailed analysis and step-by-step refactoring plan.

### Current State Assessment

| Metric | Banner (Reference) | Transport (Current) | Gap |
|--------|-------------------|---------------------|-----|
| **Architecture Violations** | 0 | 10+ | 🔴 Critical |
| **Cross-BC Dependencies** | 0 | 2 (admin, geospatial) | 🔴 Critical |
| **Immutable Aggregates** | ✅ Yes | ❌ No | 🟡 High |
| **Event Versioning** | ✅ Yes | ❌ No | 🟡 High |
| **Domain Services** | ✅ 2 services | ❌ None | 🟡 Medium |
| **DTO Duplication** | 0% | ~30% | 🟡 Medium |
| **SRP Violations** | 0 | 5+ classes | 🟡 Medium |
| **Hardcoded Business Rules** | 0% | ~15% | 🟢 Low |
| **Test Coverage** | ~85% | Unknown | 🟢 Low |

---

## 🎯 Refactoring Goals

1. **Eliminate Cross-Boundary Violations** - Remove dependencies on admin BC
2. **Implement Immutability** - Make all aggregates immutable like Banner
3. **Add Event Versioning** - Support event evolution
4. **Extract Domain Services** - Move business logic from infrastructure
5. **Consolidate DTOs/VOs** - Remove duplication
6. **Improve Validation** - Consistent validation strategy
7. **Fix Aggregate Boundaries** - Remove transient relationships
8. **Add Unit Tests** - Cover domain logic with tests

---

## 📊 Comparative Analysis: Banner vs Transport

### 1. Aggregate Root Pattern

#### Banner (Reference) ✅
```java
@Getter
@Builder(toBuilder = true)
public final class Banner extends AggregateRoot<Banner, BannerId> {
    private final BannerId id;
    private final BannerTitle title;
    private final BannerType type;
    private final BannerPeriod period;
    // ... all fields are final

    // Factory method
    public static Banner create(BannerTitle title, ...) {
        Banner banner = Banner.builder()
            .id(BannerId.generate())
            .title(title)
            // ...
            .build();
        banner.registerEvent(new BannerCreatedEvent(...));
        return banner;
    }

    // Immutable update - returns new instance
    public Banner updateBanner(BannerTitle title, ...) {
        validateNotNull(title, "Title cannot be null");
        Banner updated = this.toBuilder()
            .title(title)
            .type(type)
            // ...
            .build();
        updated.registerEvent(new BannerUpdatedEvent(...));
        return updated;
    }
}
```

#### Transport (Current) ❌
```java
@Data  // ❌ Mutable - generates setters
@Builder
public class BusRoute extends AggregateRoot<BusRoute, BusRouteId> {
    private BusRouteId id;
    private String routeNumber;
    private String routeName;
    // ... fields are mutable

    @Transient
    private List<BusStop> busStops;  // ❌ Violates aggregate boundary!

    // Mutable update - modifies state
    public void updateRouteGeometry(String forward, String backward) {
        this.routeGeometryForward = forward;  // ❌ Direct mutation
        this.routeGeometryBackward = backward;
        registerEvent(new RouteGeometryUpdatedEvent(...));
    }

    // Multiple constructors (4)
    public BusRoute() {}
    public BusRoute(BusRouteId id, String number, ...) {}
    // ... confusion about which to use
}
```

**Problems Identified:**
1. ❌ Mutable fields (no `final`)
2. ❌ Multiple constructors instead of factory methods
3. ❌ Transient `busStops` violates aggregate boundary
4. ❌ Direct field mutation instead of returning new instance
5. ❌ Missing validation in update methods

---

### 2. Value Objects Pattern

#### Banner (Reference) ✅
```java
@Value  // Lombok - immutable
public class BannerTitle {
    String value;

    public static BannerTitle of(String value) {
        validate(value);
        return new BannerTitle(value);
    }

    private static void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "Banner title cannot be null or empty"
            );
        }
        if (value.length() > 100) {
            throw new IllegalArgumentException(
                "Banner title must not exceed 100 characters"
            );
        }
    }
}
```

#### Transport (Current) ⚠️
```java
@Value
public class RouteGeometry {
    String wkt;

    // ❌ ServiceLocator anti-pattern!
    private static DistanceCalculationService distanceService;

    public static void setDistanceCalculationService(
        DistanceCalculationService service
    ) {
        RouteGeometry.distanceService = service;
    }

    public double calculateDistance() {
        // Uses static service - tight coupling!
        return distanceService.calculate(...);
    }
}
```

**Problems Identified:**
1. ❌ ServiceLocator anti-pattern (static service injection)
2. ⚠️ Multiple similar VOs: RouteStopInfo, RouteStopDetail, StopRouteDetail
3. ⚠️ Unclear distinction between VOs and DTOs

---

### 3. Domain Events Pattern

#### Banner (Reference) ✅
```java
@Getter
public abstract class BannerDomainEvent implements DomainEvent {
    private final UUID eventId;
    private final Instant timestamp;
    private final int version;  // ✅ Event versioning!

    protected BannerDomainEvent() {
        this.eventId = UUID.randomUUID();
        this.timestamp = Instant.now();
        this.version = getCurrentVersion();
    }

    public abstract int getCurrentVersion();
}

@Getter
@EqualsAndHashCode(callSuper = true)
public class BannerCreatedEvent extends BannerDomainEvent {
    public static final int CURRENT_VERSION = 1;

    private final UUID bannerId;
    private final String title;
    // ...

    @Override
    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }
}
```

#### Transport (Current) ⚠️
```java
public record BusStopCreatedEvent(
    UUID stopId,
    String stopName,
    BigDecimal latitude,
    BigDecimal longitude,
    String createdBy,
    Instant timestamp
) implements DomainEvent {
    // ❌ No event versioning
    // ❌ No event ID
    // ❌ No base class
}
```

**Problems Identified:**
1. ❌ No event versioning support
2. ❌ No base class for common fields (eventId, version)
3. ❌ Inconsistent event structure across events

---

### 4. Repository Pattern

#### Banner (Reference) ✅
```java
// Domain layer - interface
public interface AdminBannerRepository extends BaseRepository<Banner, BannerId> {
    Flux<Banner> findByTypeAndActive(BannerType type);  // ✅ Uses enum
    Mono<Long> countByType(BannerType type);
}

// Infrastructure layer - implementation
@Repository
public class R2dbcAdminBannerRepository
    extends BannerBaseRepository
    implements AdminBannerRepository {

    @Override
    public Flux<Banner> findByTypeAndActive(BannerType type) {
        String sql = """
            SELECT * FROM banners
            WHERE type = :type AND is_active = true
        """;
        return databaseClient.sql(sql)
            .bind("type", type.getValue())  // ✅ Type-safe
            .map(getRowMapper())
            .all();
    }
}
```

#### Transport (Current) ⚠️
```java
// Domain layer
public interface BusRouteRepository
    extends BaseRepository<BusRoute, BusRouteId> {

    Mono<BusRoute> findByRouteNumber(String routeNumber);
    Flux<BusRoute> findActiveRoutes();

    // ❌ Too many responsibilities - should be split
    Mono<RouteStopsData> getRouteStopsInfo(BusRouteId id);
    Mono<RouteVehicleStatistics> getRouteVehicleStatistics(BusRouteId id);
    Flux<RouteInAreaInfo> findRoutesIntersectingArea(
        BigDecimal lat, BigDecimal lon, double radiusKm
    );
    // ... 6+ specialized query methods
}

// Infrastructure layer
@Repository
public class R2dbcBusRouteRepository
    extends BaseR2dbcRepository<BusRoute, BusRouteId>
    implements BusRouteRepository {

    @Override
    public Flux<RouteInAreaInfo> findRoutesIntersectingArea(...) {
        // ❌ 50+ lines of complex SQL with PostGIS
        // ❌ Business logic embedded in SQL (rush hour calculation)
        String sql = """
            WITH route_stats AS (
                SELECT
                    br.id,
                    -- 40+ more lines of SQL...
                    CASE
                        WHEN EXTRACT(HOUR FROM NOW()) BETWEEN 7 AND 9
                        THEN 12  -- ❌ Hardcoded rush hour speed
                        WHEN EXTRACT(HOUR FROM NOW()) BETWEEN 17 AND 19
                        THEN 12
                        ELSE 18
                    END as avg_speed_kmh
                -- ...
            )
            SELECT * FROM route_stats
            WHERE ST_Intersects(...)
        """;
        return databaseClient.sql(sql)...;
    }
}
```

**Problems Identified:**
1. ❌ Interface Segregation violation - too many methods in one interface
2. ❌ Business logic in SQL (rush hours, speed calculations)
3. ❌ Complex queries hard to test and maintain
4. ⚠️ Should extract to query services or specifications

---

### 5. Use Case Pattern

#### Banner (Reference) ✅
```java
@Service
@RequiredArgsConstructor
public class CreateBannerUseCase
    extends BaseUseCase<CreateBannerCommand, BannerResponse> {

    private final AdminBannerRepository bannerRepository;
    private final BannerFactory bannerFactory;
    private final BannerImageProcessor bannerImageProcessor;
    private final BannerResponseMapper bannerResponseMapper;  // ✅ Dedicated mapper

    @Override
    protected Mono<BannerResponse> processInternal(
        CreateBannerCommand command
    ) {
        return bannerImageProcessor.process(command.imageUrl())
            .flatMap(url -> bannerFactory.create(command, url))  // ✅ Factory
            .flatMap(bannerRepository::save)
            .flatMap(bannerResponseMapper::toResponse);  // ✅ Mapper
    }
}
```

#### Transport (Current) ⚠️
```java
@Service
@RequiredArgsConstructor
public class CreateBusRouteUseCase
    extends BaseUseCase<CreateBusRoute, RouteData> {

    private final BusRouteRepository busRouteRepository;
    private final BusStopRepository busStopRepository;
    private final RouteStopRepository routeStopRepository;
    private final RouteDtoMappingService routeDtoMappingService;

    @Override
    protected Mono<RouteData> processInternal(CreateBusRoute command) {
        // ❌ Complex validation logic here (should be in domain)
        return validateRouteNumber(command.routeNumber())
            .then(validateStops(command.forwardStopIds()))
            .then(createRoute(command))
            .flatMap(route -> {
                // ❌ Direct entity creation (no factory)
                BusRoute entity = new BusRoute(
                    BusRouteId.generate(),
                    command.routeNumber(),
                    // ... 10 more parameters
                );
                return busRouteRepository.save(entity);
            })
            .flatMap(saved ->
                routeStopRepository.saveRouteStops(...)  // ❌ Multi-repo coordination
            )
            .flatMap(routeDtoMappingService::toRouteDto);  // ⚠️ Mapper OK
    }

    // ❌ Validation logic in use case (should be in domain)
    private Mono<Void> validateRouteNumber(String number) { ... }
    private Mono<Void> validateStops(List<UUID> ids) { ... }
}
```

**Problems Identified:**
1. ❌ No factory for aggregate creation
2. ❌ Validation logic in use case instead of domain
3. ❌ Complex multi-repository coordination
4. ⚠️ Direct `new BusRoute()` constructor call (should use factory)

---

### 6. Domain Services

#### Banner (Reference) ✅
```java
@Component
@RequiredArgsConstructor
public class BannerConflictDetector {

    /**
     * Detects if a new banner conflicts with existing active banners
     * in the same type and overlapping period.
     */
    public Mono<Boolean> hasConflict(
        BannerType type,
        BannerPeriod period,
        BannerId excludeId
    ) {
        // ✅ Domain logic separated from use case
        BannerPeriodOverlapSpec spec = new BannerPeriodOverlapSpec(
            type, period, excludeId
        );
        return repository.findAll()
            .filter(spec::isSatisfiedBy)
            .hasElements();
    }
}
```

#### Transport (Current) ❌
```java
// ❌ NO DOMAIN SERVICES!
// Business logic scattered across:
// - Use cases (validation)
// - Repositories (SQL queries with business rules)
// - Infrastructure services (ETA calculation)
```

**Missing Domain Services:**
1. `RouteValidationService` - Validate route geometry, stops sequence
2. `VehicleAssignmentService` - Business rules for vehicle-route assignment
3. `ETACalculationService` - Extract from infrastructure, make it domain logic
4. `RouteStopSequenceService` - Validate stop ordering and direction

---

### 7. Application Layer DTOs

#### Banner (Reference) ✅
```java
// Clear separation: Command, Query, Response

// Commands (inputs)
public record CreateBannerCommand(
    String title,
    String type,
    String imageUrl,
    // ...
) {
    // ✅ Validation in factory method
    public static CreateBannerCommand create(...) {
        validate(...);
        return new CreateBannerCommand(...);
    }
}

// Queries (search criteria)
public record BannerPaginationQuery(
    int page,
    int size,
    String sortField,
    String sortOrder,
    Boolean activeOnly
) {
    // ✅ Validation
    public static BannerPaginationQuery create(...) {
        validatePagination(page, size);
        validateSortOrder(sortOrder);
        return new BannerPaginationQuery(...);
    }
}

// Responses (outputs)
public record BannerResponse(
    UUID id,
    String title,
    String type,
    // ... mapped from domain
) {}
```

#### Transport (Current) ⚠️
```java
// ❌ Inconsistent naming: RouteData, CreateRoute, RouteDetail, RouteShortDetail
// ❌ Duplication: RouteStopInfo, RouteStopDetail, StopRouteDetail

public record RouteData(...) {}           // ❌ Generic name
public record CreateRoute(...) {}         // ⚠️ Should be CreateRouteCommand
public record UpdateRoute(...) {}         // ⚠️ Should be UpdateRouteCommand
public record RouteDetail(...) {}         // ❌ vs RouteShortDetail?
public record RouteShortDetail(...) {}    // ❌ Unclear difference
public record RouteStopInfo(...) {}       // ❌ vs RouteStopDetail?
public record RouteStopDetail(...) {}     // ❌ vs StopRouteDetail?
public record StopRouteDetail(...) {}     // ❌ vs RouteStopDetail?

// No validation in DTOs
```

**Problems Identified:**
1. ❌ Inconsistent naming convention
2. ❌ Duplication of similar DTOs (Info vs Detail vs Data)
3. ❌ No clear Command/Query/Response separation
4. ❌ Missing validation in DTO factory methods

---

### 8. Infrastructure Services

#### Banner (Reference) ✅
```java
@Service
@RequiredArgsConstructor
@Slf4j  // ✅ Logging
public class BannerStorageService extends BaseImageStorageService
    implements BannerStorage {

    @Override
    public Mono<String> save(String base64Data) {
        log.debug("Saving banner image, data length: {}",
            base64Data != null ? base64Data.length() : 0);

        return storeBase64Image(base64Data)
            .map(Result::getDisplayUrl)
            .doOnSuccess(url ->
                log.info("Banner image saved successfully: {}", url))
            .doOnError(error ->
                log.error("Failed to save banner image", error));
    }

    // ✅ Single responsibility - only file storage
}
```

#### Transport (Current) ❌
```java
@Service
@RequiredArgsConstructor
public class BusStopRealTimeServiceImpl
    implements BusStopRealTimeService {

    // ❌ CRITICAL: Cross-boundary dependency!
    // Line 3: import biz.ugur.busroutebackend.admin.domain.exceptions.BusStopException;

    private final DatabaseClient databaseClient;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final VehicleRepository vehicleRepository;

    @Override
    public Flux<BusArrivalInfo> getStopArrivals(UUID stopId) {
        // ❌ SRP Violation - does everything:
        // 1. Complex SQL query (150+ lines)
        // 2. ETA calculation with hardcoded business rules
        // 3. Redis caching
        // 4. Distance calculation
        // 5. Performance logging

        String sql = """
            WITH vehicle_positions AS (
                SELECT
                    v.id,
                    v.route_number,
                    v.current_latitude,
                    v.current_longitude,
                    -- ❌ Hardcoded rush hour logic
                    CASE
                        WHEN EXTRACT(HOUR FROM NOW()) BETWEEN 7 AND 9 THEN 12
                        WHEN EXTRACT(HOUR FROM NOW()) BETWEEN 12 AND 14 THEN 12
                        WHEN EXTRACT(HOUR FROM NOW()) BETWEEN 17 AND 19 THEN 12
                        ELSE 18
                    END as avg_speed_kmh,
                    -- ... 100+ more lines
            )
            SELECT ...
        """;

        return databaseClient.sql(sql)
            .bind("stopId", stopId)
            .map(this::mapToArrivalInfo)
            .all()
            .collectList()
            .flatMapMany(arrivals -> {
                // ❌ Redis caching logic mixed in
                return redisTemplate.opsForValue()
                    .set("arrivals:" + stopId, serialize(arrivals),
                        Duration.ofSeconds(15))
                    .thenMany(Flux.fromIterable(arrivals));
            });
    }
}
```

**Problems Identified:**
1. 🔴 **CRITICAL:** Cross-boundary exception usage (admin.domain.exceptions)
2. ❌ SRP violation - 5+ responsibilities in one class
3. ❌ Business logic in SQL (rush hours, speed estimates)
4. ❌ No logging
5. ❌ Complex SQL hard to test
6. ❌ 356 lines in single class

---

### 9. Event Handling

#### Banner (Reference) ✅
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class BannerEventHandler {

    private final BannerCacheService cacheService;

    @EventListener
    public Mono<Void> handleBannerCreated(BannerCreatedEvent event) {
        log.info("Handling BannerCreatedEvent: {}", event.getBannerId());

        return cacheService.invalidateAll()
            .doOnSuccess(v ->
                log.debug("Cache invalidated after banner creation"))
            .doOnError(error ->
                log.error("Failed to invalidate cache", error))
            .onErrorResume(error -> Mono.empty());  // ✅ Error handling
    }
}
```

#### Transport (Current) ⚠️
```java
@Component
@RequiredArgsConstructor
public class VehicleEventHandler {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final VehiclePositionWebSocketPublisher wsPublisher;

    @EventListener
    public void handleVehiclePositionUpdated(
        VehiclePositionUpdatedEvent event
    ) {
        // ❌ Fire-and-forget with .subscribe()
        cachePosition(event).subscribe();  // No error handling!
        updateStatistics(event).subscribe();  // No error handling!
        publishToWebSocket(event).subscribe();  // No error handling!

        // ❌ If any operation fails, no one knows!
    }

    private Mono<Void> cachePosition(VehiclePositionUpdatedEvent event) {
        // ❌ SRP violation - caching logic in event handler
        String key = "vehicle:position:" + event.getVehicleId();
        return redisTemplate.opsForValue()
            .set(key, serialize(event), Duration.ofMinutes(10))
            .then();
    }
}
```

**Problems Identified:**
1. ❌ Fire-and-forget event processing (`.subscribe()`)
2. ❌ No error handling for failed operations
3. ❌ SRP violation - caching logic mixed with event handling
4. ❌ No logging
5. ⚠️ Multiple responsibilities in one handler

---

## 🔧 Detailed Refactoring Plan

### Phase 1: Foundation (Week 1)

#### Task 1.1: Create Transport Domain Event Base Class
**Priority:** 🔴 Critical
**Estimated Time:** 2 hours

**Actions:**
1. Create `TransportDomainEvent` abstract base class
2. Add event versioning support (eventId, timestamp, version)
3. Update all 9 existing domain events to extend base class
4. Add `CURRENT_VERSION = 1` to each event

**Files to Create:**
- `transport/domain/event/TransportDomainEvent.java`

**Files to Modify:**
- All 9 event files in `transport/domain/event/`

**Tests:**
- Unit tests for event versioning
- Verify events can be serialized/deserialized

---

#### Task 1.2: Fix Cross-Boundary Dependencies
**Priority:** 🔴 Critical
**Estimated Time:** 3 hours

**Actions:**
1. Create `BusStopException` in `transport/domain/exceptions/`
2. Replace all imports of `admin.domain.exceptions.BusStopException`
3. Update exception hierarchy in transport context
4. Verify no cross-BC imports remain

**Files to Create:**
- `transport/domain/exceptions/BusStopException.java`

**Files to Modify:**
- `BusStopRealTimeServiceImpl.java` (remove admin import)

**Tests:**
- Verify exception throwing/catching works
- Integration tests for error scenarios

---

#### Task 1.3: Extract Domain Services
**Priority:** 🔴 Critical
**Estimated Time:** 8 hours

**Actions:**
1. Create `ETACalculationService` domain service
2. Extract hardcoded business rules (rush hours, speeds) to configuration
3. Create `RouteValidationService` domain service
4. Create `VehicleAssignmentService` domain service
5. Move business logic from SQL to domain services

**Files to Create:**
- `transport/domain/services/ETACalculationService.java`
- `transport/domain/services/RouteValidationService.java`
- `transport/domain/services/VehicleAssignmentService.java`
- `transport/domain/services/RouteStopSequenceService.java`

**Configuration to Add:**
```yaml
business:
  transport:
    eta:
      rush-hours:
        morning: [7, 8, 9]
        lunch: [12, 13, 14]
        evening: [17, 18, 19]
      speeds:
        rush-hour-kmh: 12
        normal-kmh: 18
        optimal-kmh: 25
```

**Tests:**
- Unit tests for each domain service
- Test business rules independently of infrastructure

---

### Phase 2: Aggregate Refactoring (Week 2)

#### Task 2.1: Make BusRoute Immutable
**Priority:** 🟡 High
**Estimated Time:** 6 hours

**Actions:**
1. Change `@Data` to `@Getter` + `@Builder(toBuilder = true)`
2. Make all fields `final`
3. Replace mutating methods with methods returning new instances
4. Remove transient `busStops` field - query through repository
5. Create factory methods for aggregate creation
6. Add validation to all business methods

**Before:**
```java
@Data
@Builder
public class BusRoute extends AggregateRoot<BusRoute, BusRouteId> {
    private BusRouteId id;
    private String routeNumber;

    @Transient
    private List<BusStop> busStops;

    public void updateRouteGeometry(String forward, String backward) {
        this.routeGeometryForward = forward;
        this.routeGeometryBackward = backward;
        registerEvent(new RouteGeometryUpdatedEvent(...));
    }
}
```

**After:**
```java
@Getter
@Builder(toBuilder = true)
public final class BusRoute extends AggregateRoot<BusRoute, BusRouteId> {
    private final BusRouteId id;
    private final String routeNumber;
    private final String routeName;
    // ... all final

    public static BusRoute create(
        String routeNumber,
        String routeName,
        String routeColor,
        // ...
    ) {
        validateRouteNumber(routeNumber);
        validateRouteName(routeName);

        BusRoute route = BusRoute.builder()
            .id(BusRouteId.generate())
            .routeNumber(routeNumber)
            .routeName(routeName)
            // ...
            .build();
        route.registerEvent(new RouteCreatedEvent(...));
        return route;
    }

    public BusRoute updateGeometry(
        RouteGeometry forward,
        RouteGeometry backward
    ) {
        validateGeometry(forward);
        validateGeometry(backward);

        BusRoute updated = this.toBuilder()
            .routeGeometryForward(forward.getWkt())
            .routeGeometryBackward(backward.getWkt())
            .build();
        updated.registerEvent(new RouteGeometryUpdatedEvent(...));
        return updated;
    }

    private static void validateRouteNumber(String number) {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException(
                "Route number cannot be null or empty"
            );
        }
    }
}
```

**Files to Modify:**
- `transport/domain/model/BusRoute.java`

**Files to Update (Use Cases):**
- `CreateBusRouteUseCase.java`
- `UpdateBusRouteUseCase.java`
- All other use cases using BusRoute

**Tests:**
- Unit tests for immutability
- Test each business method returns new instance
- Test validation logic

---

#### Task 2.2: Make BusStop Immutable
**Priority:** 🟡 High
**Estimated Time:** 4 hours

**Actions:**
1. Make all fields `final`
2. Consolidate 4 constructors into factory methods
3. Replace mutating methods with immutable updates
4. Fix TODO comment (get user from security context)

**Tests:**
- Unit tests for BusStop aggregate
- Test all factory methods
- Test event generation

---

#### Task 2.3: Make Vehicle Immutable
**Priority:** 🟡 High
**Estimated Time:** 4 hours

**Actions:**
1. Make all fields `final`
2. Fix typo: `@Column("course ")` → `@Column("course")`
3. Replace mutating methods with immutable updates
4. Extract coordinate validation to domain service

**Tests:**
- Unit tests for Vehicle aggregate
- Test position updates return new instance
- Test route assignment

---

### Phase 3: Repository & Infrastructure (Week 3)

#### Task 3.1: Split Large Repository Interfaces
**Priority:** 🟡 Medium
**Estimated Time:** 6 hours

**Actions:**
1. Apply Interface Segregation Principle
2. Split `BusRouteRepository` into focused interfaces:
   - `BusRouteRepository` (basic CRUD)
   - `BusRouteQueryService` (complex queries)
   - `RouteGeometryQueryService` (geospatial queries)
   - `RouteStatisticsQueryService` (statistics)

**Before:**
```java
public interface BusRouteRepository
    extends BaseRepository<BusRoute, BusRouteId> {
    Mono<BusRoute> findByRouteNumber(String number);
    Flux<BusRoute> findActiveRoutes();
    Mono<RouteStopsData> getRouteStopsInfo(BusRouteId id);
    Mono<RouteVehicleStatistics> getRouteVehicleStatistics(BusRouteId id);
    Flux<RouteInAreaInfo> findRoutesIntersectingArea(...);
    // ... 6+ methods - too many!
}
```

**After:**
```java
// Core repository - only aggregate operations
public interface BusRouteRepository
    extends BaseRepository<BusRoute, BusRouteId> {
    Mono<BusRoute> findByRouteNumber(String number);
    Flux<BusRoute> findActiveRoutes();
}

// Separate query service
@Service
public interface RouteQueryService {
    Mono<RouteStopsData> getRouteStopsInfo(BusRouteId id);
    Flux<RouteInAreaInfo> findRoutesInArea(
        Coordinates center,
        Distance radius
    );
}

// Separate statistics service
@Service
public interface RouteStatisticsService {
    Mono<RouteVehicleStatistics> getVehicleStatistics(BusRouteId id);
    Mono<RouteStopsStatistics> getStopStatistics(BusRouteId id);
}
```

**Files to Create:**
- `transport/application/services/RouteQueryService.java`
- `transport/application/services/RouteStatisticsService.java`
- `transport/infrastructure/query/RouteQueryServiceImpl.java`
- `transport/infrastructure/statistics/RouteStatisticsServiceImpl.java`

**Files to Modify:**
- `transport/domain/repository/BusRouteRepository.java` (simplify)
- All use cases using these methods

**Tests:**
- Integration tests for new services
- Verify query performance

---

#### Task 3.2: Refactor BusStopRealTimeServiceImpl
**Priority:** 🟡 High
**Estimated Time:** 10 hours

**Actions:**
1. Extract SQL to separate query builder class
2. Move ETA calculation to `ETACalculationService` domain service
3. Extract caching to separate service
4. Add comprehensive logging
5. Reduce class from 356 lines to <100 lines

**Before (356 lines):**
```java
@Service
public class BusStopRealTimeServiceImpl {
    // Everything in one class!
}
```

**After:**
```java
// Domain service
@Component
public class ETACalculationService {
    public Mono<Duration> calculateETA(
        Coordinates from,
        Coordinates to,
        LocalTime currentTime,
        double avgSpeedKmh
    ) {
        // Business logic here
    }
}

// Infrastructure query
@Service
public class BusStopArrivalQuery {
    public Flux<VehicleArrivalData> findArrivingVehicles(
        UUID stopId,
        Duration timeWindow
    ) {
        // SQL query only
    }
}

// Application service
@Service
@RequiredArgsConstructor
@Slf4j
public class BusStopRealTimeServiceImpl {
    private final BusStopArrivalQuery arrivalQuery;
    private final ETACalculationService etaService;
    private final ArrivalCacheService cacheService;

    @Override
    public Flux<BusArrivalInfo> getStopArrivals(UUID stopId) {
        log.debug("Fetching arrivals for stop: {}", stopId);

        return cacheService.get(stopId)
            .switchIfEmpty(
                arrivalQuery.findArrivingVehicles(stopId, Duration.ofMinutes(30))
                    .flatMap(this::enrichWithETA)
                    .collectList()
                    .flatMap(arrivals ->
                        cacheService.cache(stopId, arrivals)
                            .thenReturn(arrivals)
                    )
                    .flatMapMany(Flux::fromIterable)
            )
            .doOnComplete(() ->
                log.debug("Completed arrivals fetch for stop: {}", stopId))
            .doOnError(error ->
                log.error("Failed to fetch arrivals for stop: {}", stopId, error));
    }

    private Mono<BusArrivalInfo> enrichWithETA(VehicleArrivalData data) {
        return etaService.calculateETA(
            data.vehiclePosition(),
            data.stopPosition(),
            LocalTime.now(),
            data.routeAvgSpeed()
        ).map(eta -> new BusArrivalInfo(
            data.routeNumber(),
            data.vehicleLicensePlate(),
            eta
        ));
    }
}
```

**Files to Create:**
- `transport/domain/services/ETACalculationService.java`
- `transport/infrastructure/query/BusStopArrivalQuery.java`
- `transport/infrastructure/cache/ArrivalCacheService.java`

**Files to Modify:**
- `transport/infrastructure/services/BusStopRealTimeServiceImpl.java`

**Tests:**
- Unit tests for ETACalculationService
- Integration tests for BusStopArrivalQuery
- Mock tests for BusStopRealTimeServiceImpl

---

#### Task 3.3: Fix RouteGeometry ServiceLocator Anti-pattern
**Priority:** 🟡 Medium
**Estimated Time:** 4 hours

**Actions:**
1. Remove static `DistanceCalculationService` injection
2. Move distance calculation out of value object
3. Create `RouteGeometryService` for complex operations

**Before:**
```java
@Value
public class RouteGeometry {
    String wkt;

    private static DistanceCalculationService distanceService;

    public static void setDistanceCalculationService(
        DistanceCalculationService service
    ) {
        RouteGeometry.distanceService = service;
    }

    public double calculateDistance() {
        return distanceService.calculate(this.wkt);
    }
}
```

**After:**
```java
// Pure value object - no services
@Value
public class RouteGeometry {
    String wkt;

    public static RouteGeometry fromWkt(String wkt) {
        validate(wkt);
        return new RouteGeometry(wkt);
    }

    public List<Coordinates> getCoordinates() {
        return WktParser.parseLineString(wkt);
    }
}

// Domain service for operations
@Component
@RequiredArgsConstructor
public class RouteGeometryService {
    private final DistanceCalculationService distanceService;

    public double calculateDistance(RouteGeometry geometry) {
        List<Coordinates> points = geometry.getCoordinates();
        return distanceService.calculatePathDistance(points);
    }
}
```

**Files to Modify:**
- `transport/domain/valueobject/RouteGeometry.java`

**Files to Create:**
- `transport/domain/services/RouteGeometryService.java`

**Tests:**
- Unit tests for RouteGeometryService
- Verify no static dependencies

---

### Phase 4: Application Layer Cleanup (Week 4)

#### Task 4.1: Consolidate DTOs
**Priority:** 🟡 Medium
**Estimated Time:** 8 hours

**Actions:**
1. Rename DTOs following Command/Query/Response pattern
2. Remove duplicates (RouteStopInfo vs RouteStopDetail vs StopRouteDetail)
3. Add validation to DTO factory methods
4. Document DTO purpose and usage

**Mapping:**
```
BEFORE                    → AFTER (Command/Query/Response)
────────────────────────────────────────────────────────
CreateRoute               → CreateRouteCommand
UpdateRoute               → UpdateRouteCommand
RouteData                 → RouteResponse
RouteDetail               → RouteDetailResponse
RouteShortDetail          → (merge into RouteResponse)
RouteStopInfo             → RouteStopResponse
RouteStopDetail           → (remove - duplicate)
StopRouteDetail           → (remove - duplicate)
GetAllRoutePaginationQuery → RoutePaginationQuery
```

**Files to Create:**
- Clear naming guide document

**Files to Modify:**
- All DTO files in `transport/application/dto/`
- All use cases using these DTOs
- All controllers

**Tests:**
- Verify DTO validation
- Test serialization/deserialization

---

#### Task 4.2: Create Factories for Aggregates
**Priority:** 🟡 Medium
**Estimated Time:** 6 hours

**Actions:**
1. Create `BusRouteFactory` for route creation
2. Create `BusStopFactory` for stop creation
3. Create `VehicleFactory` for vehicle registration
4. Move complex creation logic from use cases to factories

**Example:**
```java
@Component
@RequiredArgsConstructor
public class BusRouteFactory {
    private final RouteValidationService validationService;
    private final RouteGeometryService geometryService;

    public Mono<BusRoute> create(CreateRouteCommand command) {
        return validationService.validateRouteNumber(command.routeNumber())
            .then(validateGeometry(command.forwardGeometry()))
            .then(validateGeometry(command.backwardGeometry()))
            .map(v -> BusRoute.create(
                command.routeNumber(),
                command.routeName(),
                command.routeColor(),
                command.forwardGeometry(),
                command.backwardGeometry()
                // ...
            ));
    }
}
```

**Files to Create:**
- `transport/application/factory/BusRouteFactory.java`
- `transport/application/factory/BusStopFactory.java`
- `transport/application/factory/VehicleFactory.java`

**Files to Modify:**
- All create use cases

**Tests:**
- Unit tests for each factory
- Test validation logic

---

#### Task 4.3: Improve Event Handling
**Priority:** 🟡 Medium
**Estimated Time:** 4 hours

**Actions:**
1. Replace `.subscribe()` with proper reactive chains
2. Add error handling to all event handlers
3. Add logging
4. Extract caching logic to separate services

**Before:**
```java
@EventListener
public void handleVehiclePositionUpdated(VehiclePositionUpdatedEvent event) {
    cachePosition(event).subscribe();  // Fire-and-forget!
}
```

**After:**
```java
@EventListener
public Mono<Void> handleVehiclePositionUpdated(
    VehiclePositionUpdatedEvent event
) {
    log.debug("Handling VehiclePositionUpdatedEvent: {}", event.getVehicleId());

    return Mono.when(
        vehicleCacheService.cachePosition(event),
        vehicleStatisticsService.updateStatistics(event),
        wsPublisher.publishPositionUpdate(event)
    )
    .doOnSuccess(v ->
        log.debug("Successfully processed position update for: {}",
            event.getVehicleId()))
    .doOnError(error ->
        log.error("Failed to process position update for: {}",
            event.getVehicleId(), error))
    .onErrorResume(error -> Mono.empty());  // Don't fail the event
}
```

**Files to Modify:**
- `transport/infrastructure/messaging/VehicleEventHandler.java`

**Files to Create:**
- `transport/infrastructure/cache/VehicleCacheService.java`
- `transport/infrastructure/statistics/VehicleStatisticsService.java`

**Tests:**
- Test event handling with mocks
- Test error scenarios

---

### Phase 5: Testing & Documentation (Week 5)

#### Task 5.1: Create Comprehensive Unit Tests
**Priority:** 🟢 Medium
**Estimated Time:** 12 hours

**Actions:**
1. Create unit tests for all aggregates (BusRoute, BusStop, Vehicle)
2. Create unit tests for all domain services
3. Create unit tests for all value objects
4. Aim for >80% coverage of domain layer

**Files to Create:**
- `transport/domain/model/BusRouteTest.java`
- `transport/domain/model/BusStopTest.java`
- `transport/domain/model/VehicleTest.java`
- `transport/domain/services/ETACalculationServiceTest.java`
- `transport/domain/services/RouteValidationServiceTest.java`
- `transport/domain/services/VehicleAssignmentServiceTest.java`
- (more test files...)

**Test Coverage:**
- Immutability guarantees
- Domain event generation
- Validation logic
- Business rules
- Edge cases

---

#### Task 5.2: Create Integration Tests
**Priority:** 🟢 Low
**Estimated Time:** 8 hours

**Actions:**
1. Create repository integration tests
2. Create use case integration tests
3. Use Testcontainers for PostgreSQL and Redis

**Files to Create:**
- `transport/infrastructure/repository/BusRouteRepositoryIntegrationTest.java`
- `transport/application/usecase/CreateBusRouteUseCaseIntegrationTest.java`
- (more test files...)

---

#### Task 5.3: Create Refactoring Report
**Priority:** 🟢 Low
**Estimated Time:** 4 hours

**Actions:**
1. Document all changes made
2. Create before/after comparison
3. Document architectural decisions
4. Create migration guide for developers

**Files to Create:**
- `TRANSPORT_REFACTORING_REPORT.md` (like banner and admin)

---

## 📈 Success Metrics

### Before Refactoring
- Cross-BC Dependencies: 2
- Architecture Violations: 10+
- Immutable Aggregates: 0/3
- Domain Services: 0
- Test Coverage: Unknown
- DTO Duplication: ~30%
- SRP Violations: 5+ classes
- Lines of Complex SQL: 500+

### After Refactoring (Target)
- Cross-BC Dependencies: 0 ✅
- Architecture Violations: 0 ✅
- Immutable Aggregates: 3/3 ✅
- Domain Services: 4+ ✅
- Test Coverage: >80% ✅
- DTO Duplication: 0% ✅
- SRP Violations: 0 ✅
- Lines of Complex SQL: <200 ✅

---

## 🚨 Risks & Mitigation

### Risk 1: Breaking Existing Functionality
**Mitigation:**
- Run full test suite after each phase
- Create comprehensive integration tests
- Deploy to staging environment
- Monitor for regressions

### Risk 2: Performance Degradation
**Mitigation:**
- Benchmark critical queries before/after
- Monitor database query performance
- Profile application with production-like load
- Optimize if needed (keep queries efficient)

### Risk 3: Time Overrun
**Mitigation:**
- Each task is independently valuable
- Can stop after any phase
- Prioritize critical fixes first (Phase 1)
- Defer nice-to-haves (Phase 5)

### Risk 4: Team Knowledge Gap
**Mitigation:**
- Document all changes thoroughly
- Pair programming sessions
- Code review for each PR
- Create training materials

---

## 📝 Implementation Strategy

### Approach: Incremental Refactoring

1. **Don't Rewrite - Refactor:**
   - Each change is a small, testable improvement
   - Always keep system working
   - No "big bang" rewrites

2. **Test-Driven:**
   - Write tests before refactoring
   - Ensure tests pass after each change
   - Use tests as safety net

3. **Review & Iterate:**
   - Code review for each task
   - Get feedback early
   - Adjust plan as needed

4. **Continuous Integration:**
   - Merge frequently to main branch
   - Run CI/CD pipeline after each merge
   - Deploy to staging for testing

---

## 🎓 Learning Resources

### For Team Members

**DDD Concepts:**
- Aggregate immutability: Banner context examples
- Domain events with versioning: Admin context examples
- Domain services: Banner `BannerConflictDetector`
- Specifications: Banner `BannerPeriodOverlapSpec`

**Clean Architecture:**
- Layer separation: See all refactored contexts
- Dependency inversion: Repository pattern usage
- Use case pattern: BaseUseCase implementation

**SOLID Principles:**
- SRP: BannerResponseMapper vs old scattered mapping
- ISP: Split large repository interfaces
- DIP: Depend on abstractions, not implementations

---

## ✅ Next Steps

1. **Review this plan** with team
2. **Get approval** for refactoring scope
3. **Create JIRA tickets** for each task
4. **Assign to developers** based on expertise
5. **Start with Phase 1** (critical fixes)
6. **Weekly progress reviews**

---

## 📞 Questions & Support

**Questions about this plan?**
- Review banner/admin refactoring reports
- Check CLAUDE.md for project guidelines
- Consult with tech lead

**Ready to start?**
- Begin with Phase 1, Task 1.1
- Create feature branch: `refactor/transport-phase-1`
- Follow test-driven approach
- Submit PR when task complete

---

**Document Version:** 1.0
**Last Updated:** 2025-10-30
**Author:** Claude Code
**Status:** Ready for Review
