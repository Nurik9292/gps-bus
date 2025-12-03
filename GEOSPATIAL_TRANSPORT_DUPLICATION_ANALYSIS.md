# Geospatial and Transport Bounded Context Duplication Analysis

**Date:** 2025-10-30
**Analyzed By:** Claude Code
**Scope:** Identify code duplication between geospatial and transport modules

---

## Executive Summary

✅ **Good News:** Most geospatial logic is properly centralized in the `geospatial` module
❌ **Issues Found:** 2 critical duplications of Haversine distance calculation in transport
⚠️ **Improvement Opportunity:** PostGIS query builders not consistently used in repositories

---

## 1. Geospatial Module Structure

### Purpose
Centralized geospatial logic serving all bounded contexts.

### Components

#### Domain Layer
- **`Coordinates`** (value object) - Immutable coordinate representation with validation
  - Location: `geospatial/domain/valueobjects/Coordinates.java`
  - Features: WKT/GeoJSON conversion, bearing calculation, validation
  - Usage: ✅ Properly reused in transport (BusStop, Vehicle, BusRoute)

- **`Distance`** (value object) - Distance representation with conversions
  - Location: `geospatial/domain/valueobjects/Distance.java`
  - Features: Unit conversions, comparisons, walking time calculations
  - Usage: ⚠️ **Underutilized** in transport module

- **`BoundingBox`** (value object) - Geographic area representation
  - Location: `geospatial/domain/valueobjects/BoundingBox.java`
  - Features: Bounds checking, area validation
  - Usage: ✅ Could be used for spatial queries

- **`DistanceCalculationService`** (domain service)
  - Location: `geospatial/domain/services/DistanceCalculationService.java`
  - Features: Haversine distance, path distance, point-to-line distance
  - **Critical:** R = 6,371,000 meters (correct)
  - Usage: ✅ Used by RouteGeometry, ❌ **NOT used by VehiclePosition and BusStopRealTimeServiceImpl**

- **`TurkmenistanBounds`** (constants)
  - Location: `geospatial/domain/constants/TurkmenistanBounds.java`
  - Features: Standard and strict bounds validation
  - Usage: ✅ Properly used in transport (VehicleValidationService)

- **`GeoConstants`** (constants)
  - Location: `geospatial/domain/constants/GeoConstants.java`
  - Features: Earth radius, walking speeds, search radiuses
  - Usage: ✅ Used by services

#### Infrastructure Layer
- **`PostGISQueryBuilder`** (utility)
  - Location: `geospatial/infrastructure/postgis/PostGISQueryBuilder.java`
  - Features: Standardized PostGIS query fragments
  - Methods: `geographyDistanceInMeters()`, `withinRadiusCondition()`, etc.
  - Usage: ⚠️ **NOT consistently used in transport repositories**

- **`PostGISConstants`** (constants)
  - Location: `geospatial/infrastructure/postgis/PostGISConstants.java`
  - Features: SRID, unit constants
  - Usage: Used by PostGISQueryBuilder

---

## 2. Transport Module Geospatial Usage

### ✅ Correct Usage (No Duplication)

#### Using geospatial.Coordinates
```java
// ✅ BusStop.java
private final Coordinates coordinates;

// ✅ Vehicle.java
private Coordinates currentPosition;

// ✅ RouteGeometry.java
private final List<Coordinates> points;
```

#### Using TurkmenistanBounds
```java
// ✅ VehicleValidationService.java
if (!TurkmenistanBounds.isWithinStandardBounds(lat, lon)) {
    log.warn("Coordinates ({}, {}) outside Turkmenistan bounds", lat, lon);
    return false;
}
```

#### Using DistanceCalculationService
```java
// ✅ RouteGeometry.java (via static injection)
private static DistanceCalculationService distanceService;

public double calculateDistanceMeters() {
    return distanceService.calculateDistance(
        prev.getLatitudeAsDouble(), prev.getLongitudeAsDouble(),
        curr.getLatitudeAsDouble(), curr.getLongitudeAsDouble()
    ).getMeters();
}
```

