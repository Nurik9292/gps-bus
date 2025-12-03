# Architecture Analysis: VehiclePosition → Coordinates Dependency

**Question:** Is it correct that `VehiclePosition` depends on `geospatial.domain.valueobjects.Coordinates`?

**Short Answer:** ✅ **Yes, it's correct** - but with important architectural context.

---

## Current Dependency

```java
// transport/domain/valueobject/VehiclePosition.java
package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates; // ⚠️ Cross-context dependency

public class VehiclePosition extends ValueObject {
    private final Coordinates coordinates; // Uses geospatial value object
    private final Double speedKmh;
    private final Boolean isInMotion;
}
```

**Dependency:** `transport` → `geospatial`

---

## DDD Context Analysis

### Current Bounded Contexts

```
┌─────────────────────────────────────────┐
│          Bounded Contexts                │
├─────────────────────────────────────────┤
│                                          │
│  ┌──────────────┐    ┌───────────────┐ │
│  │  transport   │───→│  geospatial   │ │
│  │   (domain)   │    │   (domain)    │ │
│  └──────────────┘    └───────────────┘ │
│         │                    │          │
│         │                    │          │
│         └────────┬───────────┘          │
│                  ↓                       │
│         ┌──────────────┐                │
│         │    shared    │                │
│         │   (kernel)   │                │
│         └──────────────┘                │
└─────────────────────────────────────────┘
```

---

## Two Valid Interpretations

### Interpretation 1: geospatial as **Shared Kernel** ✅

**This is the correct interpretation for this project.**

#### Definition
**Shared Kernel** - общее ядро, содержащее концепции, используемые несколькими bounded contexts.

#### Evidence in Code
```java
// geospatial is used by multiple contexts:

// 1. transport context
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.constants.TurkmenistanBounds;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;

// 2. routing context (если есть)
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Distance;

// 3. Любой контекст, работающий с координатами
```

#### Why This is Correct

1. **Ubiquitous Language** - `Coordinates` это общий язык для всей системы
   ```
   Координаты - это не "транспортная концепция"
   Координаты - это универсальная геопространственная концепция
   ```

2. **Single Source of Truth**
   ```java
   // ✅ ONE canonical Coordinates implementation
   // Used by: Vehicle, BusStop, BusRoute, TripPlan, etc.

   // ❌ NOT multiple incompatible implementations:
   // TransportCoordinates, RoutingCoordinates, StopCoordinates
   ```

3. **Shared Business Rules**
   ```java
   // All contexts need same coordinate validation:
   - WGS84 bounds (-90 to 90, -180 to 180)
   - Turkmenistan regional bounds
   - Precision (6 decimal places)
   - WKT/GeoJSON conversions
   ```

4. **Cross-Cutting Concerns**
   ```java
   // Distance calculation needed everywhere:
   - Transport: vehicle distance to stop
   - Routing: path distance calculation
   - Search: nearby stops/routes
   ```

#### Architectural Pattern: Shared Kernel

```
┌─────────────────────────────────────────────────────────┐
│                    geospatial (Shared Kernel)            │
│  ┌──────────────┐  ┌──────────┐  ┌──────────────────┐ │
│  │ Coordinates  │  │ Distance │  │ DistanceCalc     │ │
│  │ (value obj)  │  │ (value)  │  │ Service          │ │
│  └──────────────┘  └──────────┘  └──────────────────┘ │
└─────────────────────────────────────────────────────────┘
              ↑                ↑                ↑
              │                │                │
    ┌─────────┴────┐  ┌────────┴──────┐  ┌────┴─────────┐
    │  transport   │  │   routing     │  │    admin     │
    │   context    │  │   context     │  │   context    │
    └──────────────┘  └───────────────┘  └──────────────┘
```

**This is the CORRECT architecture for this project.**

#### Real-World Analogy
```
Shared Kernel = Standard Library

Just like:
- Java's java.time.* (shared time concepts)
- Java's java.math.BigDecimal (shared numeric concepts)

Our geospatial module:
- biz.ugur.geospatial.* (shared geospatial concepts)

Everyone uses the same library, nobody duplicates it.
```

