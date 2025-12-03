# Transport Refactoring: Code Examples (Before → After)

**Date:** 2025-10-30
**Reference:** Banner Bounded Context (эталонная реализация)

Этот документ показывает конкретные примеры кода из transport BC "до" и "после" рефакторинга, с детальными объяснениями каждого изменения.

---

## 📋 Содержание

1. [Проблема #1: Мутабельный Aggregate Root (BusRoute)](#problem-1)
2. [Проблема #2: ServiceLocator Anti-pattern (RouteGeometry)](#problem-2)
3. [Проблема #3: Domain Events без версионирования](#problem-3)
4. [Проблема #4: Cross-BC Dependency + SRP Violation](#problem-4)
5. [Проблема #5: Use Case без Factory Pattern](#problem-5)
6. [Проблема #6: Aggregate Boundary Violation](#problem-6)

---

<a name="problem-1"></a>
## 🔴 Проблема #1: Мутабельный Aggregate Root (BusRoute)

### ❌ ТЕКУЩИЙ КОД (Проблемный)

**Файл:** `transport/domain/model/BusRoute.java` (435 строк)

```java
@Getter
@Table("bus_routes")
@Builder
public class BusRoute extends AggregateRoot<BusRoute, BusRouteId> {

    @Id
    @Column("id")
    private BusRouteId id;  // ❌ НЕ final - можно изменить!

    @Column("route_number")
    private String routeNumber;  // ❌ НЕ final

    @Column("route_name")
    private String routeName;  // ❌ НЕ final

    @Column("route_color")
    private String routeColor;  // ❌ НЕ final

    @Column("route_geometry_forward")
    private String routeGeometryForward;  // ❌ НЕ final

    @Setter  // ❌❌❌ SETTER для distance - прямое нарушение инкапсуляции!
    @Column("total_distance_forward_meters")
    private Integer totalDistanceForwardMeters;

    @Transient
    private List<BusStop> busStops = new ArrayList<>();  // ❌❌❌ Нарушение границ агрегата!

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    // ❌ Мутирующий метод - изменяет состояние объекта
    public void updateRouteGeometry(RouteGeometry forwardGeometry,
                                     RouteGeometry backwardGeometry) {
        boolean hasChanges = false;

        if (forwardGeometry != null) {
            String forwardWKT = forwardGeometry.toWKT();
            int forwardDistance = (int) Math.round(forwardGeometry.calculateDistanceMeters());

            // ❌ Прямое изменение полей
            this.routeGeometryForward = forwardWKT;
            this.totalDistanceForwardMeters = forwardDistance;
            hasChanges = true;
        }

        if (backwardGeometry != null) {
            // ❌ Прямое изменение полей
            this.routeGeometryBackward = backwardWKT;
            this.totalDistanceBackwardMeters = backwardDistance;
            hasChanges = true;
        }

        if (hasChanges) {
            registerEvent(new RouteGeometryUpdatedEvent(...));
        }
    }

    // ❌ Мутирующий метод
    public void updateBasicInfo(
            String routeNumber,
            String routeName,
            String nameTm,
            String nameEn,
            String routeColor,
            Integer estimatedDurationMinutes,
            String cityId) {
        // ❌ Прямое изменение полей без валидации
        this.routeName = routeName;
        this.nameTm = nameTm;
        this.nameEn = nameEn;
        this.routeColor = routeColor;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.cityId = cityId;
        this.updatedAt = LocalDateTime.now();
        // ❌ НЕТ события!
    }

    // ❌ Методы работы с busStops - нарушение границ агрегата
    public boolean connectsStops(String fromStopId, String toStopId) {
        if (busStops.isEmpty()) {
            // ❌ Заглушка для совместимости
            return true;
        }

        boolean hasFromStop = busStops.stream()
                .anyMatch(stop -> stop.getId().getValue().equals(fromStopId));
        boolean hasToStop = busStops.stream()
                .anyMatch(stop -> stop.getId().getValue().equals(toStopId));

        return hasFromStop && hasToStop;
    }

    // ❌ Множественные методы для работы с geometry
    public void clearGeometry() {
        this.routeGeometryForward = null;
        this.routeGeometryBackward = null;
        this.totalDistanceForwardMeters = null;
        this.totalDistanceBackwardMeters = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void clearForwardGeometry() { ... }
    public void clearBackwardGeometry() { ... }
    public void updateForwardGeometry(...) { ... }
    public void updateBackwardGeometry(...) { ... }

    // ❌ Валидация скрыта в private методах
    private String validateRouteNumber(String routeNumber) {
        if (routeNumber == null || routeNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Route number cannot be null or empty");
        }
        String number = routeNumber.trim().toUpperCase();
        if (!number.matches("\\d{1,3}[A-Z]?")) {
            throw new IllegalArgumentException("Invalid route number format");
        }
        return number;
    }
}
```

### ✅ РЕФАКТОРИНГ (Решение)

**Файл:** `transport/domain/model/BusRoute.java` (после рефакторинга)

```java
@Getter
@Builder(toBuilder = true)  // ✅ toBuilder для immutable updates
@Table("bus_routes")
public final class BusRoute extends AggregateRoot<BusRoute, BusRouteId> {  // ✅ final class

    // ✅ ВСЕ ПОЛЯ FINAL - невозможно изменить после создания
    @Id
    @Column("id")
    private final BusRouteId id;

    @Column("route_number")
    private final String routeNumber;

    @Column("route_name")
    private final String routeName;

    @Column("name_tm")
    private final String nameTm;

    @Column("name_en")
    private final String nameEn;

    @Column("route_color")
    private final String routeColor;

    @Column("is_active")
    private final Boolean isActive;

    @Column("city_id")
    private final String cityId;

    @Column("estimated_duration_minutes")
    private final Integer estimatedDurationMinutes;

    @Column("route_geometry_forward")
    private final String routeGeometryForward;

    @Column("route_geometry_backward")
    private final String routeGeometryBackward;

    @Column("total_distance_forward_meters")
    private final Integer totalDistanceForwardMeters;  // ✅ Больше нет @Setter!

    @Column("total_distance_backward_meters")
    private final Integer totalDistanceBackwardMeters;

    // ✅ УБРАЛИ @Transient List<BusStop> busStops - нарушение границ агрегата!

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    // ✅ Factory method для создания нового агрегата
    public static BusRoute create(
            String routeNumber,
            String routeName,
            String nameTm,
            String nameEn,
            String routeColor,
            String cityId,
            Integer estimatedDurationMinutes) {

        // ✅ Валидация перед созданием
        validateRouteNumber(routeNumber);
        validateRouteName(routeName);
        String normalizedColor = validateRouteColor(routeColor);

        BusRoute route = BusRoute.builder()
                .id(BusRouteId.generate())
                .routeNumber(routeNumber.trim().toUpperCase())
                .routeName(routeName.trim())
                .nameTm(nameTm != null ? nameTm.trim() : routeName.trim())
                .nameEn(nameEn != null ? nameEn.trim() : routeName.trim())
                .routeColor(normalizedColor)
                .cityId(cityId)
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .isActive(true)
                .build();

        // ✅ Регистрируем domain event
        route.registerEvent(new RouteCreatedEvent(
                route.id.getValue(),
                route.routeNumber,
                route.routeName,
                route.routeColor,
                route.cityId
        ));

        return route;
    }

    // ✅ Immutable update - возвращает НОВЫЙ экземпляр
    public BusRoute updateGeometry(
            RouteGeometry forwardGeometry,
            RouteGeometry backwardGeometry) {

        // ✅ Валидация
        if (forwardGeometry == null && backwardGeometry == null) {
            throw new IllegalArgumentException(
                "At least one geometry (forward or backward) must be provided"
            );
        }

        // ✅ Подготовка данных
        String forwardWKT = forwardGeometry != null ? forwardGeometry.toWKT() : this.routeGeometryForward;
        Integer forwardDistance = forwardGeometry != null
            ? (int) Math.round(forwardGeometry.calculateDistanceMeters())
            : this.totalDistanceForwardMeters;

        String backwardWKT = backwardGeometry != null ? backwardGeometry.toWKT() : this.routeGeometryBackward;
        Integer backwardDistance = backwardGeometry != null
            ? (int) Math.round(backwardGeometry.calculateDistanceMeters())
            : this.totalDistanceBackwardMeters;

        // ✅ Создаем новый экземпляр с помощью toBuilder()
        BusRoute updatedRoute = this.toBuilder()
                .routeGeometryForward(forwardWKT)
                .totalDistanceForwardMeters(forwardDistance)
                .routeGeometryBackward(backwardWKT)
                .totalDistanceBackwardMeters(backwardDistance)
                .build();

        // ✅ Регистрируем событие на НОВОМ экземпляре
        updatedRoute.registerEvent(new RouteGeometryUpdatedEvent(
                this.id.getValue(),
                this.routeNumber,
                this.routeName,
                forwardGeometry != null ? forwardGeometry.getPointCount() : 0,
                backwardGeometry != null ? backwardGeometry.getPointCount() : 0,
                forwardDistance,
                backwardDistance
        ));

        return updatedRoute;  // ✅ Возвращаем НОВЫЙ экземпляр
    }

    // ✅ Immutable update - возвращает НОВЫЙ экземпляр
    public BusRoute updateBasicInfo(
            String routeName,
            String nameTm,
            String nameEn,
            String routeColor,
            Integer estimatedDurationMinutes,
            String cityId) {

        // ✅ Валидация
        validateRouteName(routeName);
        String normalizedColor = validateRouteColor(routeColor);

        // ✅ Проверка - были ли изменения?
        if (this.routeName.equals(routeName.trim()) &&
            this.nameTm.equals(nameTm != null ? nameTm.trim() : routeName.trim()) &&
            this.nameEn.equals(nameEn != null ? nameEn.trim() : routeName.trim()) &&
            this.routeColor.equals(normalizedColor) &&
            this.estimatedDurationMinutes.equals(estimatedDurationMinutes) &&
            this.cityId.equals(cityId)) {
            // ✅ Нет изменений - возвращаем тот же экземпляр
            return this;
        }

        // ✅ Создаем новый экземпляр
        BusRoute updatedRoute = this.toBuilder()
                .routeName(routeName.trim())
                .nameTm(nameTm != null ? nameTm.trim() : routeName.trim())
                .nameEn(nameEn != null ? nameEn.trim() : routeName.trim())
                .routeColor(normalizedColor)
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .cityId(cityId)
                .build();

        // ✅ Регистрируем событие
        updatedRoute.registerEvent(new RouteBasicInfoUpdatedEvent(
                this.id.getValue(),
                this.routeNumber,
                routeName,
                nameTm,
                nameEn,
                normalizedColor,
                estimatedDurationMinutes,
                cityId
        ));

        return updatedRoute;
    }

    // ✅ Immutable deactivation
    public BusRoute deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;  // ✅ Уже неактивен - возвращаем this
        }

        BusRoute deactivatedRoute = this.toBuilder()
                .isActive(false)
                .build();

        deactivatedRoute.registerEvent(new RouteDeactivatedEvent(
                this.id.getValue(),
                this.routeNumber
        ));

        return deactivatedRoute;
    }

    // ✅ Immutable activation
    public BusRoute activate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            return this;  // ✅ Уже активен - возвращаем this
        }

        BusRoute activatedRoute = this.toBuilder()
                .isActive(true)
                .build();

        activatedRoute.registerEvent(new RouteActivatedEvent(
                this.id.getValue(),
                this.routeNumber
        ));

        return activatedRoute;
    }

    // ✅ Query methods (не изменяют состояние)
    public RouteGeometry getForwardGeometry() {
        if (routeGeometryForward == null || routeGeometryForward.trim().isEmpty()) {
            return null;
        }
        try {
            return RouteGeometry.fromWKT(routeGeometryForward);
        } catch (Exception e) {
            log.warn("Failed to parse forward geometry for route {}: {}",
                routeNumber, e.getMessage());
            return null;
        }
    }

    public RouteGeometry getBackwardGeometry() {
        if (routeGeometryBackward == null || routeGeometryBackward.trim().isEmpty()) {
            return null;
        }
        try {
            return RouteGeometry.fromWKT(routeGeometryBackward);
        } catch (Exception e) {
            log.warn("Failed to parse backward geometry for route {}: {}",
                routeNumber, e.getMessage());
            return null;
        }
    }

    public boolean hasGeometry() {
        return hasForwardGeometry() || hasBackwardGeometry();
    }

    public boolean hasForwardGeometry() {
        return routeGeometryForward != null && !routeGeometryForward.trim().isEmpty();
    }

    public boolean hasBackwardGeometry() {
        return routeGeometryBackward != null && !routeGeometryBackward.trim().isEmpty();
    }

    public boolean hasCompleteGeometry() {
        return hasForwardGeometry() && hasBackwardGeometry();
    }

    // ✅ Публичная валидация (можно использовать вне класса)
    public static void validateRouteNumber(String routeNumber) {
        if (routeNumber == null || routeNumber.trim().isEmpty()) {
            throw new RouteValidationException(
                "Route number cannot be null or empty"
            );
        }
        String number = routeNumber.trim().toUpperCase();
        if (!number.matches("\\d{1,3}[A-Z]?")) {
            throw new RouteValidationException(
                "Invalid route number format. Expected: '29' or '7A', got: " + number
            );
        }
    }

    public static void validateRouteName(String routeName) {
        if (routeName == null || routeName.trim().isEmpty()) {
            throw new RouteValidationException(
                "Route name cannot be null or empty"
            );
        }
        if (routeName.trim().length() > 100) {
            throw new RouteValidationException(
                "Route name cannot exceed 100 characters"
            );
        }
    }

    public static String validateRouteColor(String routeColor) {
        if (routeColor == null || !routeColor.matches("^#[0-9A-Fa-f]{6}$")) {
            return "#1976D2";  // Default Material Blue
        }
        return routeColor.toUpperCase();
    }

    @Override
    public BusRouteId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
}
```

### 📊 Сравнение: До vs После

| Аспект | До (❌) | После (✅) | Улучшение |
|--------|---------|------------|-----------|
| **Иммутабельность** | Мутабельные поля | Все поля `final` | Thread-safe, predictable |
| **Aggregate Boundary** | Содержит `List<BusStop>` | Убрано | Правильная граница |
| **Валидация** | Private методы | Public static методы | Переиспользуемость |
| **Events** | Только при геометрии | При всех изменениях | Полная трассировка |
| **Factory Method** | Нет | `create()` | Понятное создание |
| **Методов изменения** | 9 методов (clear, update, etc.) | 3 метода (update, activate, deactivate) | Упрощение API |
| **Setters** | `@Setter` на distance | Нет setters | Инкапсуляция |
| **Строк кода** | 435 | ~320 | -26% |

### 💡 Ключевые Изменения

1. **Immutability Pattern:**
   ```java
   // ❌ БЫЛО: Мутация
   public void updateBasicInfo(...) {
       this.routeName = routeName;
       this.routeColor = routeColor;
   }

   // ✅ СТАЛО: Новый экземпляр
   public BusRoute updateBasicInfo(...) {
       BusRoute updated = this.toBuilder()
           .routeName(routeName)
           .routeColor(routeColor)
           .build();
       return updated;
   }
   ```

2. **Factory Pattern:**
   ```java
   // ❌ БЫЛО: Прямое использование Builder
   BusRoute route = BusRoute.builder()
       .routeNumber(number)
       .routeName(name)
       .build();

   // ✅ СТАЛО: Factory method с валидацией
   BusRoute route = BusRoute.create(number, name, ...);
   ```

3. **Aggregate Boundary:**
   ```java
   // ❌ БЫЛО: Транзитный список BusStop
   @Transient
   private List<BusStop> busStops;

   public boolean connectsStops(String from, String to) {
       return busStops.stream()...  // Нарушение границы!
   }

   // ✅ СТАЛО: Запросы через репозиторий
   // BusRoute больше не знает о BusStop напрямую
   // RouteStopRepository отвечает за связи
   ```

4. **Event Registration:**
   ```java
   // ❌ БЫЛО: События только при геометрии
   public void updateBasicInfo(...) {
       this.routeName = routeName;
       // НЕТ события!
   }

   // ✅ СТАЛО: События при всех изменениях
   public BusRoute updateBasicInfo(...) {
       BusRoute updated = this.toBuilder()...build();
       updated.registerEvent(new RouteBasicInfoUpdatedEvent(...));
       return updated;
   }
   ```

---

<a name="problem-2"></a>
## 🔴 Проблема #2: ServiceLocator Anti-pattern (RouteGeometry)

### ❌ ТЕКУЩИЙ КОД (Проблемный)

**Файл:** `transport/domain/valueobject/RouteGeometry.java`

```java
@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteGeometry extends ValueObject {

    private final List<Coordinates> points;

    // ❌❌❌ STATIC SERVICE - ServiceLocator anti-pattern!
    private static DistanceCalculationService distanceService;

    public RouteGeometry(List<Coordinates> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Route geometry must contain at least one point");
        }
        if (points.size() < 2) {
            throw new IllegalArgumentException("Route geometry must contain at least 2 points");
        }
        this.points = List.copyOf(points);
    }

    /**
     * ❌ Set the distance calculation service (for dependency injection).
     */
    public static void setDistanceCalculationService(DistanceCalculationService service) {
        distanceService = service;
    }

    /**
     * ❌ Calculate total distance - использует static service
     */
    public double calculateDistanceMeters() {
        if (points.size() < 2) {
            return 0.0;
        }

        // ❌ Проверка на null static service
        if (distanceService == null) {
            throw new IllegalStateException(
                "DistanceCalculationService not initialized. " +
                "Ensure GeospatialServiceInitializer has been executed during application startup."
            );
        }

        double totalDistance = 0.0;

        // ❌ Использование static service
        for (int i = 1; i < points.size(); i++) {
            Coordinates prev = points.get(i - 1);
            Coordinates curr = points.get(i);
            totalDistance += distanceService.calculateDistance(
                prev.getLatitudeAsDouble(), prev.getLongitudeAsDouble(),
                curr.getLatitudeAsDouble(), curr.getLongitudeAsDouble()
            ).getMeters();
        }

        return totalDistance;
    }

    /**
     * ❌ Check if route contains point - тоже использует static service
     */
    public boolean containsPoint(Coordinates point, double toleranceMeters) {
        if (distanceService == null) {
            throw new IllegalStateException(
                "DistanceCalculationService not initialized."
            );
        }

        return points.stream()
                .anyMatch(p -> distanceService.calculateDistance(
                    p.getLatitudeAsDouble(), p.getLongitudeAsDouble(),
                    point.getLatitudeAsDouble(), point.getLongitudeAsDouble()
                ).getMeters() <= toleranceMeters);
    }

    // ❌ toString() тоже зависит от static service
    @Override
    public String toString() {
        return String.format("RouteGeometry{points=%d, distance=%.1fm}",
                points.size(), calculateDistanceMeters());  // Может упасть!
    }
}
```

**Проблемы:**
1. ❌ **ServiceLocator anti-pattern** - static service injection
2. ❌ **Tight coupling** - Value Object зависит от infrastructure service
3. ❌ **Testability** - сложно тестировать без инициализации static service
4. ❌ **Hidden dependency** - неочевидно, что нужен service
5. ❌ **NPE risk** - может упасть, если service не инициализирован
6. ❌ **Concurrent issues** - static state может вызвать проблемы в тестах

### ✅ РЕФАКТОРИНГ (Решение)

**Шаг 1: Чистый Value Object**

**Файл:** `transport/domain/valueobject/RouteGeometry.java` (после рефакторинга)

```java
@Value  // ✅ Lombok @Value - immutable VO
public class RouteGeometry {

    List<Coordinates> points;

    // ✅ Приватный конструктор - только через factory methods
    private RouteGeometry(List<Coordinates> points) {
        this.points = List.copyOf(points);
    }

    // ✅ Factory method с валидацией
    public static RouteGeometry of(List<Coordinates> points) {
        validatePoints(points);
        return new RouteGeometry(points);
    }

    // ✅ Factory method из WKT
    public static RouteGeometry fromWKT(String wkt) {
        if (wkt == null || wkt.trim().isEmpty()) {
            throw new IllegalArgumentException("WKT string cannot be empty");
        }

        if (!wkt.startsWith("LINESTRING(") || !wkt.endsWith(")")) {
            throw new IllegalArgumentException("Invalid WKT format. Expected LINESTRING(...)");
        }

        String coordinatesStr = wkt.substring(11, wkt.length() - 1);

        if (coordinatesStr.trim().isEmpty()) {
            throw new IllegalArgumentException("WKT contains no coordinates");
        }

        String[] pointStrings = coordinatesStr.split(",");
        List<Coordinates> points = Arrays.stream(pointStrings)
                .map(String::trim)
                .map(RouteGeometry::parseWKTPoint)
                .toList();

        return new RouteGeometry(points);
    }

    // ✅ Factory method из координат
    public static RouteGeometry fromCoordinates(List<List<Double>> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }

        List<Coordinates> points = coordinates.stream()
                .map(coord -> {
                    if (coord.size() != 2) {
                        throw new IllegalArgumentException(
                            "Each coordinate must have exactly 2 values [longitude, latitude]"
                        );
                    }
                    // GeoJSON format: [longitude, latitude]
                    return Coordinates.of(coord.get(1), coord.get(0));
                })
                .toList();

        return RouteGeometry.of(points);
    }

    // ✅ Конвертация в WKT (чистая функция)
    public String toWKT() {
        String pointsWKT = points.stream()
                .map(point -> String.format("%.6f %.6f",
                    point.getLongitudeAsDouble(), point.getLatitudeAsDouble()))
                .collect(Collectors.joining(", "));

        return String.format("LINESTRING(%s)", pointsWKT);
    }

    // ✅ Конвертация в координаты (чистая функция)
    public List<List<Double>> toCoordinates() {
        return points.stream()
                .map(point -> List.of(
                    point.getLongitudeAsDouble(),
                    point.getLatitudeAsDouble()
                ))
                .toList();
    }

    // ✅ Query methods (без dependencies)
    public int getPointCount() {
        return points.size();
    }

    public boolean isValid() {
        return points.size() >= 2;
    }

    // ✅ Reverse (чистая функция)
    public RouteGeometry reverse() {
        List<Coordinates> reversedPoints = points.reversed();
        return new RouteGeometry(reversedPoints);
    }

    // ✅ Get bounds (чистая функция)
    public RouteBounds getBounds() {
        double minLat = points.stream()
            .mapToDouble(Coordinates::getLatitudeAsDouble)
            .min()
            .orElse(0);
        double maxLat = points.stream()
            .mapToDouble(Coordinates::getLatitudeAsDouble)
            .max()
            .orElse(0);
        double minLon = points.stream()
            .mapToDouble(Coordinates::getLongitudeAsDouble)
            .min()
            .orElse(0);
        double maxLon = points.stream()
            .mapToDouble(Coordinates::getLongitudeAsDouble)
            .max()
            .orElse(0);

        return new RouteBounds(
            Coordinates.of(minLat, minLon),
            Coordinates.of(maxLat, maxLon)
        );
    }

    // ✅ Валидация (статический метод)
    private static void validatePoints(List<Coordinates> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException(
                "Route geometry must contain at least one point"
            );
        }
        if (points.size() < 2) {
            throw new IllegalArgumentException(
                "Route geometry must contain at least 2 points for a valid LineString"
            );
        }
    }

    private static Coordinates parseWKTPoint(String pointStr) {
        String[] coords = pointStr.split("\\s+");
        if (coords.length != 2) {
            throw new IllegalArgumentException("Invalid point format in WKT: " + pointStr);
        }

        try {
            double longitude = Double.parseDouble(coords[0]);
            double latitude = Double.parseDouble(coords[1]);
            return Coordinates.of(latitude, longitude);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinate values in WKT: " + pointStr, e);
        }
    }

    @Override
    public String toString() {
        return String.format("RouteGeometry{points=%d}", points.size());
    }
}
```

**Шаг 2: Domain Service для операций**

**Файл:** `transport/domain/services/RouteGeometryService.java` (НОВЫЙ)

```java
package biz.ugur.busroutebackend.transport.domain.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Distance;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * ✅ Domain Service для работы с геометрией маршрутов.
 *
 * Отвечает за:
 * - Вычисление расстояний
 * - Проверку вхождения точек
 * - Геометрические операции
 */
@Component
public class RouteGeometryService {

    private final DistanceCalculationService distanceService;

    // ✅ Dependency injection через конструктор
    public RouteGeometryService(DistanceCalculationService distanceService) {
        this.distanceService = distanceService;
    }

    /**
     * ✅ Вычислить общую длину маршрута в метрах
     */
    public double calculateDistanceMeters(RouteGeometry geometry) {
        if (geometry == null || geometry.getPointCount() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        List<Coordinates> points = geometry.getPoints();

        for (int i = 1; i < points.size(); i++) {
            Coordinates prev = points.get(i - 1);
            Coordinates curr = points.get(i);

            Distance distance = distanceService.calculateDistance(
                prev.getLatitudeAsDouble(), prev.getLongitudeAsDouble(),
                curr.getLatitudeAsDouble(), curr.getLongitudeAsDouble()
            );

            totalDistance += distance.getMeters();
        }

        return totalDistance;
    }

    /**
     * ✅ Вычислить общую длину маршрута (реактивная версия)
     */
    public Mono<Double> calculateDistanceMetersReactive(RouteGeometry geometry) {
        return Mono.fromCallable(() -> calculateDistanceMeters(geometry));
    }

    /**
     * ✅ Проверить, содержит ли маршрут точку в пределах заданной толерантности
     */
    public boolean containsPoint(RouteGeometry geometry, Coordinates point, double toleranceMeters) {
        if (geometry == null || point == null) {
            return false;
        }

        return geometry.getPoints().stream()
                .anyMatch(p -> {
                    Distance distance = distanceService.calculateDistance(
                        p.getLatitudeAsDouble(), p.getLongitudeAsDouble(),
                        point.getLatitudeAsDouble(), point.getLongitudeAsDouble()
                    );
                    return distance.getMeters() <= toleranceMeters;
                });
    }

    /**
     * ✅ Найти ближайшую точку на маршруте к заданной точке
     */
    public Coordinates findClosestPoint(RouteGeometry geometry, Coordinates targetPoint) {
        if (geometry == null || targetPoint == null || geometry.getPointCount() == 0) {
            return null;
        }

        return geometry.getPoints().stream()
                .min((p1, p2) -> {
                    double dist1 = distanceService.calculateDistance(
                        p1.getLatitudeAsDouble(), p1.getLongitudeAsDouble(),
                        targetPoint.getLatitudeAsDouble(), targetPoint.getLongitudeAsDouble()
                    ).getMeters();

                    double dist2 = distanceService.calculateDistance(
                        p2.getLatitudeAsDouble(), p2.getLongitudeAsDouble(),
                        targetPoint.getLatitudeAsDouble(), targetPoint.getLongitudeAsDouble()
                    ).getMeters();

                    return Double.compare(dist1, dist2);
                })
                .orElse(null);
    }

    /**
     * ✅ Вычислить расстояние от начала маршрута до заданной точки
     */
    public double calculateDistanceFromStart(RouteGeometry geometry, Coordinates targetPoint) {
        if (geometry == null || targetPoint == null) {
            return 0.0;
        }

        List<Coordinates> points = geometry.getPoints();
        double accumulatedDistance = 0.0;

        // Находим ближайший сегмент
        int closestSegmentIndex = findClosestSegmentIndex(geometry, targetPoint);

        // Суммируем расстояния до ближайшего сегмента
        for (int i = 1; i <= closestSegmentIndex && i < points.size(); i++) {
            Coordinates prev = points.get(i - 1);
            Coordinates curr = points.get(i);

            Distance distance = distanceService.calculateDistance(
                prev.getLatitudeAsDouble(), prev.getLongitudeAsDouble(),
                curr.getLatitudeAsDouble(), curr.getLongitudeAsDouble()
            );

            accumulatedDistance += distance.getMeters();
        }

        return accumulatedDistance;
    }

    /**
     * ✅ Найти индекс ближайшего сегмента маршрута к заданной точке
     */
    private int findClosestSegmentIndex(RouteGeometry geometry, Coordinates targetPoint) {
        List<Coordinates> points = geometry.getPoints();
        int closestIndex = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            Distance distance = distanceService.calculateDistance(
                points.get(i).getLatitudeAsDouble(),
                points.get(i).getLongitudeAsDouble(),
                targetPoint.getLatitudeAsDouble(),
                targetPoint.getLongitudeAsDouble()
            );

            if (distance.getMeters() < minDistance) {
                minDistance = distance.getMeters();
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    /**
     * ✅ Проверить, пересекаются ли два маршрута
     */
    public boolean intersects(RouteGeometry geometry1, RouteGeometry geometry2, double toleranceMeters) {
        if (geometry1 == null || geometry2 == null) {
            return false;
        }

        // Простая проверка - есть ли точки в пределах толерантности
        for (Coordinates point1 : geometry1.getPoints()) {
            for (Coordinates point2 : geometry2.getPoints()) {
                Distance distance = distanceService.calculateDistance(
                    point1.getLatitudeAsDouble(), point1.getLongitudeAsDouble(),
                    point2.getLatitudeAsDouble(), point2.getLongitudeAsDouble()
                );

                if (distance.getMeters() <= toleranceMeters) {
                    return true;
                }
            }
        }

        return false;
    }
}
```

### 📊 Сравнение: До vs После

| Аспект | До (❌) | После (✅) |
|--------|---------|------------|
| **Dependency Injection** | Static service | Constructor injection |
| **Value Object Purity** | Зависит от service | Чистый VO без dependencies |
| **Testability** | Сложно (static state) | Легко (mock service) |
| **Thread Safety** | Проблемы с static | Thread-safe |
| **Separation of Concerns** | VO делает calculations | Domain Service делает calculations |
| **Hidden Dependencies** | Да (static service) | Нет (явный constructor parameter) |
| **Code Organization** | Все в одном VO | VO + Domain Service |

### 💡 Использование: До vs После

**❌ БЫЛО:**

```java
// Где-то при старте приложения (скрытая магия)
@PostConstruct
public void init() {
    RouteGeometry.setDistanceCalculationService(distanceService);
}

// В коде
RouteGeometry geometry = RouteGeometry.fromWKT(wkt);
double distance = geometry.calculateDistanceMeters();  // Может упасть с NPE!
```

**✅ СТАЛО:**

```java
// В Use Case или Service
@Service
public class SomeUseCase {
    private final RouteGeometryService geometryService;  // ✅ Explicit dependency

    public SomeUseCase(RouteGeometryService geometryService) {
        this.geometryService = geometryService;
    }

    public Mono<Double> execute(String wkt) {
        RouteGeometry geometry = RouteGeometry.fromWKT(wkt);
        double distance = geometryService.calculateDistanceMeters(geometry);  // ✅ No NPE risk!
        return Mono.just(distance);
    }
}
```

**✅ Тестирование стало простым:**

```java
@Test
void shouldCalculateDistance() {
    // Arrange
    DistanceCalculationService mockService = mock(DistanceCalculationService.class);
    RouteGeometryService geometryService = new RouteGeometryService(mockService);

    when(mockService.calculateDistance(...))
        .thenReturn(Distance.ofMeters(1000));

    RouteGeometry geometry = RouteGeometry.fromWKT("LINESTRING(...)");

    // Act
    double distance = geometryService.calculateDistanceMeters(geometry);

    // Assert
    assertThat(distance).isEqualTo(2000);  // 2 segments * 1000m
}
```

---

<a name="problem-3"></a>
## 🔴 Проблема #3: Domain Events без версионирования

### ❌ ТЕКУЩИЙ КОД (Проблемный)

**Файл:** `transport/domain/event/RouteGeometryUpdatedEvent.java`

```java
@Getter
public class RouteGeometryUpdatedEvent implements DomainEvent {

    // ❌ Нет eventId
    // ❌ Нет version
    // ❌ Нет базового класса

    private final String routeId;
    private final String routeNumber;
    private final String routeName;
    private final Integer forwardPointsCount;
    private final Integer forwardDistanceMeters;
    private final Integer backwardPointsCount;
    private final Integer backwardDistanceMeters;
    private final Instant eventOccurredAt;

    public RouteGeometryUpdatedEvent(
            String routeId,
            String routeNumber,
            String routeName,
            Integer forwardPointsCount,
            Integer forwardDistanceMeters,
            Integer backwardPointsCount,
            Integer backwardDistanceMeters) {
        this.routeId = routeId;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.forwardPointsCount = forwardPointsCount;
        this.forwardDistanceMeters = forwardDistanceMeters;
        this.backwardPointsCount = backwardPointsCount;
        this.backwardDistanceMeters = backwardDistanceMeters;
        this.eventOccurredAt = Instant.now();  // ❌ Только timestamp, нет ID и version
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        return String.format("RouteGeometryUpdated[route=%s (%s), forward=%d points/%.1fkm, backward=%d points/%.1fkm]",
                routeNumber, routeName, forwardPointsCount, forwardDistanceMeters/1000.0,
                backwardPointsCount, backwardDistanceMeters != null ? backwardDistanceMeters/1000.0 : 0.0);
    }
}
```

**Проблемы:**
1. ❌ Нет event ID - невозможно отследить конкретное событие
2. ❌ Нет версионирования - нельзя эволюционировать события
3. ❌ Нет базового класса - дублирование кода
4. ❌ Нет metadata (кто, когда, correlation ID)
5. ❌ Сложно хранить в event store

### ✅ РЕФАКТОРИНГ (Решение)

**Шаг 1: Базовый класс для событий**

**Файл:** `transport/domain/event/TransportDomainEvent.java` (НОВЫЙ)

```java
package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * ✅ Базовый класс для всех domain events в Transport BC.
 *
 * Предоставляет:
 * - Уникальный ID события
 * - Timestamp
 * - Версионирование для эволюции схемы событий
 */
@Getter
public abstract class TransportDomainEvent implements DomainEvent {

    private final UUID eventId;
    private final Instant timestamp;
    private final int version;

    /**
     * ✅ Конструктор для создания НОВЫХ событий (текущая версия)
     */
    protected TransportDomainEvent() {
        this.eventId = UUID.randomUUID();
        this.timestamp = Instant.now();
        this.version = getCurrentVersion();
    }

    /**
     * ✅ Конструктор для восстановления событий из хранилища
     * (например, при десериализации из event store)
     */
    protected TransportDomainEvent(UUID eventId, Instant timestamp, int version) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.version = version;
    }

    /**
     * ✅ Каждое событие должно объявить свою текущую версию
     */
    public abstract int getCurrentVersion();

    @Override
    public Instant getOccurredAt() {
        return timestamp;
    }

    /**
     * ✅ Проверка совместимости версий
     */
    public boolean isCompatibleWith(int requiredVersion) {
        return this.version >= requiredVersion;
    }

    @Override
    public String toString() {
        return String.format("%s{eventId=%s, version=%d, timestamp=%s}",
                getClass().getSimpleName(), eventId, version, timestamp);
    }
}
```

**Шаг 2: Конкретное событие с версионированием**

**Файл:** `transport/domain/event/RouteGeometryUpdatedEvent.java` (после рефакторинга)

```java
package biz.ugur.busroutebackend.transport.domain.event;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * ✅ Событие обновления геометрии маршрута.
 *
 * Version 1 (initial): Содержит информацию о forward и backward geometry
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class RouteGeometryUpdatedEvent extends TransportDomainEvent {

    // ✅ Текущая версия события
    public static final int CURRENT_VERSION = 1;

    private final String routeId;
    private final String routeNumber;
    private final String routeName;
    private final Integer forwardPointsCount;
    private final Integer forwardDistanceMeters;
    private final Integer backwardPointsCount;
    private final Integer backwardDistanceMeters;

    /**
     * ✅ Конструктор для создания НОВОГО события
     * (использует текущую версию)
     */
    public RouteGeometryUpdatedEvent(
            String routeId,
            String routeNumber,
            String routeName,
            Integer forwardPointsCount,
            Integer backwardPointsCount,
            Integer forwardDistanceMeters,
            Integer backwardDistanceMeters) {
        super();  // ✅ Генерирует eventId, timestamp, version
        this.routeId = routeId;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.forwardPointsCount = forwardPointsCount;
        this.backwardPointsCount = backwardPointsCount;
        this.forwardDistanceMeters = forwardDistanceMeters;
        this.backwardDistanceMeters = backwardDistanceMeters;
    }

    /**
     * ✅ Конструктор для восстановления события из хранилища
     * (может иметь старую версию)
     */
    public RouteGeometryUpdatedEvent(
            UUID eventId,
            Instant timestamp,
            int version,
            String routeId,
            String routeNumber,
            String routeName,
            Integer forwardPointsCount,
            Integer backwardPointsCount,
            Integer forwardDistanceMeters,
            Integer backwardDistanceMeters) {
        super(eventId, timestamp, version);  // ✅ Сохраняет исторические данные
        this.routeId = routeId;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.forwardPointsCount = forwardPointsCount;
        this.backwardPointsCount = backwardPointsCount;
        this.forwardDistanceMeters = forwardDistanceMeters;
        this.backwardDistanceMeters = backwardDistanceMeters;
    }

    @Override
    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String toString() {
        return String.format(
            "RouteGeometryUpdatedEvent{eventId=%s, version=%d, route=%s (%s), " +
            "forward=%d points/%.1fkm, backward=%d points/%.1fkm, timestamp=%s}",
            getEventId(), getVersion(),
            routeNumber, routeName,
            forwardPointsCount, forwardDistanceMeters/1000.0,
            backwardPointsCount, backwardDistanceMeters != null ? backwardDistanceMeters/1000.0 : 0.0,
            getTimestamp()
        );
    }
}
```

**Шаг 3: Пример эволюции события (Version 2)**

```java
/**
 * ✅ ПРИМЕР: Version 2 - добавлено поле geometryType
 *
 * Backward compatibility обеспечивается:
 * 1. Version 1 события могут быть прочитаны (geometryType = null)
 * 2. Version 2 события содержат geometryType
 * 3. Event handlers проверяют версию и обрабатывают соответственно
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class RouteGeometryUpdatedEvent extends TransportDomainEvent {

    public static final int CURRENT_VERSION = 2;  // ✅ Увеличили версию

    private final String routeId;
    private final String routeNumber;
    private final String routeName;
    private final Integer forwardPointsCount;
    private final Integer forwardDistanceMeters;
    private final Integer backwardPointsCount;
    private final Integer backwardDistanceMeters;

    // ✅ НОВОЕ ПОЛЕ в версии 2
    private final GeometryType geometryType;  // LINESTRING, MULTILINESTRING, etc.

    /**
     * ✅ Конструктор Version 2
     */
    public RouteGeometryUpdatedEvent(
            String routeId,
            String routeNumber,
            String routeName,
            Integer forwardPointsCount,
            Integer backwardPointsCount,
            Integer forwardDistanceMeters,
            Integer backwardDistanceMeters,
            GeometryType geometryType) {  // ✅ Новый параметр
        super();
        this.routeId = routeId;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.forwardPointsCount = forwardPointsCount;
        this.backwardPointsCount = backwardPointsCount;
        this.forwardDistanceMeters = forwardDistanceMeters;
        this.backwardDistanceMeters = backwardDistanceMeters;
        this.geometryType = geometryType;
    }

    /**
     * ✅ Конструктор для восстановления из хранилища (поддерживает версию 1)
     */
    public RouteGeometryUpdatedEvent(
            UUID eventId,
            Instant timestamp,
            int version,  // ✅ Может быть 1 или 2
            String routeId,
            String routeNumber,
            String routeName,
            Integer forwardPointsCount,
            Integer backwardPointsCount,
            Integer forwardDistanceMeters,
            Integer backwardDistanceMeters,
            GeometryType geometryType) {  // ✅ null для версии 1
        super(eventId, timestamp, version);
        this.routeId = routeId;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.forwardPointsCount = forwardPointsCount;
        this.backwardPointsCount = backwardPointsCount;
        this.forwardDistanceMeters = forwardDistanceMeters;
        this.backwardDistanceMeters = backwardDistanceMeters;
        // ✅ Для Version 1 событий geometryType будет null
        this.geometryType = geometryType != null ? geometryType : GeometryType.LINESTRING;
    }

    @Override
    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    // ✅ Метод для проверки версии при обработке
    public boolean hasGeometryType() {
        return getVersion() >= 2;
    }
}
```

**Шаг 4: Event Handler с поддержкой версий**

```java
@Component
@Slf4j
public class RouteGeometryEventHandler {

    @EventListener
    public Mono<Void> handleRouteGeometryUpdated(RouteGeometryUpdatedEvent event) {
        log.info("Processing RouteGeometryUpdatedEvent version {} for route {}",
                event.getVersion(), event.getRouteNumber());

        // ✅ Обработка в зависимости от версии
        if (event.getVersion() == 1) {
            return handleVersion1(event);
        } else if (event.getVersion() >= 2) {
            return handleVersion2(event);
        } else {
            log.warn("Unknown event version: {}", event.getVersion());
            return Mono.empty();
        }
    }

    private Mono<Void> handleVersion1(RouteGeometryUpdatedEvent event) {
        // Обработка Version 1 (без geometryType)
        return cacheService.updateRouteGeometry(
            event.getRouteId(),
            event.getForwardDistanceMeters(),
            event.getBackwardDistanceMeters()
        );
    }

    private Mono<Void> handleVersion2(RouteGeometryUpdatedEvent event) {
        // Обработка Version 2 (с geometryType)
        return cacheService.updateRouteGeometry(
            event.getRouteId(),
            event.getForwardDistanceMeters(),
            event.getBackwardDistanceMeters(),
            event.getGeometryType()  // ✅ Новое поле
        );
    }
}
```

### 📊 Сравнение: До vs После

| Аспект | До (❌) | После (✅) |
|--------|---------|------------|
| **Event ID** | Нет | UUID для каждого события |
| **Versioning** | Нет | Версия для каждого типа события |
| **Base Class** | Нет | TransportDomainEvent |
| **Metadata** | Только timestamp | ID + version + timestamp |
| **Event Evolution** | Невозможна | Поддерживается |
| **Event Store** | Сложно хранить | Готово для хранения |
| **Backward Compatibility** | Нет | Да (через версии) |
| **Traceability** | Нет | Да (уникальный ID) |

### 💡 Преимущества Версионирования

1. **Event Sourcing Ready:**
   ```java
   // ✅ Можно сохранить в event store
   eventStore.save(event);

   // ✅ Можно восстановить агрегат из событий
   List<TransportDomainEvent> events = eventStore.findByAggregateId(routeId);
   BusRoute route = BusRoute.reconstruct(events);
   ```

2. **Backward Compatibility:**
   ```java
   // ✅ Version 1 события продолжают работать с Version 2 кодом
   RouteGeometryUpdatedEvent v1Event = eventStore.load(eventId);

   if (v1Event.hasGeometryType()) {
       // Версия 2
       processWithGeometryType(v1Event.getGeometryType());
   } else {
       // Версия 1 - fallback
       processWithoutGeometryType();
   }
   ```

3. **Audit Trail:**
   ```java
   // ✅ Полный audit trail с ID событий
   List<TransportDomainEvent> history = eventStore.findByRouteId(routeId);
   history.forEach(event -> {
       System.out.println(String.format(
           "Event %s (v%d) at %s",
           event.getEventId(),
           event.getVersion(),
           event.getTimestamp()
       ));
   });
   ```

---

**Продолжение документа в следующей части...**

(Документ слишком большой для одного файла. Продолжить с Problem #4: Cross-BC Dependency + SRP Violation?)