---

## 3. 🔴 Critical Issues: Duplicated Logic

### Issue #1: BusStopRealTimeServiceImpl.calculateDistance()

**Location:** `transport/infrastructure/services/BusStopRealTimeServiceImpl.java:144-153`

**Problem:** Duplicate Haversine implementation

```java
// ❌ DUPLICATE CODE
private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
    final int R = 6371;  // ⚠️ Using kilometers, inconsistent!
    double latDistance = Math.toRadians(lat2 - lat1);
    double lonDistance = Math.toRadians(lon2 - lon1);
    double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c * 1000;  // Convert to meters
}
```

**Usage:**
```java
// Line 117-119
calculateDistance(lat, lon,
    nearestStop.getLatitude().doubleValue(),
    nearestStop.getLongitude().doubleValue())
```

**Issues:**
- Duplicates `DistanceCalculationService.haversineDistance()`
- Inconsistent radius constant (6371 vs 6371000)
- Manual unit conversion (km → m)
- Not using `Distance` value object
- Violates DRY principle

**Recommendation:** Use `DistanceCalculationService` dependency

---

### Issue #2: VehiclePosition.distanceTo()

**Location:** `transport/domain/valueobject/VehiclePosition.java:26-44`

**Problem:** Duplicate Haversine implementation in domain value object

```java
// ❌ DUPLICATE CODE
public double distanceTo(Double lat, Double lon) {
    final int R = 6371000;  // Using meters

    double lat1Rad = Math.toRadians(this.latitude);
    double lat2Rad = Math.toRadians(lat);
    double deltaLatRad = Math.toRadians(lat - this.latitude);
    double deltaLonRad = Math.toRadians(lon - this.longitude);

    double a = Math.sin(deltaLatRad/2) * Math.sin(deltaLatRad/2) +
            Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                    Math.sin(deltaLonRad/2) * Math.sin(deltaLonRad/2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

    return R * c;  // Returns meters
}
```

**Issues:**
- Duplicates `DistanceCalculationService.haversineDistance()`
- Domain value object should not contain calculation logic
- Should use `Coordinates` value object instead
- Returns primitive `double` instead of `Distance` value object
- Violates Single Responsibility Principle

**Architecture Problem:** Value objects should be data holders, not calculation engines.

**Recommendation:**
1. Replace with `Coordinates` value object
2. Use `DistanceCalculationService` for calculations
3. Return `Distance` value object

---

## 4. ⚠️ Improvement Opportunities

### PostGIS Query Inconsistency

**Location:** Multiple repositories in `transport/infrastructure/persistence/repository/`

**Current State:** Direct SQL with PostGIS functions
```java
// R2dbcBusStopRepository.java:350
String sql = """
    SELECT ...
    ST_Distance(
        ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
        ST_SetSRID(ST_Point(:centerLon, :centerLat), 4326)::geography
    ) / 1000.0 as distance_km
    ...
""";
```

**Available Tool:** `PostGISQueryBuilder` in geospatial module
```java
// Could use:
String distanceColumn = PostGISQueryBuilder.geographyDistanceInKm(
    "longitude", "latitude",
    ":centerLon", ":centerLat"
);

String withinCondition = PostGISQueryBuilder.withinRadiusCondition(
    "longitude", "latitude",
    ":centerLon", ":centerLat",
    ":radiusMeters"
);
```

**Benefits:**
- ✅ Standardized PostGIS usage
- ✅ Less code duplication
- ✅ Easier to maintain and test
- ✅ Consistent SRID and geography casting

**Files Affected:**
- `R2dbcBusStopRepository.java` (lines 350, 368, 379)
- `R2dbcBusRouteRepository.java` (lines 270, 289)
- `R2dbcVehicleRepository.java` (potential usage)

