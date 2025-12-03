# GPS API Migration Report - Анализ и План Изменений

## 📋 Обзор

Анализ текущей реализации GPS API и требований к новому API для получения локации автобусов.

---

## 🔍 Текущее Состояние

### Текущий API Endpoint
```
GET https://gps.tugdk.gov.tm/api/positions
Authorization: Bearer {token}
```

**Характеристики:**
- Возвращает все позиции автобусов одним запросом
- Прямой массив объектов `GpsPositionDTO`
- Не требует параметров запроса

### Текущая Структура Ответа
```json
[
  {
    "deviceId": "string",
    "latitude": 37.956309,
    "longitude": 58.40265,
    "speed": 29.0,
    "fixTime": "2025-11-19T09:43:55",
    "course": 34.0,
    "attributes": {
      "name": "4749AGI",
      "motion": true,
      "ignition": true
    }
  }
]
```

### Текущая Реализация

**Файл:** `GpsApiClient.java` (src/main/java/biz/ugur/busroutebackend/shared/infrastructure/external/)

**Методы:**
1. `fetchAllVehiclePositions()` - получает все позиции
2. `fetchVehiclePosition(deviceId)` - фильтрует из всех позиций
3. `healthCheck()` - проверка доступности API

---

## 🆕 Новый API

### Новый Endpoint
```
GET http://95.85.97.118/app-overseas-newenergy-core/api/vehicleinfo/v1/getVehicleData
Parameters:
  - id: 1211862078810443776 (можно до 50 ID в одном запросе!)
  - id: 1209007862702100480 (множественные параметры)
  - from: 2025-11-19T09:41:00Z (начало периода)
  - to: 2025-11-19T09:44:00Z (конец периода)
Header:
  - token: YT2AE19C2B1A4FDD8F10B934DA1E6905
```

**Пример запроса с несколькими ID:**
```
GET http://95.85.97.118/app-overseas-newenergy-core/api/vehicleinfo/v1/getVehicleData?id=1211862078810443776&from=2025-11-19T09:41:00Z&to=2025-11-19T09:44:00Z&id=1209007862702100480
```

**⚠️ ВАЖНО:** API поддерживает до **50 ID в одном запросе**, что значительно улучшает производительность!

### Новая Структура Ответа
```json
{
  "code": 1,
  "msg": "",
  "traceId": "b0VtogJn98lMfkrh",
  "data": [
    {
      "attributes": {
        "name": "4749AGI",
        "uniqueId": "1211862078810443776"
      },
      "longitude": 58.40265,
      "latitude": 37.956309,
      "course": 34,
      "speed": 29.0,
      "reportTime": "2025-11-19 09:43:55",
      "utcTime": "2025-11-19T04:43:55Z"
    }
  ]
}
```

### Ключевые Отличия

| Аспект | Старый API | Новый API |
|--------|-----------|-----------|
| **Endpoint** | `/api/positions` | `/api/vehicleinfo/v1/getVehicleData` |
| **Метод запроса** | Все позиции сразу | По ID транспорта (до 50 за раз) |
| **Параметры** | Нет | `id` (можно несколько), `from`, `to` обязательны |
| **Batch размер** | N/A | До 50 ID в одном запросе |
| **Аутентификация** | Bearer token в Authorization | token в header |
| **Структура ответа** | Прямой массив | Объект с полем `data` |
| **Идентификатор** | `deviceId` | `attributes.uniqueId` |
| **Время** | `fixTime` (LocalDateTime) | `utcTime` (ISO 8601) + `reportTime` |
| **Имя автобуса** | `attributes.name` | `attributes.name` |
| **Поля attributes** | `motion`, `ignition` | Только `name`, `uniqueId` |

---

## 📊 Данные из bus.xlsx

Excel файл содержит **~700 записей** автобусов с полями:
- **Car number** (车工号): Номер автобуса (например: 6360AGJ, 1478AGJ)
- **ID**: Уникальный идентификатор для нового API (например: 1211096522230550528)
- **VIN**: VIN номер (например: LZYTMGE60S1018894)
- **Название** (第4列): Дополнительная информация

**Примеры данных:**
```
Car number: 6360AGJ
ID: 1211096522230550528
VIN: LZYTMGE60S1018894

Car number: 1478AGJ
ID: 1212226353768910848
VIN: LZYTMGE64S1020535
```

---

## 🚨 Основные Проблемы и Вызовы

