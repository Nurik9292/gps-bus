# Анализ реализации пагинации в проекте Bus Route Backend

## Дата анализа
2 ноября 2025

## Исполнительное резюме

Класс `PagedResult.java` был создан как универсальная обёртка для пагинированных данных, но **нигде в проекте не используется**. Вместо него каждый модуль использует свои собственные DTO-классы с `PaginationInfo`. Это приводит к дублированию кода, но позволяет добавлять domain-specific метаданные.

---

## 1. Инфраструктура пагинации

### 1.1. PagedResult.java (НЕ ИСПОЛЬЗУЕТСЯ)

**Расположение:** `src/main/java/biz/ugur/busroutebackend/shared/application/dto/PagedResult.java`

**Структура:**
```java
public final class PagedResult<T> {
    private final List<T> items;           // Коллекция элементов
    private final PaginationInfo pagination; // Метаданные пагинации
}
```

**Возможности:**
- Фабричные методы: `of()`, `empty()`
- Маппинг: `map(Function<T, R>)` для трансформации элементов
- Convenience методы: `hasContent()`, `hasNext()`, `hasPrevious()`
- Immutable дизайн

**Статус:** ❌ **Ноль использований в кодовой базе**

---

### 1.2. PaginationInfo.java (АКТИВНО ИСПОЛЬЗУЕТСЯ)

**Расположение:** `src/main/java/biz/ugur/busroutebackend/shared/application/dto/PaginationInfo.java`

**Структура:**
```java
public final class PaginationInfo {
    @JsonProperty("current_page") private final int currentPage;
    @JsonProperty("page_size") private final int pageSize;
    @JsonProperty("total_items") private final long totalItems;
    @JsonProperty("total_pages") private final int totalPages;
}
```

**Используется в:**
- `RouteList` (transport module)
- `StopList` (transport module)
- `AdminList` (admin module)
- Всех REST response классах для admin и mobile API

---

## 2. Паттерны пагинации по модулям

### 2.1. Transport Module (Routes & Stops)

**RouteList** (`transport/application/dto/route/RouteList.java:22-42`)
```java
public record RouteList(
    List<RouteData> routes,        // ✅ Domain-specific название
    Long activeCount,              // ✅ Бизнес-метрика
    PaginationInfo pagination      // ✅ Стандартная пагинация
)
```

**StopList** (`transport/application/dto/stop/StopList.java:22-42`)
```java
public record StopList(
    List<StopData> stops,          // ✅ Domain-specific название
    Long activeCount,              // ✅ Бизнес-метрика
    PaginationInfo pagination      // ✅ Стандартная пагинация
)
```

**Use Cases:**
- `GetAllBusRoutesUseCase.java:23-115` - без пагинации (fake pagination)
- `GetAllBusRoutesWithPaginationUseCase.java:27-133` - с `PageRequest`
- `GetAllBusStopsUseCase.java:23-96` - с `PageRequest`

**Преимущества:**
- Включает `activeCount` для бизнес-логики
- Семантически правильные названия полей (`routes`, `stops`)
- Единообразная структура между routes и stops

---

### 2.2. Admin Module

**AdminList** (`admin/application/dto/admin/AdminList.java:14-26`)
```java
public record AdminList(
    List<AdminResult> admins,      // ✅ Domain-specific название
    Long activeCount,              // ✅ Бизнес-метрика
    PaginationInfo pagination      // ✅ Стандартная пагинация
)
```

**CityList** (`admin/application/dto/city/CityList.java:8-20`)
```java
@Data  // ⚠️ Mutable, inconsistent!
public class CityList {
    private List<CityResult> cities;
    private Integer totalCount;
    private Long activeCount;
    // ❌ НЕТ PaginationInfo вообще!
}
```

**Проблемы:**
- `CityList` не поддерживает пагинацию
- Использует `@Data` (mutable) вместо record или final fields
- Нет стандартизации с другими List DTO

---

### 2.3. Banner Module (ДРУГОЙ ПАТТЕРН)