---

## 5. Recommendations

### Priority 1: Fix Haversine Duplications

#### 5.1. Refactor BusStopRealTimeServiceImpl

**Current:**
```java
@Service
public class BusStopRealTimeServiceImpl implements BusStopRealTimeService {
    private final BusStopRepository busStopRepository;

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // 10 lines of duplicate Haversine code
    }
}
```

**Recommended:**
```java
@Service
public class BusStopRealTimeServiceImpl implements BusStopRealTimeService {
    private final BusStopRepository busStopRepository;
    private final DistanceCalculationService distanceCalculationService;  // ✅ Add dependency

    // ✅ Remove calculateDistance method

    // Usage:
    Distance distance = distanceCalculationService.calculateDistance(
        lat, lon,
        nearestStop.getLatitude().doubleValue(),
        nearestStop.getLongitude().doubleValue()
    );
    double distanceMeters = distance.getMeters();
}
```

**Impact:**
- Removes 10 lines of duplicate code
- Uses centralized, tested implementation
- Returns `Distance` value object (better type safety)

---

#### 5.2. Refactor VehiclePosition Value Object

**Current:**
```java
@Getter
@EqualsAndHashCode(callSuper = false)
public class VehiclePosition extends ValueObject {
    private final Double latitude;
    private final Double longitude;
    private final Double speedKmh;
    private final Boolean isInMotion;

    public double distanceTo(Double lat, Double lon) {
        // 14 lines of Haversine calculation
    }
}
```

**Recommended Option A:** Replace with Coordinates
```java
@Getter
@EqualsAndHashCode(callSuper = false)
public class VehiclePosition extends ValueObject {
    private final Coordinates coordinates;  // ✅ Use geospatial value object
    private final Double speedKmh;
    private final Boolean isInMotion;

    // Remove distanceTo - use DistanceCalculationService externally
    // Coordinates already has validation, conversions, etc.
}
```

**Recommended Option B:** Keep separate, delegate calculation
```java
@Getter
@EqualsAndHashCode(callSuper = false)
public class VehiclePosition extends ValueObject {
    private final Double latitude;
    private final Double longitude;
    private final Double speedKmh;
    private final Boolean isInMotion;

    // ✅ Remove distanceTo method entirely
    // Use DistanceCalculationService from calling code

    public Coordinates toCoordinates() {
        return Coordinates.of(latitude, longitude);
    }
}
```

**Recommendation:** **Option A** - Replace with `Coordinates`

**Rationale:**
- `Coordinates` is the canonical representation
- Already has validation, WKT/GeoJSON support
- Reduces code duplication
- Better domain model alignment

**Migration Impact:**
- Need to update all usages of `VehiclePosition`
- Change `getLatitude()` to `getCoordinates().getLatitudeAsDouble()`
- Change distance calculations to use `DistanceCalculationService`

---

### Priority 2: Standardize PostGIS Queries

#### 5.3. Use PostGISQueryBuilder in Repositories

**Example Refactoring for R2dbcBusStopRepository:**

**Before:**
```java
String sql = """
    SELECT *,
        ST_Distance(
            ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
            ST_SetSRID(ST_Point(:centerLon, :centerLat), 4326)::geography
        ) / 1000.0 as distance_km
    FROM bus_stops
    WHERE is_active = true
        AND ST_DWithin(
            ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
            ST_SetSRID(ST_Point(:centerLon, :centerLat), 4326)::geography,
            :radiusMeters
        )
    ORDER BY distance_km
""";
```

**After:**
```java
import static biz.ugur.busroutebackend.geospatial.infrastructure.postgis.PostGISQueryBuilder.*;

QueryFragment fragment = nearbyPointsQuery(
    "longitude", "latitude",
    ":centerLon", ":centerLat",
    ":radiusMeters",
    "distance_km", "km"
);

String sql = String.format("""
    SELECT *, %s
    FROM bus_stops
    WHERE is_active = true AND %s
    ORDER BY %s
    """,
    fragment.distanceColumn(),
    fragment.withinRadiusCondition(),
    fragment.orderByClause()
);
```

