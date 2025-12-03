# Отчет: Реализация поиска остановок в Admin API

## Анализ текущего состояния

### 1. Frontend ожидания
Frontend отправляет параметр `search` в запросе:
```
GET /api/v1/admin/stops?page=1&size=100&search=текст_поиска
```

### 2. Текущая реализация Backend

#### AdminStopController.java (строки 52-70)
```java
@GetMapping
public Mono<ResponseEntity<ApiResponse<BusStopListResponse>>> getAllStops(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "name") String sort,
        @RequestParam(defaultValue = "asc") String order,
        @RequestParam(required = false) Boolean active) {
    // ❌ Параметр search НЕ принимается
}
```

**Проблема**: Контроллер не принимает параметр `search`.

#### GetAllStopPaginationQuery.java
```java
public record GetAllStopPaginationQuery(
        Integer page,
        Integer size,
        String sortField,
        String sortOrder,
        Boolean isActivate
) {
    // ❌ Нет поля для searchQuery
}
```

**Проблема**: Query DTO не содержит поле для поискового запроса.

#### GetAllBusStopsUseCase.java (строки 44-82)
```java
private Mono<StopList> processInternal(GetAllStopPaginationQuery query) {
    Pageable pageable = createPageable(query);

    return busStopRepository.findAll(pageable)  // ❌ Простой findAll без фильтрации
            .collectList()
            .zipWith(busStopRepository.countActiveStops())
            .zipWith(busStopRepository.count())
            // ...
}
```

**Проблема**: UseCase всегда вызывает `findAll` без учета поискового запроса.

### 3. Существующие возможности Repository

#### BusStopRepository уже имеет метод поиска!

**R2dbcBusStopRepository.java (строки 168-191)**:
```java
@Override
public Flux<BusStop> searchByName(String query, Integer limit) {
    String sql = """
        SELECT * FROM bus_stops
        WHERE (stop_name ILIKE :query
           OR name_en ILIKE :query
           OR name_tm ILIKE :query)
        ORDER BY
            CASE
                WHEN stop_name ILIKE :exactQuery THEN 1
                WHEN name_en ILIKE :exactQuery THEN 2
                WHEN name_tm ILIKE :exactQuery THEN 3
                ELSE 4
            END,
            stop_name
        LIMIT :limit
        """;
    // ...
}
```

✅ **Отличная реализация**:
- Поиск по всем трем языкам (stop_name, name_en, name_tm)
- Case-insensitive (ILIKE)
- Приоритет точного совпадения в начале строки
- Сортировка по релевантности

**Проблема**: Этот метод НЕ используется в AdminStopController!

---

## Рекомендуемая реализация

### Шаг 1: Обновить GetAllStopPaginationQuery

**Файл**: `transport/application/dto/stop/GetAllStopPaginationQuery.java`

```java
package biz.ugur.busroutebackend.transport.application.dto.stop;

public record GetAllStopPaginationQuery(
        Integer page,
        Integer size,
        String sortField,
        String sortOrder,
        Boolean isActivate,
        String searchQuery  // ➕ Добавить поле
) {
    public static GetAllStopPaginationQuery fromParams(
            Integer page,
            Integer size,
            String sortField,
            String sortOrder,
            Boolean isActivate,
            String searchQuery) {  // ➕ Добавить параметр
        return new GetAllStopPaginationQuery(
                page != null ? page : 1,
                size != null ? size : 20,
                sortField != null ? sortField : "stopName",
                sortOrder != null ? sortOrder : "asc",
                isActivate,
                searchQuery  // ➕ Передать значение
        );
    }

    // ➕ Добавить вспомогательный метод
    public boolean hasSearchQuery() {
        return searchQuery != null && !searchQuery.trim().isEmpty();
    }
}
```

---

### Шаг 2: Обновить AdminStopController

**Файл**: `interfaces/rest/admin/V1/controller/AdminStopController.java`