**BannerListResponse** (`banner/application/dto/BannerListResponse.java:9-35`)
```java
public record BannerListResponse(
    List<BannerResponse> banners,
    Integer totalCount,
    Long activeCount,
    Boolean hasMore                 // ⚠️ Cursor-based pagination!
)
```

**Особенности:**
- Использует `hasMore` вместо `PaginationInfo`
- Реализует cursor-based pagination вместо page-based
- Единственный модуль с таким подходом

---

## 3. REST Response Layer

### 3.1. Admin API Responses (Factory Pattern)

**BusRouteListResponse** (`interfaces/rest/admin/V1/response/route/BusRouteListResponse.java:24-60`)
```java
public record BusRouteListResponse(
    List<BusRouteResponse> routes,
    Long activeCount,
    PaginationInfo pagination
) {
    public static BusRouteListResponse fromResult(RouteList routeList) {
        // Маппинг из application layer в REST layer
    }
}
```

**Паттерн:** Application Layer DTO → REST Response
- Разделение слоёв (Clean Architecture)
- Factory методы для конвертации
- Immutable records

---

### 3.2. Mobile API Responses (Builder Pattern)

**MobileRouteListResponse** (`interfaces/rest/mobile/V1/response/MobileRouteListResponse.java:14-26`)
```java
@Data
@Builder
public class MobileRouteListResponse {
    private List<MobileRouteResponse> routes;
    private Long activeCount;
    private PaginationInfo pagination;
}
```

**Построение в контроллере** (`MobileRouteApiController.java:65-69`):
```java
return MobileRouteListResponse.builder()
    .routes(responses)
    .activeCount(routeList.getActiveCount())
    .pagination(routeList.getPagination())
    .build();
```

**Отличия от Admin API:**
- Mutable (`@Data`)
- Builder pattern вместо factory methods
- Построение в контроллерах вместо статических методов

---

## 4. Использование Spring Data Pagination

### 4.1. PageRequest конвертация

**Все use cases с пагинацией:**
```java
// Клиент отправляет 1-based page (page=1 для первой страницы)
// Spring PageRequest ожидает 0-based (page=0 для первой страницы)

PageRequest pageRequest = PageRequest.of(
    page - 1,        // ⚠️ Конвертация: 1-based → 0-based
    size,
    sort
);
```

**Примеры:**
- `GetAllBusRoutesWithPaginationUseCase.java:131`
- `GetAllBusStopsUseCase.java:92`
- `GetBannersWithPaginationUseCase.java:76`

### 4.2. Mobile API особенность

**MobileRouteApiController.java:85, 108:**
```java
// Клиент отправляет 0-based page
int pageNumber = page + 1;  // ⚠️ Конвертация: 0-based → 1-based для DTO
```

**Проблема:** Мобильное API использует другую конвенцию (0-based) чем остальные endpoint'ы

---

## 5. Почему PagedResult не используется

### 5.1. Недостающая функциональность

`PagedResult` предоставляет только:
```java
{
  "items": [...],      // Generic название
  "pagination": {...}  // Стандартная информация
}
```

Проект требует:
```java
{
  "routes": [...],         // Domain-specific название
  "active_count": 10,      // Бизнес-метрика
  "pagination": {...}      // Стандартная информация
}
```

### 5.2. Бизнес-требования

**activeCount** присутствует везде:
- Для routes/stops - количество активных маршрутов/остановок
- Для admins - количество активных администраторов
- Используется в UI для отображения статистики

**Domain-specific названия:**
- `routes` вместо `items` для RouteList
- `stops` вместо `items` для StopList
- `admins` вместо `items` для AdminList
- Улучшает читаемость API контракта

### 5.3. Архитектурные причины

**Разделение слоёв:**
- Application Layer: `RouteList`, `StopList`, `AdminList`
- REST Layer: `BusRouteListResponse`, `BusStopListResponse`
- Каждый слой может иметь свою структуру

**Type safety:**
- Record types обеспечивают compile-time проверки
- Generic `PagedResult<T>` менее типобезопасен

---

## 6. Найденные несоответствия

### 6.1. CityList - отсутствие пагинации

