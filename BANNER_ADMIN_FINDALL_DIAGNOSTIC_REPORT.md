# Banner Admin FindAll Diagnostic Report

## Проблема
В админ панель не доходят баннеры при вызове `findAll(Pageable pageable)`.

## Анализ потока данных

### 1. Контроллер: AdminBannerController
**Файл:** `interfaces/rest/admin/V1/controller/AdminBannerController.java:48`

```java
@GetMapping
public Mono<ResponseEntity<ApiResponse<BannerList>>> getAllBanners(
        @RequestParam(required = false, defaultValue = "true") Boolean active,
        @RequestParam(required = false, defaultValue = "1") int page,
        @RequestParam(required = false, defaultValue = "20") int size,
        @RequestParam(required = false, defaultValue = "display_order") String sort,
        @RequestParam(required = false, defaultValue = "asc") String order)
```

**Параметры по умолчанию:**
- `active = true` (запрашиваются только активные баннеры)
- `page = 1`
- `size = 20`
- `sort = display_order`
- `order = asc`

**Важно:** Параметр `active` используется в `BannerPaginationQuery`, но НЕ используется при вызове `findAll(pageable)`.

---

### 2. Use Case: GetBannersWithPaginationUseCase
**Файл:** `banner/application/usecase/admin/GetBannersWithPaginationUseCase.java:54`

```java
return bannerRepository.findAll(pageable)
        .flatMap(bannerResponseMapper::toResponse)
        .collectList()
        .zipWhen(banners -> bannerRepository.count())
        .zipWith(bannerRepository.countActiveBanners())
```

**🔴 КРИТИЧЕСКАЯ ПРОБЛЕМА:**
Use case вызывает `bannerRepository.findAll(pageable)`, который **игнорирует параметр `activeOnly`** из `BannerPaginationQuery`.

---

### 3. Repository: BaseR2dbcRepository.findAll()
**Файл:** `shared/infrastructure/persistence/BaseR2dbcRepository.java:62`

```java
@Override
public Flux<T> findAll(Pageable pageable) {
    String sql = String.format(
            "SELECT * FROM %s %s LIMIT :limit OFFSET :offset",
            tableName,
            getOrderByClause(pageable)
    );

    return databaseClient.sql(sql)
            .bind("limit", pageable.getPageSize())
            .bind("offset", pageable.getOffset())
            .map(getRowMapper())
            .all();
}
```

**SQL запрос:** `SELECT * FROM banners ORDER BY display_order ASC LIMIT 20 OFFSET 0`

**🔴 ПРОБЛЕМА:** Запрос **НЕ фильтрует** по `is_active`, возвращает ВСЕ баннеры.

---

### 4. Доступные методы в AdminBannerRepository

**Файл:** `banner/domain/repository/AdminBannerRepository.java`

Репозиторий имеет методы:
- `findActiveBanners()` - возвращает только активные баннеры с проверкой дат
- `findBySpecification(Specification, Pageable)` - с поддержкой фильтрации
- `findAll(Pageable)` - **НЕ фильтрует** по статусу

**Реализация `findActiveBanners()` в R2dbcAdminBannerRepository.java:20:**
```java
@Override
public Flux<Banner> findActiveBanners() {
    String sql = """
        SELECT * FROM banners
        WHERE is_active = true
        AND (start_date IS NULL OR start_date <= NOW())
        AND (end_date IS NULL OR end_date >= NOW())
        ORDER BY display_order ASC, created_at DESC
        """;

    return databaseClient.sql(sql)
            .map(getRowMapper())
            .all();
}
```

---

## Текущее состояние базы данных

```
 total | active
-------+--------
     5 |      5
```

В базе 5 баннеров, все активны.

---

## Корневая причина проблемы

### Несоответствие логики:

1. **Контроллер** передает параметр `active = true` в `BannerPaginationQuery`
2. **Use Case** получает `query.getActiveOnly()`, но **игнорирует** его при вызове репозитория
3. **Repository** вызывает базовый `findAll(pageable)`, который **не фильтрует** по `is_active`
4. В результате возвращаются **все баннеры** независимо от параметра `active`

### Почему это критично:

- Если в базе есть **неактивные баннеры** (is_active = false), они попадут в результат
- Если есть баннеры с **истекшими датами** (end_date < NOW()), они тоже попадут
- Параметр `activeOnly` в контроллере **бесполезен** - он передается, но нигде не используется

---

## Решения

### Вариант 1: Использовать findActiveBanners() с пагинацией (Рекомендуется)

**Файл:** `GetBannersWithPaginationUseCase.java:54`

**Проблема:** Метод `findActiveBanners()` существует, но **не поддерживает пагинацию**.

**Решение:** Добавить метод `findActiveBanners(Pageable pageable)` в `AdminBannerRepository` и использовать его, когда `activeOnly = true`.

---

### Вариант 2: Использовать Specification Pattern

**Файл:** `GetBannersWithPaginationUseCase.java:54`

Использовать существующий метод `findBySpecification(Specification<Banner> specification, Pageable pageable)` для фильтрации по статусу.

---

### Вариант 3: Добавить метод findAll с фильтром

Создать новый метод в репозитории:
```java
Flux<Banner> findAll(Pageable pageable, Boolean activeOnly);
```

---

## Рекомендации

1. **Срочно:** Исправить логику в `GetBannersWithPaginationUseCase` для учета параметра `activeOnly`
2. Добавить тесты для проверки фильтрации активных/неактивных баннеров
3. Добавить логирование SQL-запросов для отладки
4. Проверить, используется ли аналогичная логика в других модулях (transport, routing и т.д.)

---

## Путь к исправлению

**Минимальное изменение (наименее интрузивное):**

В `GetBannersWithPaginationUseCase.java:47` изменить логику:

```java
private Mono<BannerList> processInternal(BannerPaginationQuery query) {
    return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
        log.debug("Fetching banners with pagination CorrelationId: {} - admin: page={}, size={}, sort={}, order={}, active={}",
                correlationId, query.getPage(), query.getSize(), query.getSortField(), query.getSortOrder(), query.getActiveOnly());

        Pageable pageable = createPageable(query);

        // 🔴 ТЕКУЩАЯ РЕАЛИЗАЦИЯ: игнорирует activeOnly
        // return bannerRepository.findAll(pageable)

        // ✅ ПРАВИЛЬНАЯ РЕАЛИЗАЦИЯ: учитывает activeOnly
        Flux<Banner> bannerFlux = Boolean.TRUE.equals(query.getActiveOnly())
            ? // Нужен метод findActiveBanners(pageable) или использовать specification
            : bannerRepository.findAll(pageable);

        return bannerFlux
                .flatMap(bannerResponseMapper::toResponse)
                .collectList()
                ...
    });
}
```

---

## Статус

**Проблема идентифицирована:**
- Use Case вызывает `findAll(pageable)` вместо фильтрации по `activeOnly`
- Базовый репозиторий не поддерживает фильтрацию по статусу в методе `findAll`
- Параметр `activeOnly` игнорируется на уровне бизнес-логики

**Требуется:**
- Реализация метода с поддержкой фильтрации
- Обновление Use Case для использования правильного метода репозитория