```java
@GetMapping
public Mono<ResponseEntity<ApiResponse<BusStopListResponse>>> getAllStops(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "name") String sort,
        @RequestParam(defaultValue = "asc") String order,
        @RequestParam(required = false) Boolean active,
        @RequestParam(required = false) String search) {  // ➕ Добавить параметр

    log.debug("AdminStopController.getAllStops called with page={}, size={}, sort={}, order={}, active={}, search={}",
            page, size, sort, order, active, search);  // ➕ Добавить в лог

    validatePagination(page, size);

    return ok(Mono.just(GetAllStopPaginationQuery.fromParams(
                    page, size, camelToSnake(sort), order, active, search))  // ➕ Передать search
            .as(getAllBusStopsUseCase::execute)
            .map(BusStopListResponse::fromResult)
            .doOnSuccess(result -> log.debug("Successfully retrieved {} stops", result.getStops().size()))
            .doOnError(error -> log.error("Error retrieving stops", error)));
}
```

---

### Шаг 3: Добавить новый метод в BusStopRepository

**Файл**: `transport/domain/repository/BusStopRepository.java`

```java
public interface BusStopRepository extends BaseRepository<BusStop, BusStopId> {

    // Существующие методы...

    // ➕ Добавить новый метод с пагинацией
    Flux<BusStop> searchByNameWithPagination(String query, Pageable pageable);

    // ➕ Добавить подсчет для поиска
    Mono<Long> countBySearchQuery(String query);
}
```

---

### Шаг 4: Реализовать новые методы в R2dbcBusStopRepository

**Файл**: `transport/infrastructure/persistence/repository/R2dbcBusStopRepository.java`

```java
@Override
public Flux<BusStop> searchByNameWithPagination(String query, Pageable pageable) {
    String sql = """
        SELECT * FROM bus_stops
        WHERE (stop_name ILIKE :query
           OR name_en ILIKE :query
           OR name_tm ILIKE :query)
        ORDER BY
            CASE
                WHEN stop_name ILIKE :exactQuery THEN 1
                WHEN name_en ILIKE :exactQuery THEN 2
                WHEN name_tm ILIKE :exactQuery THEN 3
                ELSE 4
            END,
            stop_name
        LIMIT :limit OFFSET :offset
        """;

    return databaseClient.sql(sql)
            .bind("query", "%" + query + "%")
            .bind("exactQuery", query + "%")
            .bind("limit", pageable.getPageSize())
            .bind("offset", pageable.getOffset())
            .map(getRowMapper())
            .all();
}

@Override
public Mono<Long> countBySearchQuery(String query) {
    String sql = """
        SELECT COUNT(*) FROM bus_stops
        WHERE (stop_name ILIKE :query
           OR name_en ILIKE :query
           OR name_tm ILIKE :query)
        """;

    return databaseClient.sql(sql)
            .bind("query", "%" + query + "%")
            .map(row -> row.get(0, Long.class))
            .one();
}
```

---

### Шаг 5: Обновить GetAllBusStopsUseCase

**Файл**: `transport/application/usecase/stop/GetAllBusStopsUseCase.java`

```java
private Mono<StopList> processInternal(GetAllStopPaginationQuery query) {
    return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
        log.debug("Getting stops with pagination Correlation - {}: page={}, size={}, sort={}, order={}, active={}, search={}",
                correlationId, query.page(), query.size(), query.sortField(),
                query.sortOrder(), query.isActivate(), query.searchQuery());

        Pageable pageable = createPageable(query);

        // ➕ Проверяем, есть ли поисковый запрос
        if (query.hasSearchQuery()) {
            return searchStopsWithPagination(query, pageable);
        } else {
            return getAllStopsWithPagination(query, pageable);
        }
    });
}

// ➕ Новый метод для поиска
private Mono<StopList> searchStopsWithPagination(GetAllStopPaginationQuery query, Pageable pageable) {
    return busStopRepository.searchByNameWithPagination(query.searchQuery(), pageable)
            .collectList()
            .zipWith(busStopRepository.countBySearchQuery(query.searchQuery()))
            .zipWith(busStopRepository.countActiveStops())
            .map(tuple -> {
                List<BusStop> busStops = tuple.getT1().getT1();
                Long searchResultCount = tuple.getT1().getT2();
                Long activeCount = tuple.getT2();

                List<StopData> stopLists = busStops.stream()
                        .map(StopData::fromDomain)
                        .toList();

                return new StopList(
                        stopLists,
                        activeCount,
                        query.page(),
                        query.size(),
                        searchResultCount  // Используем количество результатов поиска
                );
            }).doOnSuccess(response -> log.debug(
                    "Search found {} stops for query '{}' on page {} of {}",
                    response.getStops().size(),
                    query.searchQuery(),
                    response.getPagination().getCurrentPage(),
                    response.getPagination().getTotalPages()
            ));
}

// Существующий метод для обычного списка
private Mono<StopList> getAllStopsWithPagination(GetAllStopPaginationQuery query, Pageable pageable) {
    return busStopRepository.findAll(pageable)
            .collectList()
            .zipWith(busStopRepository.countActiveStops())
            .zipWith(busStopRepository.count())
            .map(tuple -> {
                List<BusStop> busStops = tuple.getT1().getT1();
                Long activeCount = tuple.getT1().getT2();
                Long totalCount = tuple.getT2();

                List<StopData> stopLists = busStops.stream()
                        .map(StopData::fromDomain)
                        .toList();

                return new StopList(
                        stopLists,
                        activeCount,
                        query.page(),
                        query.size(),
                        totalCount
                );
            }).doOnSuccess(response -> log.debug(
                    "Retrieved {} stops on page {} of {} ({} active, {} total)",
                    response.getStops().size(),
                    response.getPagination().getCurrentPage(),
                    response.getPagination().getTotalPages(),
                    response.getActiveCount(),
                    response.getPagination().getTotalItems()
            ));
}
```