**Файл:** `admin/application/dto/city/CityList.java:8-20`

**Проблемы:**
- ❌ Нет `PaginationInfo`
- ❌ Использует mutable `@Data`
- ❌ Невозможно пагинировать города
- ❌ Не соответствует паттерну других List DTO

**Рекомендация:** Добавить `PaginationInfo` и сделать immutable record

---

### 6.2. Banner Module - другой паттерн

**Файл:** `banner/application/dto/BannerListResponse.java:9-35`

**Особенности:**
- ✓ Использует cursor-based pagination (`hasMore`)
- ⚠️ Не соответствует page-based паттерну остальных модулей
- ⚠️ Может запутать пользователей API

**Вопрос:** Есть ли техническая причина для cursor-based pagination баннеров?

---

### 6.3. Mobile vs Admin API responses

| Аспект | Admin API | Mobile API |
|--------|-----------|------------|
| Mutability | Immutable (record) | Mutable (@Data) |
| Создание | Factory methods | Builder pattern |
| Место создания | Static методы в DTO | Контроллеры |
| Page numbering | 1-based | 0-based (с конвертацией) |

**Проблема:** Разные подходы для одинаковой функциональности

---

### 6.4. Default pagination values

| Endpoint | Default Page | Default Size |
|----------|-------------|--------------|
| Admin Routes | 1 | 20 |
| Admin Stops | 1 | 20 |
| Mobile Routes | Обязательный | Обязательный |
| Mobile Stops | 1 | 1500 ⚠️ |
| Banners | 1 | 10 (max 100) |

**Проблема:** Mobile stops использует огромный default size = 1500 (строка 157-165 `MobileStopApiController.java`)

---

## 7. Рекомендации

### 7.1. Краткосрочные улучшения

#### 1. Удалить или использовать PagedResult
**Опция А: Удалить** (рекомендуется)
- Класс не используется
- Не соответствует бизнес-требованиям
- Создаёт confusion

**Опция Б: Расширить и использовать**
```java
public final class PagedResult<T> {
    private final List<T> items;
    private final Long activeCount;        // Добавить
    private final PaginationInfo pagination;
}
```

#### 2. Исправить CityList
```java
// Было:
@Data
public class CityList {
    private List<CityResult> cities;
    private Integer totalCount;
    private Long activeCount;
}

// Должно быть:
public record CityList(
    List<CityResult> cities,
    Long activeCount,
    PaginationInfo pagination  // Добавить!
)
```

#### 3. Стандартизировать Mobile API
- Перейти на 1-based page numbering
- Использовать records вместо @Data + @Builder
- Добавить factory methods как в Admin API

#### 4. Нормализовать default pagination
```java
// Предложенные дефолты:
public static final int DEFAULT_PAGE = 1;
public static final int DEFAULT_SIZE = 20;
public static final int MAX_SIZE = 100;
```

---

### 7.2. Долгосрочные улучшения

#### 1. Создать базовый класс для List DTOs
```java
public sealed interface PagedList<T>
    permits RouteList, StopList, AdminList, CityList {

    List<T> items();
    Long activeCount();
    PaginationInfo pagination();
}
```

#### 2. Унифицировать REST response создание
```java
@Component
public class PagedResponseMapper {
    public <T, R> PagedListResponse<R> toResponse(
        PagedList<T> list,
        Function<T, R> mapper
    ) {
        // Централизованная логика маппинга
    }
}
```

#### 3. Добавить базовый контроллер
```java
public abstract class BasePaginatedController {

    protected static final int DEFAULT_PAGE = 1;
    protected static final int DEFAULT_SIZE = 20;

    protected void validatePagination(int page, int size) {
        // Централизованная валидация
    }
}
```

#### 4. Пересмотреть Banner pagination
- Решить: нужен ли действительно cursor-based подход?
- Если да - документировать причину
- Если нет - мигрировать на page-based с `PaginationInfo`

---

## 8. Статистика дублирования кода

### Дублированная структура List DTOs