### 1. **Архитектурное Изменение: От Bulk к Batch Requests**

**Проблема:**
- Старый API: 1 запрос = все автобусы
- Новый API: 1 запрос = до 50 автобусов

**Влияние:**
- ✅ Для ~700 автобусов потребуется ~14 HTTP запросов (700/50)
- ✅ Приемлемое увеличение нагрузки
- ✅ Низкий риск rate limiting при правильной реализации

### 2. **Изменение Идентификатора**

**Проблема:**
- Текущая система использует `deviceId`
- Новый API использует `uniqueId` из `attributes`
- Необходимо сопоставление между `deviceId` и `uniqueId`

### 3. **Необходимость Временных Параметров и Фильтрация Дубликатов**

**Проблема:**
- Новый API требует обязательные параметры `from` и `to`
- API возвращает **все позиции за период** (множественные записи для каждого автобуса)
- Один запрос может вернуть 100+ записей для 50 автобусов (по 10-20 записей каждый)
- **Критично:** Нужно фильтровать дубликаты и брать только последнюю позицию по `reportTime`

**Решение:**
- Использовать узкое временное окно (3-5 минут)
- Группировать результаты по `uniqueId`
- Для каждой группы брать запись с максимальным `reportTime`

### 4. **Отсутствие Полей Motion/Ignition**

**Проблема:**
- Старый API предоставлял `motion` и `ignition` в attributes
- Новый API не предоставляет эти данные
- Текущий код использует эти поля (см. `GpsPositionDTO.getMotion()`)

### 5. **Изменение Формата Времени**

**Проблема:**
- Старый API: `fixTime` как LocalDateTime
- Новый API: `utcTime` (ISO 8601) и `reportTime` (строка)
- Нужна конвертация и выбор правильного поля

---

## ✅ Необходимые Изменения

### 1. **Обновление GpsPositionDTO**

**Файл:** `transport/application/dto/GpsPositionDTO.java`

**Изменения:**
```java
@Data
public class GpsPositionDTO {
    @JsonProperty("deviceId")
    private String deviceId;  // Оставляем для обратной совместимости

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("speed")
    private Double speed;

    @JsonProperty("fixTime")
    private LocalDateTime fixTime;  // Маппим из utcTime

    @JsonProperty("course")
    private Double course;

    @JsonProperty("attributes")
    private GpsAttributesDTO attributes;

    @JsonProperty("utcTime")  // НОВОЕ: для нового API
    private String utcTime;

    @JsonProperty("reportTime")  // НОВОЕ: для нового API
    private String reportTime;

    // Методы остаются
    public String getVehicleName() {
        return attributes != null ? attributes.getName() : null;
    }

    public Boolean getMotion() {
        // ИЗМЕНЕНИЕ: вычисляем из speed если нет в attributes
        if (attributes != null && attributes.getMotion() != null) {
            return attributes.getMotion();
        }
        return speed != null && speed > 0;  // Fallback logic
    }

    @Data
    public static class GpsAttributesDTO {
        @JsonProperty("name")
        private String name;

        @JsonProperty("uniqueId")  // НОВОЕ
        private String uniqueId;

        @JsonProperty("motion")
        private Boolean motion;  // Может быть null в новом API

        @JsonProperty("ignition")
        private Boolean ignition;  // Может быть null в новом API
    }
}
```

### 2. **Создание Response Wrapper DTO**

**Новый файл:** `transport/application/dto/GpsApiResponseDTO.java`

```java
@Data
public class GpsApiResponseDTO {
    @JsonProperty("code")
    private Integer code;

    @JsonProperty("msg")
    private String msg;

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("data")
    private List<GpsPositionDTO> data;
}
```

### 3. **Обновление GpsApiClient**

**Файл:** `shared/infrastructure/external/GpsApiClient.java`

**Новые методы:**