---

### Interpretation 2: geospatial as **Separate Bounded Context** ⚠️

**This would be incorrect for this project, but let's analyze it.**

#### If geospatial were a separate context

```
┌──────────────┐        ┌───────────────┐
│  transport   │   ❌   │  geospatial   │
│   context    │───────→│   context     │
│              │        │ (separate BC) │
└──────────────┘        └───────────────┘

Problem: Tight coupling between bounded contexts
```

#### Why This Would Be Wrong

1. **Unnecessary Coupling**
   - Changes to geospatial BC affect transport BC
   - Violates bounded context independence

2. **Would Require ACL (Anti-Corruption Layer)**
   ```java
   // transport/domain/valueobject/VehiclePosition.java
   public class VehiclePosition {
       private final TransportCoordinates coordinates; // Own implementation
   }

   // transport/application/mapper/CoordinatesMapper.java
   public class CoordinatesMapper {
       // Convert between transport and geospatial coordinates
       public TransportCoordinates fromGeospatial(Coordinates coords) { ... }
       public Coordinates toGeospatial(TransportCoordinates coords) { ... }
   }
   ```

3. **Code Duplication**
   ```java
   // ❌ BAD: Multiple implementations of same concept

   // transport/domain/valueobject/TransportCoordinates.java
   public class TransportCoordinates {
       private final BigDecimal latitude;
       private final BigDecimal longitude;
       // Validation logic duplicated
   }

   // geospatial/domain/valueobjects/Coordinates.java
   public class Coordinates {
       private final BigDecimal latitude;
       private final BigDecimal longitude;
       // Same validation logic duplicated
   }
   ```

4. **Incompatible Validations**
   ```java
   // What if validations differ?
   TransportCoordinates: allows 7 decimal places
   Coordinates: allows 6 decimal places

   // Which one is correct? 🤔
   // This leads to bugs!
   ```

---

## Comparison: Alternative Architectures

### Option 1: Current (Shared Kernel) ✅ BEST

```java
// transport/domain/valueobject/VehiclePosition.java
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;

public class VehiclePosition {
    private final Coordinates coordinates; // ✅ Shared
}
```

**Pros:**
- ✅ Single source of truth
- ✅ No code duplication
- ✅ Consistent validations
- ✅ DRY principle
- ✅ Clear ubiquitous language

**Cons:**
- ⚠️ transport depends on geospatial (but this is acceptable for Shared Kernel)

**Verdict:** ✅ **CORRECT** - This is the right approach

---

### Option 2: Move to shared Module ⚠️ ALTERNATIVE

```java
// Move Coordinates to shared module
// shared/domain/valueobjects/Coordinates.java

// transport/domain/valueobject/VehiclePosition.java
import biz.ugur.busroutebackend.shared.domain.valueobjects.Coordinates;

public class VehiclePosition {
    private final Coordinates coordinates;
}
```

**Pros:**
- ✅ More obvious that it's shared
- ✅ No "geospatial" in the import path

**Cons:**
- ⚠️ Breaks geospatial cohesion (Coordinates separated from Distance, DistanceCalculationService)
- ⚠️ Shared module becomes dumping ground

**Verdict:** ⚠️ **POSSIBLE but NOT BETTER** - Current structure is more cohesive

---

### Option 3: Duplicate in Each Context ❌ WRONG

```java
// transport/domain/valueobject/TransportCoordinates.java
public class TransportCoordinates {
    private final BigDecimal latitude;
    private final BigDecimal longitude;
}

// geospatial/domain/valueobjects/Coordinates.java
public class Coordinates {
    private final BigDecimal latitude;
    private final BigDecimal longitude;
}
```

**Pros:**
- ✅ Complete independence (no coupling)

**Cons:**
- ❌ Violates DRY
- ❌ Duplicate validation logic
- ❌ Inconsistent behavior between contexts
- ❌ More code to maintain
- ❌ Conversion overhead

**Verdict:** ❌ **WRONG** - This is an anti-pattern

---