**Паттерн повторяется 3+ раза:**
```java
public record XxxList(
    List<XxxData> items,      // Разные названия
    Long activeCount,         // Идентично
    PaginationInfo pagination // Идентично
)
```

**Файлы:**
- `RouteList.java` (42 строки)
- `StopList.java` (42 строки)
- `AdminList.java` (26 строк)

**Дублирование:** ~110 строк кода

---

### Дублированная логика в Use Cases

**Повторяющийся паттерн:**
```java
PageRequest pageRequest = PageRequest.of(page - 1, size, sort);
// ... fetch data ...
int totalPages = (int) Math.ceil((double) totalElements / size);
```

**Файлы:**
- `GetAllBusRoutesWithPaginationUseCase.java`
- `GetAllBusStopsUseCase.java`
- `GetBannersWithPaginationUseCase.java`

---

## 9. Выводы

### Текущее состояние: ⚠️ Работает, но неоптимально

**Преимущества текущего подхода:**
- ✅ Domain-specific DTOs с правильными названиями
- ✅ Включает бизнес-метрики (`activeCount`)
- ✅ Разделение слоёв (application vs REST)
- ✅ Type-safe с records

**Недостатки текущего подхода:**
- ❌ `PagedResult` создан но не используется (dead code)
- ❌ Дублирование структуры List DTOs
- ❌ Несоответствия между модулями (Banner, City)
- ❌ Разные подходы для Admin vs Mobile API
- ❌ Нет централизованной валидации пагинации
- ❌ Хардкод магических чисел (size=1500)

---

### Главная причина неиспользования PagedResult

**PagedResult слишком generic** для потребностей проекта:
1. Не поддерживает `activeCount` (ключевая бизнес-метрика)
2. Использует generic название `items` вместо domain terms
3. Не интегрируется с существующей архитектурой слоёв

**Решение:** Либо расширить `PagedResult`, либо удалить и документировать текущий подход

---

## 10. Приоритеты рефакторинга

### P0 (Critical) - Удалить dead code
- [ ] Удалить `PagedResult.java` или расширить его
- [ ] Документировать решение в ADR

### P1 (High) - Устранить несоответствия
- [ ] Добавить pagination в `CityList`
- [ ] Стандартизировать `BannerListResponse`
- [ ] Унифицировать Mobile API с Admin API

### P2 (Medium) - Уменьшить дублирование
- [ ] Создать базовый sealed interface `PagedList<T>`
- [ ] Централизовать валидацию пагинации
- [ ] Вынести константы в конфигурацию

### P3 (Low) - Улучшить архитектуру
- [ ] Создать `PagedResponseMapper`
- [ ] Добавить `BasePaginatedController`
- [ ] Написать архитектурные тесты (ArchUnit)

---

## Ссылки на ключевые файлы

**Shared Infrastructure:**
- `PagedResult.java:16-93` - НЕ ИСПОЛЬЗУЕТСЯ
- `PaginationInfo.java:11-72` - ИСПОЛЬЗУЕТСЯ ВЕЗДЕ

**Application DTOs:**
- `transport/application/dto/route/RouteList.java:22-42`
- `transport/application/dto/stop/StopList.java:22-42`
- `admin/application/dto/admin/AdminList.java:14-26`
- `admin/application/dto/city/CityList.java:8-20` - БЕЗ ПАГИНАЦИИ
- `banner/application/dto/BannerListResponse.java:9-35` - ДРУГОЙ ПАТТЕРН

**Use Cases:**
- `transport/application/usecase/route/GetAllBusRoutesWithPaginationUseCase.java:27-133`
- `transport/application/usecase/stop/GetAllBusStopsUseCase.java:23-96`
- `banner/application/usecase/admin/GetBannersWithPaginationUseCase.java:22-79`

**Controllers:**
- `interfaces/rest/mobile/V1/controller/MobileRouteApiController.java:76-111`
- `interfaces/rest/mobile/V1/controller/MobileStopApiController.java:100-116`

---

**Дата генерации отчёта:** 2 ноября 2025
**Версия:** 1.0
**Автор:** Claude Code Analysis