```java
public Mono<List<GpsPositionDTO>> fetchAllVehiclePositions() {
    // НОВАЯ РЕАЛИЗАЦИЯ:
    // 1. Получить список всех uniqueId из базы данных (или конфигурации)
    // 2. Определить временное окно (например, последние 5 минут)
    // 3. Сделать параллельные запросы для всех автобусов
    // 4. Объединить результаты
}

public Mono<List<GpsPositionDTO>> fetchVehiclePositionsByIds(List<String> uniqueIds,
                                                               Instant from,
                                                               Instant to) {
    log.debug("Fetching GPS positions for {} vehicles in batch mode", uniqueIds.size());

    // Разбить на пакеты по 50 ID
    int batchSize = 50;
    List<List<String>> batches = partitionList(uniqueIds, batchSize);

    return Flux.fromIterable(batches)
        .flatMap(batch -> fetchBatch(batch, from, to), 3) // 3 параллельных запроса
        .flatMapIterable(list -> list)
        .collectList()
        .doOnSuccess(positions ->
            log.info("Successfully fetched {} GPS positions from {} batches",
                positions.size(), batches.size()));
}

private Mono<List<GpsPositionDTO>> fetchBatch(List<String> uniqueIds,
                                               Instant from,
                                               Instant to) {
    log.debug("Fetching batch of {} vehicle IDs", uniqueIds.size());

    return webClient.get()
        .uri(uriBuilder -> {
            UriBuilder builder = uriBuilder
                .path("/api/vehicleinfo/v1/getVehicleData")
                .queryParam("from", from.toString())
                .queryParam("to", to.toString());

            // Добавить множественные параметры id
            for (String id : uniqueIds) {
                builder.queryParam("id", id);
            }

            return builder.build();
        })
        .header("token", bearerToken)  // ИЗМЕНЕНО: header вместо Authorization
        .retrieve()
        .bodyToMono(GpsApiResponseDTO.class)
        .map(response -> {
            if (response.getCode() != 1) {
                log.warn("GPS API returned non-success code: {}, msg: {}",
                    response.getCode(), response.getMsg());
            }

            // Преобразование данных
            List<GpsPositionDTO> allPositions = response.getData();

            // ВАЖНО: API возвращает множественные записи за период времени
            // Нужно взять только последнюю запись для каждого uniqueId
            List<GpsPositionDTO> latestPositions = getLatestPositionsByUniqueId(allPositions);

            latestPositions.forEach(this::transformPosition);

            log.debug("Received {} total positions, filtered to {} latest positions",
                allPositions.size(), latestPositions.size());

            return latestPositions;
        })
        .timeout(Duration.ofSeconds(30))
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
            .filter(this::isRetryableException)
            .doBeforeRetry(retrySignal ->
                log.warn("Retrying batch request, attempt: {}", retrySignal.totalRetries() + 1)));
}

/**
 * Извлекает последнюю позицию для каждого uniqueId из списка позиций.
 * API возвращает исторические данные за период (from/to), нам нужна только последняя.
 */
private List<GpsPositionDTO> getLatestPositionsByUniqueId(List<GpsPositionDTO> positions) {
    if (positions == null || positions.isEmpty()) {
        return List.of();
    }

    // Группируем по uniqueId и берем максимальный по reportTime
    Map<String, GpsPositionDTO> latestByUniqueId = positions.stream()
        .filter(pos -> pos.getAttributes() != null
                    && pos.getAttributes().getUniqueId() != null
                    && pos.getReportTime() != null)
        .collect(Collectors.toMap(
            pos -> pos.getAttributes().getUniqueId(),
            pos -> pos,
            // В случае дубликатов - берем с более поздним reportTime
            (existing, replacement) ->
                compareReportTime(existing.getReportTime(), replacement.getReportTime()) >= 0
                    ? existing
                    : replacement
        ));

    return new ArrayList<>(latestByUniqueId.values());
}

/**
 * Сравнивает два reportTime (формат: "2025-11-19 09:43:55")
 * @return положительное число если time1 > time2, 0 если равны, отрицательное если time1 < time2
 */
private int compareReportTime(String time1, String time2) {
    try {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dt1 = LocalDateTime.parse(time1, formatter);
        LocalDateTime dt2 = LocalDateTime.parse(time2, formatter);
        return dt1.compareTo(dt2);
    } catch (Exception e) {
        log.warn("Failed to parse reportTime: {} or {}", time1, time2);
        return time1.compareTo(time2); // Fallback to string comparison
    }
}

private void transformPosition(GpsPositionDTO position) {
    // Преобразование utcTime -> fixTime
    if (position.getUtcTime() != null) {
        position.setFixTime(
            LocalDateTime.parse(position.getUtcTime(),
                DateTimeFormatter.ISO_DATE_TIME)
        );
    }

    // Заполнение deviceId из uniqueId для обратной совместимости
    if (position.getDeviceId() == null &&
        position.getAttributes() != null &&
        position.getAttributes().getUniqueId() != null) {
        position.setDeviceId(position.getAttributes().getUniqueId());
    }
}

private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
    List<List<T>> partitions = new ArrayList<>();
    for (int i = 0; i < list.size(); i += batchSize) {
        partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
    }
    return partitions;
}
```

