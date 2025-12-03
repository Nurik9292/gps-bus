# План реализации долгосрочных улучшений пагинации

**Дата:** 2 ноября 2025
**Версия:** 1.0
**Статус:** Готово к реализации

---

## Содержание

1. [Анализ BaseController](#1-анализ-basecontroller)
2. [Текущая архитектура](#2-текущая-архитектура)
3. [Проблемы и требования](#3-проблемы-и-требования)
4. [Целевая архитектура](#4-целевая-архитектура)
5. [Детальный план реализации](#5-детальный-план-реализации)
6. [Миграция Banner Module](#6-миграция-banner-module)
7. [План тестирования](#7-план-тестирования)
8. [Чеклист внедрения](#8-чеклист-внедрения)

---

## 1. Анализ BaseController

### 1.1. Текущая структура

**Файл:** `src/main/java/biz/ugur/busroutebackend/shared/infrastructure/web/BaseController.java`

**Основные компоненты:**

```java
public abstract class BaseController {
    private final MessageSource messageSource;

    // Response wrapper methods
    protected <T> Mono<ResponseEntity<ApiResponse<T>>> ok(Mono<T> data)
    protected <T> Mono<ResponseEntity<ApiResponse<T>>> created(Mono<T> data)
    protected Mono<ResponseEntity<Void>> noContent()
    protected <T> Mono<ResponseEntity<ApiResponse<T>>> accepted(Mono<T> data)

    // List handling
    protected <T> Mono<ResponseEntity<ApiResponse<List<T>>>> okList(Flux<T> data)
    protected <T> Mono<ResponseEntity<ApiResponse<List<T>>>> okListBuffered(Flux<T> data)

    // Utility
    protected String camelToSnake(String str)

    // Inner class
    public static class ApiResponse<T> {
        private final boolean success;
        private final T data;
        private final String errorCode;
        private final String errorMessage;
        private final LocalDateTime timestamp;
    }
}
```

### 1.2. Сильные стороны

✅ **Единообразное оборачивание ответов:**
- Все успешные ответы оборачиваются в `ApiResponse<T>`
- Включает timestamp, success flag, error handling
- Reactive-first дизайн (Mono/Flux)

✅ **Логирование:**
- Автоматическое логирование успешных ответов
- Автоматическое логирование ошибок
- Debug-friendly с именем контроллера

✅ **Интернационализация:**
- Интеграция с `MessageSource`
- Поддержка локализации через `LocaleContextHolder`

✅ **HTTP статусы:**
- Semantic methods: `ok()`, `created()`, `accepted()`, `noContent()`
- Type-safe ResponseEntity handling

### 1.3. Недостатки и пробелы

❌ **Нет поддержки пагинации:**
- `okList()` возвращает простой `List<T>` без метаданных
- Нет встроенной поддержки `PaginationInfo`
- Каждый контроллер вручную создает пагинированные ответы

❌ **Нет валидации параметров пагинации:**
- Page, size, sort validation дублируется в контроллерах
- Нет централизованных констант (DEFAULT_PAGE, MAX_SIZE)

❌ **Нет типобезопасности для пагинированных данных:**
- Контроллеры вручную собирают ответы
- Легко ошибиться в структуре

❌ **Буферизация без контекста:**
- `okListBuffered()` использует fixed buffer size (200)
- Не учитывает pagination context

### 1.4. BaseMobileController

**Файл:** `src/main/java/biz/ugur/busroutebackend/interfaces/rest/mobile/V1/controller/BaseMobileController.java`

```java
public abstract class BaseMobileController extends BaseController {
    private final RequestedContentTypeResolver requestedContentTypeResolver;
    protected final RouteIsFavoriteUseCase routeIsFavoriteUseCase;

    protected Mono<ClientPrincipal> getCurrentPrincipal()
}
```

**Особенности:**
- Специфичные для mobile функции (favorites, content negotiation)
- Доступ к authenticated client principal
- Наследует все методы `BaseController`

**Проблема:** Нет специализированных методов для mobile pagination (которая использует 0-based indexing)

---

## 2. Текущая архитектура

### 2.1. Структура пагинированных ответов

#### Pattern A: Transport & Admin Modules

**Application Layer:**
```java
public record RouteList(
    List<RouteData> routes,      // Domain-specific name
    Long activeCount,            // Business metric
    PaginationInfo pagination    // Standard pagination
)
```

**REST Layer:**
```java
public record BusRouteListResponse(
    List<BusRouteResponse> routes,
    Long activeCount,
    PaginationInfo pagination
) {
    public static BusRouteListResponse fromResult(RouteList routeList) {
        // Factory method for conversion
    }
}
```

**Controller:**
```java
public Mono<ResponseEntity<ApiResponse<BusRouteListResponse>>> getAllRoutes(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int size
) {
    return getAllBusRoutesUseCase.execute(page, size)
        .map(BusRouteListResponse::fromResult)
        .flatMap(this::ok);
}
```

#### Pattern B: Banner Module (CURSOR-BASED)

**Application Layer:**
```java
public record BannerListResponse(
    List<BannerResponse> banners,
    Integer totalCount,          // Current page size
    Long activeCount,            // Varies by use case
    Boolean hasMore              // ❌ Instead of PaginationInfo
)
```

**Controller:**
```java
return getBannersWithPaginationUseCase.execute(query)
    .flatMap(this::ok);
```

**hasMore calculation:**
```java
hasMore = banners.size() == query.getSize()  // Heuristic
```

### 2.2. Дублирование кода

**Повторяющийся паттерн (4+ раза):**

1. **RouteList** (transport/application/dto/route/)
2. **StopList** (transport/application/dto/stop/)
3. **AdminList** (admin/application/dto/admin/)
4. **BusRouteListResponse** (interfaces/rest/admin/V1/response/route/)
5. **BusStopListResponse** (interfaces/rest/admin/V1/response/stop/)
6. **AdminListResponse** (interfaces/rest/admin/V1/response/admin/)
7. **MobileRouteListResponse** (interfaces/rest/mobile/V1/response/)
8. **MobileStopListResponse** (interfaces/rest/mobile/V1/response/)

**Общая структура:**
```java
{
    "items": [...],           // domain-specific name
    "active_count": N,        // business metric
    "pagination": {           // standard metadata
        "current_page": 1,
        "page_size": 20,
        "total_items": 100,
        "total_pages": 5
    }
}
```

### 2.3. Несоответствия

| Аспект | Transport/Admin | Banner | City |
|--------|-----------------|--------|------|
| Pagination Type | Page-based | Cursor-based | None |
| Metadata | PaginationInfo | hasMore | - |
| Active Count | Total active | Varies | Total |
| Page Indexing | 1-based | 1-based | - |
| Total Count | Via pagination | Current page | Total |

---

## 3. Проблемы и требования

### 3.1. Текущие проблемы

**P0 - Critical:**
1. ❌ Banner module использует cursor-based вместо page-based pagination
2. ❌ CityList не поддерживает pagination вообще
3. ❌ `PagedResult.java` существует но не используется (dead code)
4. ❌ Семантическая путаница: `activeCount` означает разное в разных модулях

**P1 - High:**
5. ❌ Дублирование структуры List DTOs (8+ файлов)
6. ❌ Нет централизованной валидации pagination параметров
7. ❌ Mobile API использует 0-based pages, остальные 1-based
8. ❌ Хардкод magic numbers (size=1500 для mobile stops)
9. ❌ Нет единого подхода к созданию пагинированных ответов

**P2 - Medium:**
10. ❌ BaseController не поддерживает pagination
11. ❌ Отсутствие архитектурных тестов для pagination
12. ❌ Нет документации по pagination conventions

### 3.2. Бизнес-требования

**Должны поддерживаться:**
- ✅ Domain-specific названия полей (`routes`, `stops`, `admins`, не generic `items`)
- ✅ Business метрики (`activeCount` - общее количество активных элементов)
- ✅ Стандартные метаданные пагинации (`PaginationInfo`)
- ✅ Разделение слоев (Application Layer DTO ≠ REST Response)
- ✅ Type safety (records, immutability)

**Должны быть добавлены:**
- 🆕 Единый интерфейс для пагинированных DTO
- 🆕 Централизованная валидация параметров
- 🆕 Helper методы в BaseController
- 🆕 Consistent API contracts

### 3.3. Технические требования

**Архитектурные:**
- Reactive-first (Mono/Flux)
- Clean Architecture (layered separation)
- DDD principles (domain-specific DTOs)
- Immutable data structures

**Качество кода:**
- Type-safe
- Minimal code duplication
- Self-documenting
- Testable

**Обратная совместимость:**
- Существующие API endpoints должны продолжать работать
- Можно добавлять новые поля, но не удалять старые
- Миграция должна быть постепенной

---

## 4. Целевая архитектура

### 4.1. Архитектурные компоненты

```
┌─────────────────────────────────────────────────────────────┐
│                    REST Controllers                          │
│  - AdminRouteController                                      │
│  - MobileRouteApiController                                  │
│  - AdminBannerController                                     │
│  Uses: BasePaginatedController                               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│             BasePaginatedController (NEW)                    │
│  + validatePagination()                                      │
│  + okPaginated(Mono<PagedList<T>>)                          │
│  + DEFAULT_PAGE, DEFAULT_SIZE, MAX_SIZE                      │
│  Extends: BaseController                                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  BaseController                              │
│  + ok(), created(), accepted()                               │
│  + okList()                                                  │
│  + ApiResponse<T> wrapper                                    │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│           Application Layer (Use Cases)                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│           PagedList<T> Interface (NEW)                       │
│  Implemented by: RouteList, StopList, AdminList, BannerList │
│                                                              │
│  sealed interface PagedList<T> {                            │
│      List<T> items();                                       │
│      Long activeCount();                                     │
│      PaginationInfo pagination();                            │
│  }                                                           │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│         PagedResponseMapper (NEW)                            │
│  + <T, R> toResponse(PagedList<T>, Function<T,R>)           │
│  + <T> toMobileResponse(PagedList<T>, ...)                  │
└─────────────────────────────────────────────────────────────┘
```

### 4.2. PagedList Sealed Interface

**Файл:** `src/main/java/biz/ugur/busroutebackend/shared/application/dto/PagedList.java`

```java
package biz.ugur.busroutebackend.shared.application.dto;

import java.util.List;

/**
 * Sealed interface for paginated data transfer objects.
 * Enforces consistent structure across all paginated responses.
 *
 * @param <T> The type of items in the paginated list
 */
public sealed interface PagedList<T>
    permits RouteList, StopList, AdminList, CityList, BannerList {

    /**
     * @return The list of items on the current page
     */
    List<T> items();

    /**
     * @return Total count of active items (business metric)
     */
    Long activeCount();

    /**
     * @return Pagination metadata (current page, page size, total items, total pages)
     */
    PaginationInfo pagination();

    /**
     * @return Number of items on current page
     */
    default int size() {
        return items().size();
    }

    /**
     * @return True if current page is empty
     */
    default boolean isEmpty() {
        return items().isEmpty();
    }

    /**
     * @return True if current page has content
     */
    default boolean hasContent() {
        return !items().isEmpty();
    }
}
```

**Преимущества:**
- ✅ Sealed interface - compile-time проверка всех имплементаций
- ✅ Default методы уменьшают дублирование
- ✅ Type-safe - невозможно создать несовместимую структуру
- ✅ Self-documenting через Javadoc

### 4.3. Обновленные List DTOs

#### RouteList (обновленная)

```java
package biz.ugur.busroutebackend.transport.application.dto.route;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;

import java.util.Collections;
import java.util.List;

/**
 * Paginated list of bus routes.
 */
public record RouteList(
    List<RouteData> routes,        // domain-specific name
    Long activeCount,              // total active routes
    PaginationInfo pagination      // standard metadata
) implements PagedList<RouteData> {

    // Compact constructor for validation
    public RouteList {
        routes = Collections.unmodifiableList(routes);
    }

    // PagedList interface implementation
    @Override
    public List<RouteData> items() {
        return routes;
    }

    @Override
    public Long activeCount() {
        return activeCount;
    }

    @Override
    public PaginationInfo pagination() {
        return pagination;
    }

    // Factory method
    public static RouteList of(
        List<RouteData> routes,
        Long activeCount,
        int currentPage,
        int pageSize,
        long totalItems
    ) {
        PaginationInfo pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
        return new RouteList(routes, activeCount, pagination);
    }
}
```

#### BannerList (NEW - заменяет BannerListResponse в application layer)

```java
package biz.ugur.busroutebackend.banner.application.dto;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;

import java.util.Collections;
import java.util.List;

/**
 * Paginated list of banners.
 * Replaces cursor-based pagination (hasMore) with page-based pagination.
 */
public record BannerList(
    List<BannerResponse> banners,  // domain-specific name
    Long activeCount,              // total active banners
    PaginationInfo pagination      // standard metadata
) implements PagedList<BannerResponse> {

    public BannerList {
        banners = Collections.unmodifiableList(banners);
    }

    @Override
    public List<BannerResponse> items() {
        return banners;
    }

    @Override
    public Long activeCount() {
        return activeCount;
    }

    @Override
    public PaginationInfo pagination() {
        return pagination;
    }

    public static BannerList of(
        List<BannerResponse> banners,
        Long activeCount,
        int currentPage,
        int pageSize,
        long totalItems
    ) {
        PaginationInfo pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
        return new BannerList(banners, activeCount, pagination);
    }
}
```

### 4.4. BasePaginatedController

**Файл:** `src/main/java/biz/ugur/busroutebackend/shared/infrastructure/web/BasePaginatedController.java`

```java
package biz.ugur.busroutebackend.shared.infrastructure.web;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Base controller with pagination support.
 * Extends BaseController with standardized pagination handling.
 */
@Slf4j
public abstract class BasePaginatedController extends BaseController {

    // Pagination constants
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;

    protected BasePaginatedController(MessageSource messageSource) {
        super(messageSource);
    }

    /**
     * Wraps a paginated result in ApiResponse and ResponseEntity.
     *
     * @param pagedData Mono containing PagedList
     * @return Mono of ResponseEntity with ApiResponse wrapper
     */
    protected <T> Mono<ResponseEntity<ApiResponse<PagedList<T>>>> okPaginated(
        Mono<? extends PagedList<T>> pagedData
    ) {
        return pagedData
            .cast(PagedList.class)
            .flatMap(this::ok)
            .doOnSuccess(response -> logPaginatedResponse(response));
    }

    /**
     * Validates pagination parameters.
     *
     * @param page Current page (1-indexed)
     * @param size Page size
     * @throws IllegalArgumentException if parameters are invalid
     */
    protected void validatePagination(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException(
                String.format("Page must be >= 1, got: %d", page)
            );
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                String.format("Size must be between %d and %d, got: %d",
                    MIN_SIZE, MAX_SIZE, size)
            );
        }
    }

    /**
     * Creates Spring Data PageRequest from 1-indexed page number.
     *
     * @param page 1-indexed page number
     * @param size Page size
     * @param sort Sort specification
     * @return PageRequest (0-indexed)
     */
    protected Pageable createPageRequest(int page, int size, Sort sort) {
        validatePagination(page, size);
        return PageRequest.of(page - 1, size, sort);  // Convert to 0-indexed
    }

    /**
     * Creates PageRequest with single field sort.
     */
    protected Pageable createPageRequest(
        int page,
        int size,
        String sortField,
        String sortOrder
    ) {
        Sort sort = createSort(sortField, sortOrder);
        return createPageRequest(page, size, sort);
    }

    /**
     * Creates Sort from field and order.
     */
    protected Sort createSort(String sortField, String sortOrder) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder)
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
        return Sort.by(direction, sortField);
    }

    private void logPaginatedResponse(ResponseEntity<?> response) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Paginated response: status={}",
                getControllerName(), response.getStatusCode());
        }
    }
}
```

**Возможности:**
- ✅ Константы для pagination (DEFAULT_PAGE, DEFAULT_SIZE, MAX_SIZE)
- ✅ Валидация параметров с понятными сообщениями
- ✅ Конвертация 1-based → 0-based для Spring PageRequest
- ✅ Helper методы для создания Sort и Pageable
- ✅ Type-safe `okPaginated()` метод
- ✅ Автоматическое логирование

### 4.5. PagedResponseMapper

**Файл:** `src/main/java/biz/ugur/busroutebackend/shared/application/mapper/PagedResponseMapper.java`

```java
package biz.ugur.busroutebackend.shared.application.mapper;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility for mapping paginated DTOs between layers.
 * Centralizes conversion logic from application layer to REST layer.
 */
@Component
public class PagedResponseMapper {

    /**
     * Maps items in PagedList using provided mapper function.
     * Preserves pagination metadata.
     *
     * @param source Source PagedList
     * @param itemMapper Function to map individual items
     * @return New PagedList with mapped items
     */
    public <S, T, P extends PagedList<S>> PagedList<T> map(
        P source,
        Function<S, T> itemMapper
    ) {
        List<T> mappedItems = source.items().stream()
            .map(itemMapper)
            .collect(Collectors.toList());

        // Return anonymous implementation
        return new PagedList<T>() {
            @Override
            public List<T> items() {
                return mappedItems;
            }

            @Override
            public Long activeCount() {
                return source.activeCount();
            }

            @Override
            public PaginationInfo pagination() {
                return source.pagination();
            }
        };
    }
}
```

**Использование:**
```java
// In controller or use case
RouteList applicationDto = useCase.execute(query);
PagedList<RouteResponse> restDto = pagedResponseMapper.map(
    applicationDto,
    RouteResponse::from
);
```

---

## 5. Детальный план реализации

### Фаза 1: Создание новой инфраструктуры (Week 1)

#### Step 1.1: PagedList sealed interface

**Файл:** `shared/application/dto/PagedList.java`

**Задачи:**
- [ ] Создать sealed interface `PagedList<T>`
- [ ] Добавить методы: `items()`, `activeCount()`, `pagination()`
- [ ] Добавить default методы: `size()`, `isEmpty()`, `hasContent()`
- [ ] Написать Javadoc
- [ ] Создать юнит-тесты (проверка default методов)

**Зависимости:** Нет

**Оценка:** 2 часа

---

#### Step 1.2: BasePaginatedController

**Файл:** `shared/infrastructure/web/BasePaginatedController.java`

**Задачи:**
- [ ] Создать класс, наследующий `BaseController`
- [ ] Добавить константы: `DEFAULT_PAGE`, `DEFAULT_SIZE`, `MAX_SIZE`
- [ ] Реализовать `validatePagination(int page, int size)`
- [ ] Реализовать `createPageRequest()` overloads
- [ ] Реализовать `createSort()`
- [ ] Реализовать `okPaginated()`
- [ ] Написать Javadoc
- [ ] Создать юнит-тесты

**Зависимости:** PagedList interface

**Оценка:** 4 часа

---

#### Step 1.3: PagedResponseMapper

**Файл:** `shared/application/mapper/PagedResponseMapper.java`

**Задачи:**
- [ ] Создать @Component класс
- [ ] Реализовать `map()` метод
- [ ] Написать Javadoc
- [ ] Создать юнит-тесты
- [ ] Добавить integration test (маппинг между слоями)

**Зависимости:** PagedList interface

**Оценка:** 3 часа

---

### Фаза 2: Миграция существующих List DTOs (Week 1-2)

#### Step 2.1: Обновить RouteList

**Файл:** `transport/application/dto/route/RouteList.java`

**Задачи:**
- [ ] Добавить `implements PagedList<RouteData>`
- [ ] Реализовать методы интерфейса
- [ ] Добавить factory method `of()`
- [ ] Обновить юнит-тесты
- [ ] Убедиться, что существующие use cases работают

**Зависимости:** PagedList interface

**Оценка:** 2 часа

---

#### Step 2.2: Обновить StopList

**Файл:** `transport/application/dto/stop/StopList.java`

**Задачи:**
- [ ] Аналогично RouteList
- [ ] Implements PagedList<StopData>
- [ ] Factory method
- [ ] Обновить тесты

**Зависимости:** PagedList interface

**Оценка:** 2 часа

---

#### Step 2.3: Обновить AdminList

**Файл:** `admin/application/dto/admin/AdminList.java`

**Задачи:**
- [ ] Implements PagedList<AdminResult>
- [ ] Factory method
- [ ] Обновить тесты

**Зависимости:** PagedList interface

**Оценка:** 2 часа

---

#### Step 2.4: Обновить CityList (ДОБАВИТЬ PAGINATION)

**Файл:** `admin/application/dto/city/CityList.java`

**До:**
```java
@Data
public class CityList {
    private List<CityResult> cities;
    private Integer totalCount;
    private Long activeCount;
}
```

**После:**
```java
public record CityList(
    List<CityResult> cities,
    Long activeCount,
    PaginationInfo pagination
) implements PagedList<CityResult> {
    // ...
}
```

**Задачи:**
- [ ] Переписать с @Data class на record
- [ ] Добавить поле `PaginationInfo pagination`
- [ ] Implements PagedList<CityResult>
- [ ] Обновить `GetAllCitiesUseCase` для поддержки pagination
- [ ] Обновить `CityRepository` (добавить `findAll(Pageable)`)
- [ ] Обновить `AdminCityController`
- [ ] Обновить тесты
- [ ] ⚠️ **Breaking change** - проверить API contracts

**Зависимости:** PagedList interface

**Оценка:** 4 часа

---

### Фаза 3: Миграция Banner Module (Week 2)

#### Step 3.1: Создать BannerList

**Файл:** `banner/application/dto/BannerList.java` (NEW)

**Задачи:**
- [ ] Создать `record BannerList implements PagedList<BannerResponse>`
- [ ] Добавить factory method
- [ ] Написать Javadoc (отметить замену cursor-based)
- [ ] Создать юнит-тесты

**Зависимости:** PagedList interface

**Оценка:** 2 часа

---

#### Step 3.2: Обновить GetBannersWithPaginationUseCase

**Файл:** `banner/application/usecase/admin/GetBannersWithPaginationUseCase.java`

**До:**
```java
public Mono<BannerListResponse> execute(BannerPaginationQuery query) {
    // ... fetch banners ...
    boolean hasMore = banners.size() == query.getSize();
    return Mono.just(new BannerListResponse(banners, activeCount, hasMore));
}
```

**После:**
```java
public Mono<BannerList> execute(BannerPaginationQuery query) {
    return bannerRepository.findAll(pageable)
        .collectList()
        .zipWith(bannerRepository.count())  // Get total count
        .flatMap(tuple -> {
            List<Banner> banners = tuple.getT1();
            Long totalCount = tuple.getT2();

            return bannerRepository.countActiveBanners()
                .map(activeCount -> BannerList.of(
                    banners.stream().map(mapper::toResponse).toList(),
                    activeCount,
                    query.getPage(),
                    query.getSize(),
                    totalCount
                ));
        });
}
```

**Задачи:**
- [ ] Заменить возвращаемый тип на `Mono<BannerList>`
- [ ] Удалить логику `hasMore`
- [ ] Добавить `bannerRepository.count()` для total count
- [ ] Использовать `BannerList.of()`
- [ ] Обновить юнит-тесты
- [ ] Обновить integration тесты

**Зависимости:** BannerList

**Оценка:** 3 часа

---

#### Step 3.3: Обновить SearchBannersUseCase

**Файл:** `banner/application/usecase/admin/SearchBannersUseCase.java`

**Задачи:**
- [ ] Изменить возвращаемый тип на `Mono<BannerList>`
- [ ] Добавить `countBySpecification()` для total count
- [ ] Удалить неправильное использование `activeCount` (использовалось для total search results)
- [ ] Теперь `activeCount` = общее количество активных баннеров
- [ ] Обновить тесты

**Важно:** Это исправляет semantic bug, где `activeCount` использовался для total search results.

**Зависимости:** BannerList

**Оценка:** 3 часа

---

#### Step 3.4: Обновить GetBannersWithPaginationByTypeUseCase (Client)

**Файл:** `banner/application/usecase/client/GetBannersWithPaginationByTypeUseCase.java`

**Задачи:**
- [ ] Изменить возвращаемый тип на `Mono<BannerList>`
- [ ] Добавить `countByType()` для total count
- [ ] Исправить `activeCount` (было current page size, должно быть total active)
- [ ] Обновить тесты

**Зависимости:** BannerList

**Оценка:** 3 часа

---

#### Step 3.5: Обновить AdminBannerController

**Файл:** `interfaces/rest/admin/V1/controller/AdminBannerController.java`

**Текущая логика:**
```java
@GetMapping
public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getAllBanners(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "25") int size,
    // ...
) {
    // If default params → GetAllBannersUseCase
    // Otherwise → GetBannersWithPaginationUseCase
}
```

**Задачи:**
- [ ] Наследовать от `BasePaginatedController` вместо `BaseController`
- [ ] Использовать константы из `BasePaginatedController`
- [ ] Заменить `this::ok` на `this::okPaginated`
- [ ] Применить `validatePagination()`
- [ ] Использовать `createPageRequest()`
- [ ] Обновить Swagger annotations
- [ ] Обновить integration тесты

**Зависимости:** BannerList, BasePaginatedController

**Оценка:** 2 часа

---

#### Step 3.6: Обновить MobileBannerApiController

**Файл:** `interfaces/rest/mobile/V1/controller/MobileBannerApiController.java`

**Текущая проблема:**
```java
@GetMapping("/paginated")
public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getBannersPaginated(
    @RequestParam(defaultValue = "0") int page,  // ❌ 0-indexed
    // ...
) {
    int pageNumber = page + 1;  // Manual conversion
}
```

**После:**
```java
@GetMapping("/paginated")
public Mono<ResponseEntity<ApiResponse<BannerList>>> getBannersPaginated(
    @RequestParam(defaultValue = "1") int page,  // ✅ 1-indexed
    @RequestParam(defaultValue = "10") int size,
    // ...
) {
    validatePagination(page, size);
    // Direct use, no conversion needed
}
```

**Задачи:**
- [ ] Изменить default page с 0 на 1
- [ ] Удалить manual conversion `page + 1`
- [ ] Использовать `validatePagination()`
- [ ] Использовать `okPaginated()`
- [ ] Обновить API documentation (migration guide)
- [ ] ⚠️ **Breaking change** - обновить mobile clients

**Зависимости:** BannerList, BasePaginatedController

**Оценка:** 3 часа

---

#### Step 3.7: Удалить BannerListResponse

**Файл:** `banner/application/dto/BannerListResponse.java`

**Задачи:**
- [ ] Убедиться, что все использования заменены на `BannerList`
- [ ] Удалить файл
- [ ] Обновить imports во всех файлах
- [ ] Проверить компиляцию

**Зависимости:** Все предыдущие banner миграции

**Оценка:** 1 час

---

### Фаза 4: Обновление контроллеров (Week 3)

#### Step 4.1: Обновить AdminRouteController

**Файл:** `interfaces/rest/admin/V1/controller/AdminRouteController.java`

**Задачи:**
- [ ] Extends `BasePaginatedController`
- [ ] Использовать `validatePagination()`
- [ ] Использовать `createPageRequest()`
- [ ] Использовать `okPaginated()`
- [ ] Обновить тесты

**Оценка:** 2 часа

---

#### Step 4.2: Обновить AdminStopController

**Аналогично AdminRouteController**

**Оценка:** 2 часа

---

#### Step 4.3: Обновить AdminUserController

**Аналогично для admin list endpoints**

**Оценка:** 2 часа

---

#### Step 4.4: Обновить MobileRouteApiController

**Файл:** `interfaces/rest/mobile/V1/controller/MobileRouteApiController.java`

**Задачи:**
- [ ] Исправить 0-based на 1-based indexing
- [ ] Extends `BasePaginatedController`
- [ ] Использовать helper методы
- [ ] Обновить тесты

**Оценка:** 2 часа

---

#### Step 4.5: Обновить MobileStopApiController

**Текущая проблема:**
```java
int page = 1;
int size = 1500;  // ❌ Огромный default size!
```

**Задачи:**
- [ ] Изменить default size на разумное значение (20)
- [ ] Использовать константы из `BasePaginatedController`
- [ ] Extends `BasePaginatedController`
- [ ] Обновить тесты
- [ ] ⚠️ Проверить влияние на mobile clients

**Оценка:** 2 часа

---

### Фаза 5: Удаление PagedResult (Week 3)

#### Step 5.1: Финальная проверка

**Задачи:**
- [ ] Найти все использования `PagedResult` (должно быть 0)
- [ ] Убедиться, что `PagedList` используется везде
- [ ] Проверить API documentation

**Оценка:** 1 час

---

#### Step 5.2: Удалить PagedResult.java

**Файл:** `shared/application/dto/PagedResult.java`

**Задачи:**
- [ ] Удалить файл
- [ ] Обновить PAGINATION_ANALYSIS_REPORT.md
- [ ] Добавить запись в CHANGELOG.md

**Оценка:** 0.5 часа

---

### Фаза 6: Документация и тесты (Week 3-4)

#### Step 6.1: Архитектурные тесты

**Файл:** `src/test/java/architecture/PaginationArchitectureTest.java` (NEW)

```java
@AnalyzeClasses(packages = "biz.ugur.busroutebackend")
public class PaginationArchitectureTest {

    @ArchTest
    static final ArchRule pagedListImplementations =
        classes()
            .that().implement(PagedList.class)
            .should().beRecords()
            .andShould().bePublic();

    @ArchTest
    static final ArchRule controllersShouldExtendBasePaginated =
        classes()
            .that().resideInAPackage("..controller..")
            .and().haveSimpleNameEndingWith("Controller")
            .and().areNotInterfaces()
            .and().doNotHaveSimpleName("BaseController")
            .and().doNotHaveSimpleName("BasePaginatedController")
            .and().doNotHaveSimpleName("BaseMobileController")
            .should().beAssignableTo(BasePaginatedController.class);

    @ArchTest
    static final ArchRule pagedListUsageInUseCases =
        methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..usecase..")
            .and().haveNameContaining("Pagination")
            .should().haveRawReturnType(assignableTo(Mono.class))
            .andShould().haveRawReturnType(withTypeArgument(assignableTo(PagedList.class)));
}
```

**Задачи:**
- [ ] Создать architecture test class
- [ ] Тест: все PagedList implementations должны быть records
- [ ] Тест: controllers должны наследовать BasePaginatedController
- [ ] Тест: use cases с pagination должны возвращать Mono<PagedList>
- [ ] Запустить и убедиться в прохождении

**Оценка:** 4 часа

---

#### Step 6.2: Integration тесты

**Задачи:**
- [ ] Создать `PaginationIntegrationTest.java`
- [ ] Тест: pagination работает с настоящей БД
- [ ] Тест: валидация параметров
- [ ] Тест: граничные случаи (page 1, last page, empty results)
- [ ] Тест: сортировка

**Оценка:** 4 часа

---

#### Step 6.3: API Documentation

**Файлы для обновления:**
- `PAGINATION_BEST_PRACTICES.md` (NEW)
- `API_MIGRATION_GUIDE.md` (NEW)
- `CLAUDE.md` (UPDATE)
- `README.md` (UPDATE)

**Содержимое PAGINATION_BEST_PRACTICES.md:**
```markdown
# Pagination Best Practices

## Overview
This project uses standardized page-based pagination across all modules.

## Implementation Guide

### 1. Creating a Paginated DTO
...

### 2. Implementing a Pagination Use Case
...

### 3. Creating a Paginated Controller Endpoint
...

## Common Patterns
...

## Anti-patterns
...
```

**Задачи:**
- [ ] Написать best practices guide
- [ ] Написать migration guide для API consumers
- [ ] Обновить CLAUDE.md с новыми паттернами
- [ ] Обновить README.md

**Оценка:** 4 часа

---

## 6. Миграция Banner Module

### 6.1. Что меняется

#### Before (Cursor-based)

**Response Structure:**
```json
{
  "success": true,
  "data": {
    "banners": [...],
    "total_count": 10,
    "active_count": 25,
    "has_more": true
  }
}
```

**Client Logic:**
```javascript
let page = 1;
do {
  const response = await fetch(`/api/v1/mobile/banners/paginated?page=${page}&size=10`);
  const data = response.data;

  // Process banners
  displayBanners(data.banners);

  // Check if more pages exist
  page++;
} while (data.has_more);
```

**Problems:**
- ❌ No total count → can't show "Page X of Y"
- ❌ `hasMore` is heuristic → can be wrong if exactly pageSize results
- ❌ `activeCount` has different meanings in different endpoints
- ❌ Inconsistent with rest of API

---

#### After (Page-based)

**Response Structure:**
```json
{
  "success": true,
  "data": {
    "banners": [...],
    "active_count": 25,
    "pagination": {
      "current_page": 1,
      "page_size": 10,
      "total_items": 100,
      "total_pages": 10
    }
  }
}
```

**Client Logic:**
```javascript
const response = await fetch(`/api/v1/mobile/banners/paginated?page=1&size=10`);
const data = response.data;

// Display banners
displayBanners(data.banners);

// Show pagination UI
showPaginationControls({
  currentPage: data.pagination.current_page,
  totalPages: data.pagination.total_pages,
  hasNext: data.pagination.current_page < data.pagination.total_pages,
  hasPrev: data.pagination.current_page > 1
});
```

**Benefits:**
- ✅ Exact total count → can show "Page X of Y"
- ✅ Consistent `activeCount` → always total active banners
- ✅ Can jump to any page (not just next)
- ✅ Matches rest of API

---

### 6.2. Repository Changes

#### AdminBannerRepository (interface)

**Add method:**
```java
/**
 * Count total banners (active and inactive).
 * @return Total banner count
 */
Mono<Long> count();
```

#### R2dbcAdminBannerRepository (implementation)

**Implementation:**
```java
@Override
public Mono<Long> count() {
    return databaseClient
        .sql("SELECT COUNT(*) FROM banners")
        .map(row -> row.get(0, Long.class))
        .first()
        .defaultIfEmpty(0L);
}
```

**Already exists:**
- `Mono<Long> countActiveBanners()` ✅
- `Mono<Long> countByType(BannerType type)` ✅
- `Mono<Long> countBySpecification(Specification<Banner> spec)` ✅

---

### 6.3. Use Case Changes

#### GetBannersWithPaginationUseCase

**Before:**
```java
public Mono<BannerListResponse> execute(BannerPaginationQuery query) {
    PageRequest pageRequest = PageRequest.of(
        query.getPage() - 1,
        query.getSize(),
        createSort(query)
    );

    return bannerRepository.findAll(pageRequest)
        .map(bannerResponseMapper::toResponse)
        .collectList()
        .zipWith(bannerRepository.countActiveBanners())
        .map(tuple -> {
            List<BannerResponse> banners = tuple.getT1();
            Long activeCount = tuple.getT2();
            Boolean hasMore = banners.size() == query.getSize();

            return new BannerListResponse(
                banners,
                banners.size(),  // totalCount = current page size
                activeCount,
                hasMore
            );
        });
}
```

**After:**
```java
public Mono<BannerList> execute(BannerPaginationQuery query) {
    PageRequest pageRequest = PageRequest.of(
        query.getPage() - 1,
        query.getSize(),
        createSort(query)
    );

    return bannerRepository.findAll(pageRequest)
        .map(bannerResponseMapper::toResponse)
        .collectList()
        .zipWhen(banners -> bannerRepository.count())              // Total count
        .zipWith(bannerRepository.countActiveBanners())             // Active count
        .map(tuple -> {
            List<BannerResponse> banners = tuple.getT1().getT1();
            Long totalCount = tuple.getT1().getT2();
            Long activeCount = tuple.getT2();

            return BannerList.of(
                banners,
                activeCount,
                query.getPage(),
                query.getSize(),
                totalCount
            );
        });
}
```

**Changes:**
- ✅ Return type: `BannerListResponse` → `BannerList`
- ✅ Add `count()` call for total items
- ✅ Remove `hasMore` calculation
- ✅ Use `BannerList.of()` factory method

---

#### SearchBannersUseCase

**Before:**
```java
public Mono<BannerListResponse> execute(SearchBannersQuery query) {
    Specification<Banner> spec = buildSpecification(query);
    PageRequest pageRequest = PageRequest.of(/* ... */);

    return bannerRepository.findBySpecification(spec, pageRequest)
        .map(bannerResponseMapper::toResponse)
        .collectList()
        .zipWith(bannerRepository.countBySpecification(spec))
        .map(tuple -> {
            List<BannerResponse> banners = tuple.getT1();
            Long totalMatching = tuple.getT2();

            // ❌ Bug: using activeCount for total search results
            return new BannerListResponse(banners, totalMatching);
        });
}
```

**After:**
```java
public Mono<BannerList> execute(SearchBannersQuery query) {
    Specification<Banner> spec = buildSpecification(query);
    PageRequest pageRequest = PageRequest.of(/* ... */);

    return bannerRepository.findBySpecification(spec, pageRequest)
        .map(bannerResponseMapper::toResponse)
        .collectList()
        .zipWhen(banners -> bannerRepository.countBySpecification(spec))  // Total matching
        .zipWith(bannerRepository.countActiveBanners())                    // Active count
        .map(tuple -> {
            List<BannerResponse> banners = tuple.getT1().getT1();
            Long totalMatching = tuple.getT1().getT2();
            Long activeCount = tuple.getT2();

            return BannerList.of(
                banners,
                activeCount,      // ✅ Now semantically correct
                query.getPage(),
                query.getSize(),
                totalMatching
            );
        });
}
```

**Changes:**
- ✅ Fix semantic bug: `activeCount` now represents total active, not search results
- ✅ Search results count goes to `pagination.totalItems`
- ✅ Use `BannerList`

---

#### GetBannersWithPaginationByTypeUseCase (Client)

**Before:**
```java
public Mono<BannerListResponse> execute(BannerPaginationQuery query, BannerType type) {
    PageRequest pageRequest = PageRequest.of(/* ... */);

    return clientBannerRepository
        .findActiveBannersByTypeWithPagination(type, pageRequest)
        .map(bannerResponseMapper::toResponse)
        .collectList()
        .map(banners -> {
            Boolean hasMore = banners.size() == query.getSize();
            Long pageSize = (long) banners.size();  // ❌ Bug

            return new BannerListResponse(
                banners,
                pageSize,     // ❌ activeCount = current page size!
                hasMore
            );
        });
}
```

**After:**
```java
public Mono<BannerList> execute(BannerPaginationQuery query, BannerType type) {
    PageRequest pageRequest = PageRequest.of(/* ... */);

    return clientBannerRepository
        .findActiveBannersByTypeWithPagination(type, pageRequest)
        .map(bannerResponseMapper::toResponse)
        .collectList()
        .zipWhen(banners -> clientBannerRepository.countByType(type))  // Total of type
        .zipWith(clientBannerRepository.countActiveBanners())           // Total active
        .map(tuple -> {
            List<BannerResponse> banners = tuple.getT1().getT1();
            Long totalOfType = tuple.getT1().getT2();
            Long totalActive = tuple.getT2();

            return BannerList.of(
                banners,
                totalActive,      // ✅ Total active banners
                query.getPage(),
                query.getSize(),
                totalOfType       // ✅ Total banners of this type
            );
        });
}
```

**Changes:**
- ✅ Fix bug: `activeCount` was page size, now total active
- ✅ Add `countByType()` for accurate pagination
- ✅ Remove `hasMore`

---

### 6.4. Controller Changes

#### AdminBannerController

**Before:**
```java
@RestController
@RequestMapping("/api/v1/admin/banners")
public class AdminBannerController extends BaseController {

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getAllBanners(
        @RequestParam(required = false) Boolean active,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam(defaultValue = "display_order") String sort,
        @RequestParam(defaultValue = "asc") String order
    ) {
        // If default params → GetAllBannersUseCase
        if (isDefaultParams(page, size, sort, order)) {
            return getAllBannersUseCase.execute(active)
                .flatMap(this::ok);
        }

        // Otherwise → GetBannersWithPaginationUseCase
        BannerPaginationQuery query = BannerPaginationQuery.builder()
            .page(page)
            .size(size)
            .sortField(sort)
            .sortOrder(order)
            .activeOnly(active)
            .build();

        return getBannersWithPaginationUseCase.execute(query)
            .flatMap(this::ok);
    }
}
```

**After:**
```java
@RestController
@RequestMapping("/api/v1/admin/banners")
public class AdminBannerController extends BasePaginatedController {

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<BannerList>>> getAllBanners(
        @RequestParam(required = false) Boolean active,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,  // Use DEFAULT_SIZE
        @RequestParam(defaultValue = "display_order") String sort,
        @RequestParam(defaultValue = "asc") String order
    ) {
        // Validate pagination params
        validatePagination(page, size);

        BannerPaginationQuery query = BannerPaginationQuery.builder()
            .page(page)
            .size(size)
            .sortField(sort)
            .sortOrder(order)
            .activeOnly(active)
            .build();

        return getBannersWithPaginationUseCase.execute(query)
            .flatMap(this::okPaginated);
    }
}
```

**Changes:**
- ✅ Extends `BasePaginatedController`
- ✅ Use `DEFAULT_SIZE` constant
- ✅ Add `validatePagination()`
- ✅ Use `okPaginated()` method
- ✅ Remove branching logic (always use pagination use case)

---

#### MobileBannerApiController

**Before:**
```java
@RestController
@RequestMapping("/api/v1/mobile/banners")
public class MobileBannerApiController extends BaseMobileController {

    @GetMapping("/paginated")
    public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getBannersPaginated(
        @RequestParam(defaultValue = "0") int page,    // ❌ 0-indexed
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "displayOrder") String sortField,
        @RequestParam(defaultValue = "asc") String sortOrder,
        @RequestParam(defaultValue = "main") String type
    ) {
        int pageNumber = page + 1;  // Manual conversion

        BannerPaginationQuery query = BannerPaginationQuery.builder()
            .page(pageNumber)
            .size(size)
            .sortField(sortField)
            .sortOrder(sortOrder)
            .build();

        BannerType bannerType = BannerType.valueOf(type.toUpperCase());

        return getBannersWithPaginationByTypeUseCase.execute(query, bannerType)
            .flatMap(this::ok);
    }
}
```

**After:**
```java
@RestController
@RequestMapping("/api/v1/mobile/banners")
public class MobileBannerApiController extends BaseMobileController {

    @GetMapping("/paginated")
    @Operation(
        summary = "Get paginated banners by type",
        description = "⚠️ BREAKING CHANGE: page parameter is now 1-indexed (was 0-indexed)"
    )
    public Mono<ResponseEntity<ApiResponse<BannerList>>> getBannersPaginated(
        @RequestParam(defaultValue = "1") int page,    // ✅ 1-indexed
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "displayOrder") String sortField,
        @RequestParam(defaultValue = "asc") String sortOrder,
        @RequestParam(defaultValue = "MAIN") String type
    ) {
        // Validate pagination
        validatePagination(page, size);

        BannerPaginationQuery query = BannerPaginationQuery.builder()
            .page(page)  // Direct use, no conversion
            .size(size)
            .sortField(sortField)
            .sortOrder(sortOrder)
            .build();

        BannerType bannerType = BannerType.valueOf(type.toUpperCase());

        return getBannersWithPaginationByTypeUseCase.execute(query, bannerType)
            .flatMap(this::okPaginated);
    }
}
```

**Changes:**
- ✅ Change default from `0` to `1`
- ✅ Remove manual conversion
- ✅ Add `validatePagination()`
- ✅ Use `okPaginated()`
- ✅ Add Swagger annotation about breaking change

---

### 6.5. Breaking Changes для клиентов

#### Mobile API Breaking Change

**Endpoint:** `GET /api/v1/mobile/banners/paginated`

**Change:** Page parameter indexing

**Before:**
```
GET /api/v1/mobile/banners/paginated?page=0&size=10  // First page
GET /api/v1/mobile/banners/paginated?page=1&size=10  // Second page
```

**After:**
```
GET /api/v1/mobile/banners/paginated?page=1&size=10  // First page
GET /api/v1/mobile/banners/paginated?page=2&size=10  // Second page
```

**Migration Guide for Clients:**
```javascript
// Before
const page = 0;
fetch(`/api/v1/mobile/banners/paginated?page=${page}`);

// After
const page = 1;
fetch(`/api/v1/mobile/banners/paginated?page=${page}`);
```

---

#### Response Structure Change

**Before:**
```json
{
  "success": true,
  "data": {
    "banners": [...],
    "total_count": 10,
    "active_count": 25,
    "has_more": true
  },
  "timestamp": "2025-11-02T12:00:00"
}
```

**After:**
```json
{
  "success": true,
  "data": {
    "banners": [...],
    "active_count": 25,
    "pagination": {
      "current_page": 1,
      "page_size": 10,
      "total_items": 100,
      "total_pages": 10
    }
  },
  "timestamp": "2025-11-02T12:00:00"
}
```

**Migration:**
```javascript
// Before
const hasMore = response.data.has_more;
const currentPageSize = response.data.total_count;

// After
const hasMore = response.data.pagination.current_page < response.data.pagination.total_pages;
const currentPageSize = response.data.banners.length;
const totalPages = response.data.pagination.total_pages;
const totalItems = response.data.pagination.total_items;
```

---

## 7. План тестирования

### 7.1. Unit Tests

#### PagedList Tests
```java
@Test
void pagedList_shouldImplementDefaultMethods() {
    BannerList list = BannerList.of(
        List.of(new BannerResponse(/* ... */)),
        10L,
        1,
        20,
        100L
    );

    assertThat(list.size()).isEqualTo(1);
    assertThat(list.hasContent()).isTrue();
    assertThat(list.isEmpty()).isFalse();
}
```

#### BasePaginatedController Tests
```java
@Test
void validatePagination_shouldThrowForInvalidPage() {
    assertThrows(IllegalArgumentException.class,
        () -> controller.validatePagination(0, 20));
}

@Test
void createPageRequest_shouldConvertTo0Based() {
    Pageable pageable = controller.createPageRequest(1, 20, Sort.unsorted());
    assertThat(pageable.getPageNumber()).isEqualTo(0);
}
```

#### BannerList Tests
```java
@Test
void bannerList_shouldImplementPagedList() {
    // Test factory method, immutability, interface compliance
}
```

---

### 7.2. Integration Tests

#### Banner Pagination Integration Test
```java
@SpringBootTest
@AutoConfigureWebTestClient
class BannerPaginationIntegrationTest {

    @Autowired
    WebTestClient webTestClient;

    @Test
    void getBannersPaginated_shouldReturnCorrectStructure() {
        webTestClient.get()
            .uri("/api/v1/admin/banners?page=1&size=10")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data.banners").isArray()
            .jsonPath("$.data.active_count").isNumber()
            .jsonPath("$.data.pagination.current_page").isEqualTo(1)
            .jsonPath("$.data.pagination.page_size").isEqualTo(10)
            .jsonPath("$.data.pagination.total_items").isNumber()
            .jsonPath("$.data.pagination.total_pages").isNumber();
    }

    @Test
    void getBannersPaginated_shouldCalculateCorrectTotalPages() {
        // Insert 25 test banners
        // Request page 1, size 10
        // Assert total_pages = 3
    }

    @Test
    void getBannersPaginated_shouldHandleLastPage() {
        // Insert 25 test banners
        // Request page 3, size 10
        // Assert banners.length = 5
        // Assert current_page = 3, total_pages = 3
    }
}
```

---

### 7.3. Architecture Tests

```java
@ArchTest
static final ArchRule bannersUseBannerList =
    methods()
        .that().areDeclaredInClassesThat()
            .resideInAPackage("..banner.application.usecase..")
        .and().haveNameContaining("Pagination")
        .should().haveRawReturnType(assignableTo(Mono.class));
```

---

### 7.4. API Contract Tests

**Цель:** Убедиться, что API contracts не нарушены

```java
@Test
void adminBannersEndpoint_shouldHaveCorrectSchema() {
    webTestClient.get()
        .uri("/api/v1/admin/banners?page=1&size=10")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .consumeWith(response -> {
            DocumentContext json = JsonPath.parse(response.getResponseBody());

            // Required fields
            assertThat(json.read("$.data.banners", List.class)).isNotNull();
            assertThat(json.read("$.data.active_count", Long.class)).isNotNull();
            assertThat(json.read("$.data.pagination", Map.class)).isNotNull();

            // Pagination fields
            assertThat(json.read("$.data.pagination.current_page", Integer.class)).isNotNull();
            assertThat(json.read("$.data.pagination.page_size", Integer.class)).isNotNull();
            assertThat(json.read("$.data.pagination.total_items", Long.class)).isNotNull();
            assertThat(json.read("$.data.pagination.total_pages", Integer.class)).isNotNull();

            // Should NOT have hasMore
            assertThatThrownBy(() -> json.read("$.data.has_more"))
                .isInstanceOf(PathNotFoundException.class);
        });
}
```

---

## 8. Чеклист внедрения

### Pre-Implementation
- [ ] Review и approval плана от team lead
- [ ] Создать feature branch: `feature/pagination-refactoring`
- [ ] Setup tracking issue в project management tool

### Phase 1: Infrastructure (Week 1)
- [ ] Создать `PagedList` interface
- [ ] Создать `BasePaginatedController`
- [ ] Создать `PagedResponseMapper`
- [ ] Написать unit tests
- [ ] Code review
- [ ] Merge to main

### Phase 2: Existing DTOs (Week 1-2)
- [ ] Обновить `RouteList`
- [ ] Обновить `StopList`
- [ ] Обновить `AdminList`
- [ ] Обновить `CityList` (добавить pagination)
- [ ] Обновить unit tests
- [ ] Запустить integration tests
- [ ] Code review
- [ ] Merge to main

### Phase 3: Banner Module (Week 2)
- [ ] Создать `BannerList`
- [ ] Обновить `GetBannersWithPaginationUseCase`
- [ ] Обновить `SearchBannersUseCase`
- [ ] Обновить `GetBannersWithPaginationByTypeUseCase`
- [ ] Обновить `AdminBannerController`
- [ ] Обновить `MobileBannerApiController`
- [ ] Удалить `BannerListResponse`
- [ ] Написать migration tests
- [ ] Code review
- [ ] ⚠️ Coordinate с mobile team о breaking change
- [ ] Merge to main

### Phase 4: Controllers (Week 3)
- [ ] Обновить `AdminRouteController`
- [ ] Обновить `AdminStopController`
- [ ] Обновить `AdminUserController`
- [ ] Обновить `MobileRouteApiController`
- [ ] Обновить `MobileStopApiController`
- [ ] Запустить все integration tests
- [ ] Code review
- [ ] Merge to main

### Phase 5: Cleanup (Week 3)
- [ ] Удалить `PagedResult.java`
- [ ] Обновить documentation
- [ ] Code review
- [ ] Merge to main

### Phase 6: Testing & Documentation (Week 3-4)
- [ ] Написать architecture tests
- [ ] Написать comprehensive integration tests
- [ ] Написать `PAGINATION_BEST_PRACTICES.md`
- [ ] Написать `API_MIGRATION_GUIDE.md`
- [ ] Обновить `CLAUDE.md`
- [ ] Обновить `README.md`
- [ ] Обновить Swagger/OpenAPI documentation
- [ ] Code review
- [ ] Merge to main

### Post-Implementation
- [ ] Deploy to staging
- [ ] Smoke tests на staging
- [ ] Notify mobile/frontend teams о changes
- [ ] Monitor production после deployment
- [ ] Закрыть tracking issue

---

## 9. Риски и Mitigation

### Риск 1: Breaking Changes для Mobile Clients

**Описание:** Mobile API меняет page indexing с 0-based на 1-based

**Вероятность:** High
**Влияние:** High

**Mitigation:**
1. Создать temporary endpoint с версионированием:
   - `/api/v1/mobile/banners/paginated` (новая версия, 1-based)
   - `/api/v1/mobile/banners/paginated-legacy` (старая версия, 0-based)
2. Deprecated старый endpoint с warning
3. Дать mobile team 2 недели на миграцию
4. После миграции удалить legacy endpoint

---

### Риск 2: Performance Regression

**Описание:** Добавление `count()` запросов может замедлить API

**Вероятность:** Medium
**Влияние:** Medium

**Mitigation:**
1. Оптимизировать `count()` queries (использовать indexes)
2. Кэшировать результаты count для медленно меняющихся данных
3. Мониторить query performance после deployment
4. При необходимости добавить Redis caching для count results

---

### Риск 3: Semantic Bug в Existing Code

**Описание:** `activeCount` используется неправильно в некоторых местах

**Вероятность:** Low
**Влияние:** Medium

**Mitigation:**
1. Тщательно проверить все use cases
2. Написать tests для semantic correctness
3. Code review с фокусом на business logic

---

### Риск 4: Test Coverage Gaps

**Описание:** Могут быть пропущены edge cases

**Вероятность:** Medium
**Влияние:** Medium

**Mitigation:**
1. Comprehensive test plan (unit + integration + architecture)
2. Test на boundary conditions (page 1, last page, empty results)
3. Load testing для pagination endpoints
4. Ручное тестирование critical flows

---

## 10. Оценка трудозатрат

| Фаза | Задачи | Оценка (часы) |
|------|--------|---------------|
| Phase 1 | Infrastructure | 9 |
| Phase 2 | Existing DTOs | 10 |
| Phase 3 | Banner Module | 18 |
| Phase 4 | Controllers | 10 |
| Phase 5 | Cleanup | 1.5 |
| Phase 6 | Testing & Docs | 12 |
| **Total** | | **60.5 часов** |

**В рабочих днях:** ~8 дней (assuming 7.5 hour workdays)

**Рекомендуемый timeline:** 3-4 недели (с учетом code reviews, testing, coordination)

---

## 11. Success Criteria

### Обязательные критерии (Must Have)

✅ **Архитектура:**
- [ ] Все List DTOs implements `PagedList<T>`
- [ ] Все paginated controllers extends `BasePaginatedController`
- [ ] `PagedResult.java` удален
- [ ] Banner module использует page-based pagination

✅ **Функциональность:**
- [ ] Все pagination endpoints работают корректно
- [ ] Total count calculation точный
- [ ] Semantic correctness для `activeCount`
- [ ] Validation работает на всех endpoints

✅ **Качество:**
- [ ] Test coverage >= 85%
- [ ] Все architecture tests проходят
- [ ] No regression в существующих endpoints
- [ ] Performance не ухудшилась (< 10% slowdown)

✅ **Документация:**
- [ ] Best practices guide написан
- [ ] API migration guide написан
- [ ] CLAUDE.md обновлен
- [ ] Swagger docs обновлен

---

### Желаемые критерии (Nice to Have)

🎯 **Code Quality:**
- [ ] Sonarqube quality gate passed
- [ ] No new code smells
- [ ] Technical debt ratio improved

🎯 **Performance:**
- [ ] Response times улучшились (caching)
- [ ] Database query count reduced

🎯 **Developer Experience:**
- [ ] Easier to add new paginated endpoints
- [ ] Less boilerplate code
- [ ] Better error messages

---

## 12. Rollback Plan

### Если возникли критические проблемы

**Triggers for rollback:**
- Production errors > 5%
- Performance degradation > 20%
- Critical bug в pagination logic

**Rollback steps:**
1. Revert deploy (rollback к предыдущей версии)
2. Restore `BannerListResponse` (temporary)
3. Re-enable legacy mobile endpoint
4. Fix issues offline
5. Re-deploy с fixes

**Prevention:**
- Thorough testing before production
- Staged rollout (staging → production)
- Feature flags для новых endpoints

---

## Заключение

Этот план предоставляет детальный roadmap для рефакторинга pagination в проекте Bus Route Backend. Ключевые улучшения включают:

1. **Унификация:** Все модули используют единый `PagedList<T>` interface
2. **Упрощение:** `BasePaginatedController` уменьшает boilerplate
3. **Консистентность:** Banner module переходит на page-based pagination
4. **Type Safety:** Sealed interface обеспечивает compile-time проверки
5. **Качество:** Comprehensive testing и documentation

**Next Steps:**
1. Review и approval этого плана
2. Создать tracking issues
3. Начать Phase 1 implementation

---

**Автор:** Claude Code Analysis
**Дата:** 2 ноября 2025
**Версия документа:** 1.0
