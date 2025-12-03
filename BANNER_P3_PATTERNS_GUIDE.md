# Руководство по паттернам Specification и Event Sourcing в Banner Context

> **Для новичков**: Этот документ объясняет, что такое Specification Pattern и Event Sourcing, зачем они нужны и как они реализованы в контексте баннеров нашего приложения.

---

## Оглавление

1. [Specification Pattern](#specification-pattern)
   - [Что это такое?](#что-это-specification-pattern)
   - [Зачем это нужно?](#зачем-нужен-specification-pattern)
   - [Как это работает?](#как-работает-specification-pattern)
   - [Примеры использования](#примеры-использования-specification)
2. [Event Sourcing](#event-sourcing)
   - [Что это такое?](#что-это-event-sourcing)
   - [Зачем это нужно?](#зачем-нужен-event-sourcing)
   - [Как это работает?](#как-работает-event-sourcing)
   - [Примеры использования](#примеры-использования-event-sourcing)
3. [Структура файлов](#структура-файлов)
4. [Тестирование](#тестирование)

---

## Specification Pattern

### Что это Specification Pattern?

**Specification Pattern** (паттерн спецификации) — это способ инкапсулировать бизнес-правила и критерии фильтрации в переиспользуемые объекты.

**Простыми словами**: Вместо того чтобы писать множество методов в репозитории типа:
- `findActiveBanners()`
- `findActiveBannersByType()`
- `findActiveBannersByTypeAndPeriod()`
- `findInactiveBannersByType()`
- ...и ещё сотни комбинаций...

Мы создаём **строительные блоки** (спецификации), которые можно комбинировать как конструктор Lego:

```java
// Вместо написания 100 методов репозитория:
Specification<Banner> spec = BannerSpecifications.isActive()
    .and(BannerSpecifications.hasType(BannerType.MAIN))
    .and(BannerSpecifications.isPeriodActive(LocalDateTime.now()));

// Один универсальный метод:
bannerRepository.findBySpecification(spec);
```

### Зачем нужен Specification Pattern?

#### Проблема без Specification Pattern

Представим, что у нас есть разные комбинации фильтров для баннеров:

1. Активные баннеры
2. Активные баннеры типа MAIN
3. Активные баннеры типа MAIN с активным периодом
4. Активные баннеры с названием содержащим "акция"
5. Неактивные баннеры типа POPUP
6. ...

Без Specification Pattern вам придётся написать в репозитории метод **для каждой комбинации**:

```java
// Репозиторий превращается в кошмар:
Flux<Banner> findActiveBanners();
Flux<Banner> findActiveBannersByType(BannerType type);
Flux<Banner> findActiveBannersByTypeWithPeriod(BannerType type);
Flux<Banner> findActiveBannersWithTitleContaining(String text);
Flux<Banner> findInactiveBannersByType(BannerType type);
// ... ещё 50+ методов ...
```

**Проблемы**:
- ❌ Много дублирующегося кода
- ❌ Репозиторий раздувается до огромных размеров
- ❌ Каждая новая комбинация = новый метод
- ❌ Тяжело тестировать каждый метод отдельно

#### Решение с Specification Pattern

С паттерном Specification:

```java
// В репозитории всего 3 метода:
Flux<Banner> findBySpecification(Specification<Banner> spec);
Flux<Banner> findBySpecification(Specification<Banner> spec, Pageable pageable);
Mono<Long> countBySpecification(Specification<Banner> spec);

// А комбинации строятся динамически:
var spec = BannerSpecifications.isActive()
    .and(BannerSpecifications.hasType(BannerType.MAIN))
    .and(BannerSpecifications.titleContains("акция"));

bannerRepository.findBySpecification(spec);
```

**Преимущества**:
- ✅ Репозиторий остаётся маленьким
- ✅ Критерии можно комбинировать как угодно
- ✅ Легко читается: "isActive И hasType И titleContains"
- ✅ Переиспользуемые строительные блоки
- ✅ Легко тестировать каждую спецификацию отдельно

### Как работает Specification Pattern?

#### 1. Базовый интерфейс `Specification<T>`

```java
public interface Specification<T> {

    // Проверка объекта в памяти (in-memory)
    boolean isSatisfiedBy(T candidate);

    // Генерация SQL для базы данных
    SqlCriteria toSqlCriteria();

    // Комбинирование спецификаций
    default Specification<T> and(Specification<T> other) {
        return new AndSpecification<>(this, other);
    }

    default Specification<T> or(Specification<T> other) {
        return new OrSpecification<>(this, other);
    }

    default Specification<T> not() {
        return new NotSpecification<>(this);
    }
}
```

#### 2. Конкретные спецификации

Файл: `BannerSpecifications.java`

```java
public class BannerSpecifications {

    // Спецификация: баннер активен
    public static Specification<Banner> isActive() {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                return Boolean.TRUE.equals(banner.getIsActive());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("is_active = :isActive", "isActive", true);
            }
        };
    }

    // Спецификация: баннер определённого типа
    public static Specification<Banner> hasType(BannerType type) {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                return banner.getType() == type;
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("type = :type", "type", type.getValue());
            }
        };
    }

    // Спецификация: период баннера активен
    public static Specification<Banner> isPeriodActive(LocalDateTime now) {
        return new Specification<Banner>() {
            @Override
            public boolean isSatisfiedBy(Banner banner) {
                LocalDateTime start = banner.getPeriod().getStartTime();
                LocalDateTime end = banner.getPeriod().getEndTime();
                return !now.isBefore(start) && !now.isAfter(end);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return new SqlCriteria(
                    "start_date <= :periodNow AND end_date >= :periodNow",
                    Map.of("periodNow", now)
                );
            }
        };
    }
}
```

**Что здесь происходит?**

Каждая спецификация имеет две реализации:

1. **`isSatisfiedBy()`** — проверка объекта в памяти (Java)
   - Используется для unit-тестов
   - Позволяет проверить объект без обращения к базе данных

2. **`toSqlCriteria()`** — генерация SQL WHERE clause
   - Используется репозиторием для построения запроса к базе данных
   - Возвращает строку SQL и параметры для bind

#### 3. Комбинирование спецификаций

```java
// Активные MAIN-баннеры с активным периодом
Specification<Banner> spec = BannerSpecifications.isActive()
    .and(BannerSpecifications.hasType(BannerType.MAIN))
    .and(BannerSpecifications.isPeriodActive(LocalDateTime.now()));

// SQL который будет сгенерирован:
// SELECT * FROM banners
// WHERE (is_active = :isActive)
//   AND (type = :type)
//   AND (start_date <= :periodNow AND end_date >= :periodNow)
```

#### 4. Использование в репозитории

Файл: `BannerBaseRepository.java`

```java
public Flux<Banner> findBySpecification(Specification<Banner> specification) {
    // 1. Конвертируем Specification в SQL
    SqlCriteria criteria = specification.toSqlCriteria();

    // 2. Строим SQL запрос
    String sql = String.format(
        "SELECT * FROM banners WHERE %s ORDER BY display_order ASC",
        criteria.getWhereClause()
    );

    // 3. Выполняем запрос с параметрами
    DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.sql(sql);

    for (Map.Entry<String, Object> entry : criteria.getParameters().entrySet()) {
        executeSpec = bindValue(executeSpec, entry.getKey(), entry.getValue());
    }

    return executeSpec.map(getRowMapper()).all();
}
```

### Примеры использования Specification

#### Пример 1: Простой поиск активных баннеров

```java
Specification<Banner> spec = BannerSpecifications.isActive();

bannerRepository.findBySpecification(spec)
    .subscribe(banner -> System.out.println(banner.getTitle()));

// SQL: SELECT * FROM banners WHERE is_active = true
```

#### Пример 2: Комплексный поиск

```java
// Найти активные MAIN-баннеры с текстом "акция"
// в названии и приоритетом от 1 до 10
Specification<Banner> spec = BannerSpecifications.isActive()
    .and(BannerSpecifications.hasType(BannerType.MAIN))
    .and(BannerSpecifications.titleContains("акция"))
    .and(BannerSpecifications.displayOrderBetween(1, 10));

bannerRepository.findBySpecification(spec)
    .subscribe(banner -> System.out.println(banner.getTitle()));

// SQL: SELECT * FROM banners
// WHERE (is_active = true)
//   AND (type = 'MAIN')
//   AND (LOWER(title) LIKE '%акция%')
//   AND (display_order BETWEEN 1 AND 10)
```

#### Пример 3: Использование OR

```java
// Баннеры типа MAIN или POPUP
Specification<Banner> spec = BannerSpecifications.hasType(BannerType.MAIN)
    .or(BannerSpecifications.hasType(BannerType.POPUP));

// SQL: SELECT * FROM banners
// WHERE (type = 'MAIN') OR (type = 'POPUP')
```

#### Пример 4: Использование NOT

```java
// Неактивные баннеры (NOT активные)
Specification<Banner> spec = BannerSpecifications.isActive().not();

// SQL: SELECT * FROM banners WHERE NOT (is_active = true)
```

#### Пример 5: Готовые комплексные спецификации

```java
// Баннеры готовые к показу клиентам
// (активные + с активным периодом)
Specification<Banner> spec = BannerSpecifications.isReadyForDisplay();

// Баннеры требующие внимания администратора
// (активные + истекают в течение 7 дней)
Specification<Banner> spec = BannerSpecifications.requiresAdminAttention();
```

#### Пример 6: Use Case с динамическими критериями

Файл: `SearchBannersUseCase.java`

```java
@Service
public class SearchBannersUseCase {

    public Mono<BannerListResponse> execute(SearchBannersQuery query) {
        // Динамическое построение спецификации
        Specification<Banner> spec = buildSpecification(query);

        return bannerRepository.findBySpecification(spec, pageable)
            .flatMap(bannerResponseMapper::toResponse)
            .collectList()
            .zipWith(bannerRepository.countBySpecification(spec))
            .map(tuple -> new BannerListResponse(tuple.getT1(), tuple.getT2()));
    }

    private Specification<Banner> buildSpecification(SearchBannersQuery query) {
        Specification<Banner> spec = alwaysTrue(); // базовая спецификация

        if (query.getType() != null) {
            spec = spec.and(BannerSpecifications.hasType(query.getType()));
        }

        if (Boolean.TRUE.equals(query.getIsActive())) {
            spec = spec.and(BannerSpecifications.isActive());
        }

        if (query.getTitleSearch() != null) {
            spec = spec.and(BannerSpecifications.titleContains(query.getTitleSearch()));
        }

        return spec;
    }
}
```

**Что здесь происходит?**

Use Case **динамически** строит Specification на основе того, какие фильтры предоставил пользователь:

- Если указан `type` → добавляется `hasType()`
- Если указан `isActive` → добавляется `isActive()`
- Если указан `titleSearch` → добавляется `titleContains()`

Все критерии объединяются через `.and()`, создавая одну комплексную спецификацию.

---

## Event Sourcing

### Что это Event Sourcing?

**Event Sourcing** — это паттерн, где все изменения состояния приложения сохраняются как последовательность **событий** (events).

**Простыми словами**: Вместо того чтобы хранить только текущее состояние объекта, мы храним **всю историю изменений** этого объекта.

#### Пример без Event Sourcing

Традиционная база данных (CRUD):

```
Таблица: banners
+------+-------+--------+
| id   | title | active |
+------+-------+--------+
| 123  | Акция | true   |
+------+-------+--------+
```

Если изменить название:
```sql
UPDATE banners SET title = 'Новая акция' WHERE id = 123;
```

**Результат**: старое название "Акция" **потеряно навсегда**.

Мы не знаем:
- ❌ Какое было старое название?
- ❌ Кто изменил?
- ❌ Когда изменили?
- ❌ Почему изменили?

#### Пример с Event Sourcing

Event Store (лог событий):

```
Таблица: banner_events
+--------+-----------+-----------------------+------------------------+
| banner | event_type| occurred_at           | payload                |
+--------+-----------+-----------------------+------------------------+
| 123    | Created   | 2025-01-01 10:00:00   | {title: "Акция", ...}  |
| 123    | Updated   | 2025-01-10 14:30:00   | {title: "Новая акция"} |
| 123    | Activated | 2025-01-15 09:00:00   | {}                     |
+--------+-----------+-----------------------+------------------------+
```

**Результат**: вся история сохранена!

Мы можем:
- ✅ Восстановить состояние на любой момент времени
- ✅ Узнать, что было название "Акция"
- ✅ Узнать, когда изменили (2025-01-10 14:30)
- ✅ Проследить все изменения баннера
- ✅ Аудит: кто и что менял
- ✅ Аналитика: какие баннеры чаще редактируются

### Зачем нужен Event Sourcing?

#### 1. **Полный аудит**

Каждое изменение записывается как событие. Вы **всегда** можете ответить на вопросы:
- Кто создал баннер?
- Кто изменил название?
- Кто деактивировал баннер?
- Когда это произошло?

**Пример**: Руководитель спрашивает: "Почему баннер неактивен?"

```java
// Получаем все события баннера
eventStore.findByBannerId("123")
    .subscribe(event -> {
        // BannerCreatedEvent: создан 01.01.2025
        // BannerActivatedEvent: активирован 05.01.2025
        // BannerDeactivatedEvent: деактивирован 15.01.2025 (AHA!)
    });
```

#### 2. **Time Travel** (путешествие во времени)

Вы можете восстановить состояние объекта **на любой момент времени**.

**Пример**: "Какое название было у баннера 10 января?"

```java
eventStore.findByBannerIdBefore("123", "2025-01-10")
    .subscribe(events -> {
        Banner banner = Banner.replay(events); // восстановление из событий
        System.out.println(banner.getTitle()); // "Акция"
    });
```

#### 3. **Отладка и анализ**

Когда что-то сломалось, вы видите **точную последовательность событий**, которая привела к проблеме.

**Пример**: Баннер отображается некорректно.

```java
eventStore.findByBannerId("123")
    .subscribe(event -> System.out.println(event));

// Output:
// 10:00 - BannerCreatedEvent (title: "Акция", type: MAIN)
// 11:00 - BannerUpdatedEvent (changes: {type: POPUP})  <- AHA! Тип изменили!
// 12:00 - BannerActivatedEvent
```

#### 4. **Аналитика**

События можно использовать для построения отчётов и аналитики.

**Примеры аналитики**:
- Сколько баннеров создаётся в день?
- Какие баннеры чаще всего редактируются?
- Сколько раз баннер активировали/деактивировали?
- Средняя продолжительность жизни баннера?

```java
// Сколько баннеров создано в январе?
eventStore.findByEventType("BannerCreatedEvent")
    .filter(event -> event.getOccurredAt().isAfter(jan1)
                  && event.getOccurredAt().isBefore(feb1))
    .count()
    .subscribe(count -> System.out.println("Created: " + count));
```

### Как работает Event Sourcing?

#### 1. Доменные события

Файл: `BannerCreatedEvent.java`

```java
public class BannerCreatedEvent extends BannerDomainEvent {
    private final String title;
    private final String type;
    private final String imageUrl;
    private final String targetUrl;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final Integer displayOrder;
    private final String content;

    // Конструктор создаёт событие с текущим временем
    public BannerCreatedEvent(String bannerId, String title, ...) {
        super(bannerId); // устанавливает eventId и occurredAt
        this.title = title;
        // ...
    }
}
```

**Типы событий**:
- `BannerCreatedEvent` — баннер создан
- `BannerUpdatedEvent` — баннер обновлён (содержит только изменения)
- `BannerActivatedEvent` — баннер активирован
- `BannerDeactivatedEvent` — баннер деактивирован
- `BannerDeletedEvent` — баннер удалён

#### 2. Регистрация событий в Aggregate Root

Файл: `Banner.java`

```java
public class Banner extends AggregateRoot<Banner, BannerId> {

    public static Banner create(...) {
        Banner banner = builder()
            .id(BannerId.generate())
            .title(title)
            .isActive(true)
            .build();

        // Регистрация события создания
        banner.registerEvent(new BannerCreatedEvent(
            banner.id.getValue(),
            banner.title.getValue(),
            // ...
        ));

        return banner;
    }

    public void deactivate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            this.isActive = false;

            // Регистрация события деактивации
            registerEvent(new BannerDeactivatedEvent(this.id.getValue()));
        }
    }
}
```

**Что происходит?**

Когда мы меняем состояние баннера (создание, обновление, деактивация), мы:
1. Изменяем поля объекта (state)
2. **Регистрируем событие** через `registerEvent()`

События накапливаются в `AggregateRoot` и затем сохраняются в Event Store.

#### 3. Event Store (хранилище событий)

Файл: `BannerEventStore.java` (интерфейс)

```java
public interface BannerEventStore {

    // Сохранение события
    Mono<BannerDomainEvent> save(BannerDomainEvent event);

    // Получение всех событий баннера
    Flux<BannerDomainEvent> findByBannerId(String bannerId);

    // Получение событий после определённого времени
    Flux<BannerDomainEvent> findByBannerIdAfter(String bannerId, Instant since);

    // Получение событий по типу
    Flux<BannerDomainEvent> findByEventType(String eventType);
}
```

#### 4. Реализация Event Store с R2DBC

Файл: `R2dbcBannerEventStore.java`

```java
@Repository
public class R2dbcBannerEventStore implements BannerEventStore {

    @Override
    public Mono<BannerDomainEvent> save(BannerDomainEvent event) {
        String payload = serializeEventPayload(event); // JSON

        String sql = """
            INSERT INTO banner_events
            (event_id, banner_id, event_type, payload, occurred_at, recorded_at)
            VALUES (:eventId, :bannerId, :eventType, :payload::jsonb, :occurredAt, :recordedAt)
            RETURNING *
            """;

        return databaseClient.sql(sql)
            .bind("eventId", UUID.fromString(event.getEventId()))
            .bind("bannerId", UUID.fromString(event.getBannerId()))
            .bind("eventType", event.getEventType())
            .bind("payload", payload)
            .bind("occurredAt", event.getOccurredAt())
            .bind("recordedAt", Instant.now())
            .map(this::mapRowToEvent)
            .one();
    }

    private String serializeEventPayload(BannerDomainEvent event) {
        return switch (event) {
            case BannerCreatedEvent e -> objectMapper.writeValueAsString(Map.of(
                "title", e.getTitle(),
                "type", e.getType(),
                "imageUrl", e.getImageUrl(),
                // ...
            ));
            case BannerUpdatedEvent e -> objectMapper.writeValueAsString(Map.of(
                "changes", e.getChanges()
            ));
            // ...
        };
    }
}
```

**Что происходит?**

1. Событие сериализуется в JSON (`serializeEventPayload`)
2. Сохраняется в таблицу `banner_events` как JSONB
3. Никогда не изменяется и не удаляется (append-only)

#### 5. Таблица banner_events

Миграция: `V18__create_banner_events_table.sql`

```sql
CREATE TABLE banner_events (
    event_id UUID PRIMARY KEY,
    banner_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    metadata JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_banner_events_banner_id ON banner_events(banner_id, occurred_at);
CREATE INDEX idx_banner_events_event_type ON banner_events(event_type, occurred_at);
```

**Структура таблицы**:
- `event_id` — уникальный ID события
- `banner_id` — к какому баннеру относится
- `event_type` — тип события (BannerCreatedEvent, BannerUpdatedEvent, ...)
- `payload` — JSON с данными события
- `occurred_at` — когда событие произошло
- `recorded_at` — когда событие записано в БД

### Примеры использования Event Sourcing

#### Пример 1: Аудит всех изменений баннера

```java
@GetMapping("/admin/banners/{id}/history")
public Mono<List<BannerEvent>> getBannerHistory(@PathVariable String id) {
    return eventStore.findByBannerId(id)
        .map(event -> new BannerEvent(
            event.getEventType(),
            event.getOccurredAt(),
            event.getPayload()
        ))
        .collectList();
}

// Response:
// [
//   { type: "BannerCreatedEvent", occurredAt: "2025-01-01T10:00:00Z", payload: {...} },
//   { type: "BannerUpdatedEvent", occurredAt: "2025-01-10T14:30:00Z", payload: {...} },
//   { type: "BannerActivatedEvent", occurredAt: "2025-01-15T09:00:00Z", payload: {} }
// ]
```

#### Пример 2: Восстановление состояния на момент времени

```java
public Mono<Banner> getBannerStateAt(String bannerId, Instant timestamp) {
    return eventStore.findByBannerIdBefore(bannerId, timestamp)
        .collectList()
        .map(events -> Banner.replay(events)); // восстановление из событий
}

// Какое название было у баннера 10 января?
getBannerStateAt("123", Instant.parse("2025-01-10T00:00:00Z"))
    .subscribe(banner -> System.out.println(banner.getTitle())); // "Акция"
```

#### Пример 3: Аналитика создания баннеров

```java
public Mono<Long> countBannersCreatedInJanuary() {
    Instant jan1 = Instant.parse("2025-01-01T00:00:00Z");
    Instant feb1 = Instant.parse("2025-02-01T00:00:00Z");

    return eventStore.findByEventType("BannerCreatedEvent")
        .filter(event -> event.getOccurredAt().isAfter(jan1)
                      && event.getOccurredAt().isBefore(feb1))
        .count();
}
```

#### Пример 4: Отчёт о самых активных баннерах

```java
// Найти баннеры с наибольшим количеством изменений
public Flux<BannerActivityReport> getMostEditedBanners() {
    return eventStore.findAll()
        .filter(event -> event.getEventType().equals("BannerUpdatedEvent"))
        .groupBy(BannerDomainEvent::getBannerId)
        .flatMap(group -> group.count()
            .map(count -> new BannerActivityReport(group.key(), count)))
        .sort((a, b) -> Long.compare(b.getCount(), a.getCount()))
        .take(10); // топ 10
}
```

---

## Структура файлов

### Specification Pattern

```
banner/
├── domain/
│   └── specification/
│       └── BannerSpecifications.java      # Конкретные спецификации для баннеров
├── application/
│   ├── dto/
│   │   └── SearchBannersQuery.java        # DTO для поиска с критериями
│   └── usecase/
│       └── admin/
│           └── SearchBannersUseCase.java  # Use Case для поиска баннеров
└── infrastructure/
    └── persistence/
        └── repository/
            └── BannerBaseRepository.java  # findBySpecification() имплементация

shared/
└── domain/
    └── specification/
        ├── Specification.java             # Базовый интерфейс
        ├── SqlCriteria.java               # SQL WHERE clause + параметры
        ├── AndSpecification.java          # AND комбинация
        ├── OrSpecification.java           # OR комбинация
        └── NotSpecification.java          # NOT инверсия
```

### Event Sourcing

```
banner/
├── domain/
│   ├── events/
│   │   ├── BannerDomainEvent.java         # Базовое событие
│   │   ├── BannerCreatedEvent.java        # Событие создания
│   │   ├── BannerUpdatedEvent.java        # Событие обновления
│   │   ├── BannerActivatedEvent.java      # Событие активации
│   │   ├── BannerDeactivatedEvent.java    # Событие деактивации
│   │   └── BannerDeletedEvent.java        # Событие удаления
│   ├── model/
│   │   └── Banner.java                    # Aggregate Root (регистрирует события)
│   └── repository/
│       └── BannerEventStore.java          # Интерфейс Event Store
└── infrastructure/
    └── persistence/
        ├── entity/
        │   └── BannerEventEntity.java     # Entity для таблицы banner_events
        └── repository/
            └── R2dbcBannerEventStore.java # R2DBC имплементация Event Store

resources/
└── db/
    └── migration/
        └── V18__create_banner_events_table.sql  # Таблица для событий
```

---

## Тестирование

### Тесты Specification Pattern

Файл: `BannerSpecificationsTest.java`

```java
@Test
void testIsActive() {
    Specification<Banner> spec = BannerSpecifications.isActive();

    // In-memory проверка
    assertTrue(spec.isSatisfiedBy(activeBanner));
    assertFalse(spec.isSatisfiedBy(inactiveBanner));

    // SQL генерация
    SqlCriteria criteria = spec.toSqlCriteria();
    assertEquals("is_active = :isActive", criteria.getWhereClause());
    assertEquals(true, criteria.getParameters().get("isActive"));
}

@Test
void testComplexCombination() {
    // Активный MAIN-баннер с активным периодом
    Specification<Banner> spec = BannerSpecifications.isActive()
        .and(BannerSpecifications.hasType(BannerType.MAIN))
        .and(BannerSpecifications.isPeriodActive(LocalDateTime.now()));

    assertTrue(spec.isSatisfiedBy(activeBanner));
    assertFalse(spec.isSatisfiedBy(inactiveBanner));
}
```

### Тесты Use Case

Файл: `SearchBannersUseCaseTest.java`

```java
@Test
void testSearchByType() {
    SearchBannersQuery query = SearchBannersQuery.builder()
        .type(BannerType.MAIN)
        .page(1)
        .size(10)
        .build();

    when(bannerRepository.findBySpecification(any(), any()))
        .thenReturn(Flux.just(testBanner));
    when(bannerRepository.countBySpecification(any()))
        .thenReturn(Mono.just(1L));

    StepVerifier.create(useCase.execute(Mono.just(query)))
        .assertNext(response -> {
            assertEquals(1, response.getBanners().size());
        })
        .verifyComplete();
}
```

---

## Заключение

### Specification Pattern

**Главное преимущество**: Вместо написания сотен методов репозитория, мы создаём переиспользуемые строительные блоки, которые можно комбинировать как конструктор.

**Когда использовать**:
- Когда у вас много комбинаций фильтров
- Когда критерии поиска динамические (зависят от пользовательского ввода)
- Когда нужна гибкость в комбинировании условий

### Event Sourcing

**Главное преимущество**: Полная история всех изменений объекта. Вы никогда не потеряете данные и всегда можете узнать "кто, что, когда и почему".

**Когда использовать**:
- Когда нужен полный аудит операций
- Когда важна история изменений
- Когда нужна аналитика действий пользователей
- Когда требуется отладка сложных сценариев

---

**Вопросы?** Проверьте:
- Тесты в `src/test/java/.../banner/`
- Javadoc в исходных файлах
- Комментарии в коде