### 4. **Обновление Конфигурации**

**Файл:** `application.yml`

```yaml
external:
  api:
    gps:
      base-url: http://95.85.97.118/app-overseas-newenergy-core
      token: YT2AE19C2B1A4FDD8F10B934DA1E6905  # ИЗМЕНЕНО: не Bearer
      timeout: 30s  # Увеличено для batch запросов
      batch-size: 50  # НОВОЕ: максимум ID в одном запросе (лимит API)
      max-concurrent-batches: 3  # НОВОЕ: параллельные batch запросы
      time-window-minutes: 3  # НОВОЕ: окно времени для запроса (from/to)
```

### 5. **Создание Vehicle UniqueId Mapping**

**Опции:**

**Вариант A: Добавить поле в таблицу vehicles**
```sql
ALTER TABLE vehicles ADD COLUMN gps_unique_id VARCHAR(255);
CREATE INDEX idx_vehicles_gps_unique_id ON vehicles(gps_unique_id);
```

**Вариант B: Создать отдельную таблицу маппинга**
```sql
CREATE TABLE vehicle_gps_mapping (
    vehicle_id UUID PRIMARY KEY REFERENCES vehicles(id),
    device_id VARCHAR(255) NOT NULL,
    gps_unique_id VARCHAR(255) NOT NULL,
    license_plate VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id),
    UNIQUE(gps_unique_id)
);
```

**Вариант C: Загрузить из bus.xlsx в конфигурацию**
- Создать service для загрузки маппинга из Excel
- Кэшировать в Redis или памяти

### 6. **Оптимизация Performance**

**Стратегии:**

1. **Параллельные запросы с ограничением:**
```java
Flux.fromIterable(vehicleIds)
    .flatMap(id -> fetchVehiclePositionById(id, from, to),
             maxConcurrency) // например, 10 параллельных запросов
    .collectList()
```

2. **Кэширование:**
```java
@Cacheable(value = "vehiclePositions", key = "#uniqueId")
public Mono<List<GpsPositionDTO>> fetchVehiclePositionById(...)
```

3. **Incremental Updates:**
- Запрашивать только автобусы, которые обновлялись недавно
- Использовать приоритизацию по активности

4. **Circuit Breaker настройки:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      gpsApi:
        slidingWindowSize: 20  # Увеличить из-за большего количества запросов
        minimumNumberOfCalls: 10
```

### 7. **Обработка Временных Параметров**

**Стратегия:**
```java
private Instant calculateFromTime() {
    return Instant.now().minus(5, ChronoUnit.MINUTES);
}