**Benefits:**
- 15 lines → 12 lines (20% reduction)
- Standardized PostGIS usage
- Easier to read and maintain
- Type-safe query building

---

### Priority 3: Increase Distance Value Object Usage

#### 5.4. Return Distance Instead of Primitives

**Current Pattern:**
```java
// Returns double
public double calculateDistance(...) {
    return 123.45;  // meters? kilometers? unclear
}
```

**Recommended Pattern:**
```java
// Returns Distance value object
public Distance calculateDistance(...) {
    return Distance.ofMeters(123.45);  // ✅ Clear units
}

// Usage
Distance distance = calculateDistance(...);
if (distance.isLessThan(Distance.ofKilometers(1))) {
    // ...
}
```

**Benefits:**
- Type safety (can't mix meters and kilometers)
- Rich API (comparisons, conversions, formatting)
- Self-documenting code

---

## 6. Implementation Plan

### Phase 1: Critical Fixes (High Priority)
1. ✅ Add `DistanceCalculationService` to `BusStopRealTimeServiceImpl`
2. ✅ Remove `calculateDistance()` method
3. ✅ Refactor `VehiclePosition` to use `Coordinates`
4. ✅ Update all `VehiclePosition` usages
5. ✅ Run tests to verify

**Estimated Effort:** 2-3 hours
**Risk:** Medium (touches core domain model)
**Impact:** Removes all Haversine duplications

### Phase 2: PostGIS Standardization (Medium Priority)
1. ⚠️ Refactor `R2dbcBusStopRepository` to use `PostGISQueryBuilder`
2. ⚠️ Refactor `R2dbcBusRouteRepository` to use `PostGISQueryBuilder`
3. ⚠️ Refactor `R2dbcVehicleRepository` to use `PostGISQueryBuilder`
4. ⚠️ Run integration tests

**Estimated Effort:** 3-4 hours
**Risk:** Low (infrastructure layer only)
**Impact:** Standardizes PostGIS usage

### Phase 3: Enhance Distance Usage (Low Priority)
1. 📋 Review all methods returning `double` for distances
2. 📋 Change signatures to return `Distance` value object
3. 📋 Update calling code
4. 📋 Add documentation

**Estimated Effort:** 4-5 hours
**Risk:** Low (improves type safety)
**Impact:** Better API design

---

## 7. Verification Checklist

After implementing recommendations:

- [ ] No Haversine implementations outside `geospatial` module
- [ ] All distance calculations use `DistanceCalculationService`
- [ ] `VehiclePosition` uses `Coordinates` or delegates to service
- [ ] PostGIS queries use `PostGISQueryBuilder` where possible
- [ ] All tests passing
- [ ] No regression in distance calculation accuracy
- [ ] Code review completed
- [ ] Documentation updated

---

## 8. Summary

### Current State
- ✅ Good separation: geospatial module exists and is well-designed
- ✅ Proper reuse: `Coordinates`, `TurkmenistanBounds` correctly used
- ❌ **2 Critical Issues:** Duplicate Haversine in `BusStopRealTimeServiceImpl` and `VehiclePosition`
- ⚠️ Improvement opportunity: PostGIS standardization

### Target State
- ✅ Zero Haversine duplications
- ✅ All distance calculations centralized
- ✅ Standardized PostGIS query building
- ✅ Consistent use of value objects

### Next Steps
1. **Immediate:** Fix Haversine duplications (Priority 1)
2. **Soon:** Standardize PostGIS queries (Priority 2)
3. **Later:** Enhance Distance value object usage (Priority 3)

---

**Analysis Complete**
For questions or clarifications, refer to source files listed in each section.