---

## Преимущества реализации

✅ **1. Использование существующего кода**
- Метод `searchByName` уже работает, нужно только добавить пагинацию

✅ **2. Мультиязычный поиск**
- Поиск по stop_name (основное название)
- Поиск по name_ru (русский)
- Поиск по name_tm (туркменский)
- Поиск по name_en (английский)

✅ **3. Case-insensitive поиск**
- Использование ILIKE в PostgreSQL

✅ **4. Релевантная сортировка**
- Точные совпадения в начале строки выше в результатах
- Затем сортировка по имени

✅ **5. Обратная совместимость**
- Если параметр `search` не передан - работает как обычно
- Существующие запросы продолжат работать

✅ **6. Правильная пагинация**
- Корректный подсчет результатов поиска
- Pagination объект с правильными значениями

---

## Порядок реализации

1. ✅ **GetAllStopPaginationQuery** - добавить поле `searchQuery`
2. ✅ **AdminStopController** - добавить параметр `@RequestParam search`
3. ✅ **BusStopRepository** - добавить методы с пагинацией
4. ✅ **R2dbcBusStopRepository** - реализовать методы
5. ✅ **GetAllBusStopsUseCase** - добавить логику поиска

---

## Тестирование

### Запросы для тестирования:

```bash
# Обычный список (без поиска)
curl -X GET "http://localhost:8080/api/v1/admin/stops?page=1&size=25" \
  -H "Authorization: Bearer YOUR_TOKEN"

# Поиск по названию
curl -X GET "http://localhost:8080/api/v1/admin/stops?page=1&size=100&search=центр" \
  -H "Authorization: Bearer YOUR_TOKEN"

# Поиск с пагинацией
curl -X GET "http://localhost:8080/api/v1/admin/stops?page=1&size=10&search=площадь" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Ожидаемое поведение:

1. **Без параметра search**: Возвращает все остановки с пагинацией
2. **С параметром search**: Возвращает только остановки, соответствующие поиску
3. **Пустой search**: Работает как обычный запрос

---

## Альтернативный подход (если нужно учитывать фильтр active)

Если нужно комбинировать поиск с фильтром `active`, используйте Specification pattern:

```java
// В BusStopSpecifications.java
public static Specification<BusStop> searchByNameAndActive(String query, Boolean isActive) {
    return new Specification<BusStop>() {
        @Override
        public SqlCriteria toSqlCriteria() {
            Map<String, Object> params = new HashMap<>();
            StringBuilder where = new StringBuilder("(stop_name ILIKE :query OR name_en ILIKE :query OR name_tm ILIKE :query)");
            params.put("query", "%" + query + "%");

            if (isActive != null) {
                where.append(" AND is_active = :isActive");
                params.put("isActive", isActive);
            }

            return new SqlCriteria(where.toString(), params);
        }
    };
}
```

---

## Заключение

**Текущее состояние**: ❌ Поиск не работает
**После реализации**: ✅ Полнофункциональный мультиязычный поиск с пагинацией

**Время реализации**: ~30-40 минут
**Сложность**: Низкая (код уже наполовину готов)

**Критичность**: Высокая - фронтенд уже использует этот параметр!