private Instant calculateToTime() {
    return Instant.now();
}
```

**Для реального времени:** `from` = текущее время - 5 минут, `to` = текущее время

### 8. **Миграция Данных из bus.xlsx**

**Шаги:**
1. Создать скрипт для парсинга Excel файла
2. Извлечь маппинг: Car number -> ID (uniqueId)
3. Сопоставить с существующими записями vehicles по license_plate
4. Обновить базу данных или создать конфигурационный файл

---

## 🎯 План Реализации

### Этап 1: Подготовка (Priority: HIGH)
- [ ] Создать DTO для нового API (`GpsApiResponseDTO`)
- [ ] Обновить `GpsPositionDTO` для поддержки новых полей
- [ ] Добавить поле `gps_unique_id` в таблицу vehicles (миграция)
- [ ] Импортировать данные из bus.xlsx

### Этап 2: Обновление API Client (Priority: HIGH)
- [ ] Изменить метод аутентификации (header `token` вместо Bearer)
- [ ] Обновить base URL в конфигурации
- [ ] Реализовать `fetchVehiclePositionById()` для нового API
- [ ] Добавить логику преобразования времени (utcTime -> fixTime)

### Этап 3: Batch Processing (Priority: HIGH)
- [ ] Реализовать `fetchVehiclePositionsByIds()` с параллелизмом
- [ ] Настроить ограничение параллельных запросов
- [ ] Добавить логику для определения временного окна
- [ ] Обновить `fetchAllVehiclePositions()` для использования batch логики

### Этап 4: Обработка Отсутствующих Данных (Priority: MEDIUM)
- [ ] Реализовать fallback для `motion` (вычисление из speed)
- [ ] Обработать отсутствие `ignition` поля
- [ ] Добавить валидацию ответов API

### Этап 5: Оптимизация (Priority: MEDIUM)
- [ ] Настроить кэширование для позиций
- [ ] Обновить настройки Resilience4j для нового паттерна запросов
- [ ] Реализовать приоритизацию запросов (активные автобусы первыми)

### Этап 6: Тестирование (Priority: HIGH)
- [ ] Unit тесты для новых методов GpsApiClient
- [ ] Integration тесты с mock сервером нового API
- [ ] Performance тесты для 700+ автобусов
- [ ] Тест обработки ошибок и fallback

### Этап 7: Мониторинг и Логирование (Priority: LOW)
- [ ] Добавить метрики для количества запросов
- [ ] Логировать время выполнения batch операций
- [ ] Алерты для rate limiting

---

## ⚠️ Риски и Митигация

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|-----------|
| Rate limiting от API | Высокая | Высокое | Ограничение параллелизма, retry с backoff |
| Timeout при 700+ запросах | Высокая | Высокое | Batch processing, кэширование |
| Несоответствие uniqueId и deviceId | Средняя | Высокое | Качественный импорт из bus.xlsx, валидация |
| Отсутствие данных motion/ignition | Низкая | Среднее | Вычисление из speed |
| API недоступен | Средняя | Высокое | Circuit breaker, fallback на пустой список |

---

## 📈 Оценка Производительности

### Текущий подход:
- **1 запрос** для всех автобусов
- Время: ~2-5 секунд

### Новый подход (с batch 50 ID):
- **700 автобусов** / 50 ID per request = **14 запросов**
- ✅ Последовательно: 14 × 3 сек = **~42 секунды**
- ✅ С параллелизмом (3 одновременно): 14 / 3 × 3 сек = **~14-20 секунд**
- ✅ **ПРИЕМЛЕМО!**

### Новый подход (с оптимизацией и кэшированием):
- **Кэш TTL 30 секунд** → большинство запросов из кэша
- **Первый запрос:** ~14-20 секунд
- **Последующие:** ~1-2 секунды (из кэша)
- ✅ **ОТЛИЧНО!**

### Рекомендации для оптимизации:
1. ✅ **Использовать batch по 50 ID** (уже реализовано)
2. ✅ **Параллелизм 3-5 запросов** (безопасно для API)
3. ✅ **Кэшировать результаты** на 30-60 секунд
4. ⚠️ **Запрашивать только активные автобусы** (опционально)
5. ⚠️ **Использовать WebSocket** для реального времени (если доступен)

---

## 🔄 Обратная Совместимость

### Вариант 1: Feature Flag
```yaml
external:
  api:
    gps:
      use-new-api: true  # переключатель
```

### Вариант 2: Две имплементации
- Создать `LegacyGpsApiClient` и `NewGpsApiClient`
- Использовать стратегию паттерн для выбора

---

## 📝 Дополнительные Вопросы

1. **Есть ли у нового API endpoint для получения всех автобусов сразу?**
   - Это значительно упростит реализацию

2. **Какие лимиты rate limiting у нового API?**
   - Нужно для настройки параллелизма

3. **Можно ли использовать WebSocket для real-time обновлений?**
   - Избежать polling

4. **Как часто нужно обновлять позиции?**
   - Влияет на стратегию кэширования

5. **Все ли 700 автобусов активны одновременно?**
   - Можно оптимизировать количество запросов

---

## 📚 Затронутые Файлы

1. `GpsApiClient.java` - основные изменения
2. `GpsPositionDTO.java` - обновление структуры
3. `GpsApiResponseDTO.java` - новый класс
4. `ApiClientConfig.java` - обновление конфигурации WebClient
5. `ResilientExternalApiServiceImpl.java` - возможные изменения в resilience4j
6. `application.yml` - новые настройки
7. `UpdateVehiclePositionsUseCase.java` - возможно потребуется адаптация
8. База данных - новая миграция для gps_unique_id

---

## 🎓 Выводы

Миграция на новый GPS API требует **значительных архитектурных изменений**:

1. **Переход от bulk к individual requests** - основная сложность
2. **Необходимость маппинга deviceId ↔ uniqueId** - критично для работы
3. **Performance оптимизация обязательна** - иначе система будет медленной
4. **Обработка отсутствующих полей** - требует fallback логики

**Рекомендация:** Реализовать поэтапно с feature flag для возможности отката.