### Option 4: ACL (Anti-Corruption Layer) ❌ OVER-ENGINEERING

```java
// transport/domain/valueobject/VehiclePosition.java
public class VehiclePosition {
    private final TransportCoordinates coordinates; // Own type
}

// transport/infrastructure/mapper/CoordinatesACL.java
@Component
public class CoordinatesACL {
    public TransportCoordinates fromGeospatial(Coordinates coords) {
        return new TransportCoordinates(
            coords.getLatitudeAsDouble(),
            coords.getLongitudeAsDouble()
        );
    }
}
```

**Pros:**
- ✅ Complete isolation between contexts

**Cons:**
- ❌ Over-engineering for shared concepts
- ❌ Adds complexity without benefit
- ❌ Performance overhead (conversions)
- ❌ More code to maintain

**Verdict:** ❌ **OVER-ENGINEERING** - Unnecessary for Shared Kernel

---

## DDD Patterns: When to Use What

### Shared Kernel ✅ (Current Approach)

**Use When:**
- Concept is truly universal (coordinates, time, money)
- Multiple contexts need EXACT same behavior
- Duplication would lead to inconsistency
- Changes are rare and affect all contexts equally

**Examples in This Project:**
- ✅ `Coordinates` - universal geospatial concept
- ✅ `Distance` - universal measurement concept
- ✅ `TurkmenistanBounds` - shared business rule
- ✅ `DistanceCalculationService` - shared calculation

**Rule:** If it's in math/physics/geography textbook, it's probably Shared Kernel.

---

### Separate Bounded Context with ACL ⚠️

**Use When:**
- Contexts have different business rules
- Concepts have context-specific meaning
- Independence more important than consistency
- Integration with external systems

**Example (NOT in this project):**
```java
// Billing context has different "Customer" than CRM context
// billing/domain/Customer (billing rules)
// crm/domain/Customer (relationship rules)
//
// ACL converts between them
```

**Rule:** If concept means different things in different contexts, use ACL.

---

### Duplication (Context-Specific Implementations) ❌

**Use When:**
- Concepts are similar but fundamentally different
- Each context evolves independently
- No shared behavior needed

**Example:**
```java
// UserProfile vs AdminProfile
// Similar structure, but different business rules
// Each context owns its own model
```

**Rule:** Only duplicate if concepts are **coincidentally similar** but **semantically different**.

---

## Analysis of Current Code

### Evidence that geospatial is Shared Kernel

#### 1. Used by Multiple Contexts

```java
// transport context uses geospatial
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;

// Examples:
- Vehicle.java: uses Coordinates for currentPosition
- BusStop.java: uses Coordinates for location
- BusRoute.java: uses Coordinates in RouteGeometry
- VehiclePosition.java: uses Coordinates
```

#### 2. Generic, Not Domain-Specific

```java
// geospatial/domain/valueobjects/Coordinates.java
// ✅ Generic: No transport-specific logic
public class Coordinates extends ValueObject {
    private final BigDecimal latitude;
    private final BigDecimal longitude;

    // Generic methods:
    public String toWKT() { ... }              // Standard WKT format
    public double[] toGeoJSON() { ... }        // Standard GeoJSON
    public double bearingTo(Coordinates) { ... } // Standard geography
}

// ❌ Would be domain-specific if it had:
// public boolean isValidBusStopLocation() { ... }
// public VehicleStatus checkVehicleZone() { ... }
```

#### 3. Mathematical/Scientific Foundation

```java
// geospatial/domain/services/DistanceCalculationService.java
public class DistanceCalculationService {
    // ✅ Based on Haversine formula (universal)
    // ✅ Uses Earth radius constant (universal)
    // ✅ WGS84 coordinate system (universal standard)

    private double haversineDistance(...) {
        // Mathematical formula, same for everyone
    }
}
```

#### 4. Infrastructure Support

```java
// geospatial/infrastructure/postgis/PostGISQueryBuilder.java
// ✅ Provides PostGIS integration for ALL contexts
// Not specific to transport
public static String geographyDistanceInMeters(...) {
    // Standard PostGIS ST_Distance usage
}
```

---

## Recommendations

### ✅ Keep Current Architecture (Shared Kernel)

**Recommendation:** **No changes needed**

**Rationale:**
1. `geospatial` correctly acts as Shared Kernel
2. All coordinate/distance operations centralized
3. No duplication, consistent behavior
4. Clear separation: domain concepts (geospatial) vs business logic (transport)

### 📝 Document the Architecture

Add documentation to clarify geospatial's role:

```java
// geospatial/package-info.java
/**
 * Geospatial Shared Kernel
 *
 * This module provides shared geospatial concepts used across all bounded contexts.
 * It acts as a Shared Kernel in DDD terms.
 *
 * Core Concepts:
 * - {@link Coordinates} - Universal coordinate representation
 * - {@link Distance} - Universal distance measurement
 * - {@link DistanceCalculationService} - Standard distance calculations
 * - {@link TurkmenistanBounds} - Shared business rules for regional validation
 *
 * Usage:
 * All bounded contexts (transport, routing, admin) should use these shared
 * geospatial concepts rather than creating their own implementations.
 *
 * Architecture Pattern: Shared Kernel
 *
 * @see <a href="https://martinfowler.com/bliki/BoundedContext.html">Bounded Context</a>
 * @see <a href="https://www.domainlanguage.com/ddd/reference/">DDD Reference</a>
 */
package biz.ugur.busroutebackend.geospatial;
```

### 📊 Update Architecture Diagram

```
Project Architecture (Modular Monolith with DDD)

┌─────────────────────────────────────────────────────────────┐
│                   Application Layer                          │
└─────────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
┌───────▼────────┐  ┌──────▼──────┐  ┌────────▼────────┐
│   transport    │  │   routing    │  │     admin       │
│  (BC: 운송)    │  │ (BC: 경로)    │  │  (BC: 관리)     │
└───────┬────────┘  └──────┬──────┘  └────────┬────────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            │
                    ┌───────▼───────┐
                    │  geospatial   │
                    │ (Shared Kernel)│ ← ALL contexts use this
                    └───────┬───────┘
                            │
                    ┌───────▼───────┐
                    │    shared     │
                    │ (Core Kernel)  │ ← Base classes
                    └───────────────┘
```

---

## Conclusion

### ✅ Current Architecture is CORRECT

The dependency `VehiclePosition` → `Coordinates` is **architecturally sound** because:

1. **geospatial is a Shared Kernel**, not a separate bounded context
2. **Coordinates is ubiquitous language** across the entire system
3. **No duplication** - single source of truth
4. **Consistent behavior** - all contexts use same validation/conversions
5. **Follows DDD patterns** - appropriate use of Shared Kernel

### 📚 Key Takeaways

**Shared Kernel Pattern:**
- ✅ Use for universal concepts (coordinates, time, money)
- ✅ Centralize in dedicated module
- ✅ All contexts depend on it (acceptable coupling)

**When NOT to Share:**
- ❌ Don't share if concept means different things in different contexts
- ❌ Don't share if contexts need independent evolution
- ❌ Don't share if coupling creates problems

**For This Project:**
- ✅ geospatial = Shared Kernel (correct)
- ✅ transport → geospatial = acceptable dependency
- ✅ No changes needed

---

## References

### DDD Patterns
- **Shared Kernel** - Eric Evans, Domain-Driven Design
- **Bounded Context** - Martin Fowler
- **Anti-Corruption Layer** - Eric Evans

### Similar Examples in Industry

**Spring Framework:**
```java
// Everyone depends on spring-core (Shared Kernel)
import org.springframework.util.Assert;
import org.springframework.core.io.Resource;
```

**Java Standard Library:**
```java
// Everyone depends on java.time (Shared Kernel)
import java.time.LocalDateTime;
import java.time.Duration;
```

**Our Project:**
```java
// Everyone depends on geospatial (Shared Kernel) ✅
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
```

**This is the SAME pattern** - and it's correct! ✅

---

**Author:** Claude Code
**Review Status:** Architecture Analysis Complete
**Recommendation:** ✅ Keep current design (no changes needed)
