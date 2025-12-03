# GPS API Migration - Краткое Резюме ⚡

## ✅ Хорошие Новости!

API поддерживает **до 50 ID в одном запросе** - это решает большинство проблем с производительностью!

---

## 📊 Ключевые Изменения

### 1. Endpoint и Параметры

**Старый:**
```
GET /api/positions
Authorization: Bearer {token}
```

**Новый:**
```
GET /api/vehicleinfo/v1/getVehicleData?id={id1}&id={id2}&...&from={time}&to={time}
token: {token}
```

**Пример:**
```
http://95.85.97.118/app-overseas-newenergy-core/api/vehicleinfo/v1/getVehicleData?id=1211862078810443776&from=2025-11-19T09:41:00Z&to=2025-11-19T09:44:00Z&id=1209007862702100480
```

### 2. Структура Ответа

**Старый:** Прямой массив
```json
[{...}, {...}]
```

**Новый:** Обернутый объект
```json
{
  "code": 1,
  "msg": "",
  "data": [{...}, {...}]
}
```

### 3. Идентификаторы

- **Старый:** `deviceId`
- **Новый:** `attributes.uniqueId` (данные в bus.xlsx)
- **Нужен маппинг:** deviceId ↔ uniqueId

---

## ⚡ Производительность

### Расчет запросов:
- **700 автобусов** / 50 ID = **~14 запросов**

### Время выполнения:

| Подход | Время |
|--------|-------|
| Последовательно | ~42 сек |
| 3 параллельных | ~14-20 сек ✅ |
| С кэшем (30s TTL) | ~1-2 сек ✅✅ |

**Вывод:** Производительность приемлема!

---

## 🔧 Технические Изменения

### Файлы для Изменения:

1. **GpsApiClient.java**
   - Новый метод `fetchBatch(List<String> ids, Instant from, Instant to)`
   - Batch processing с разбивкой на пакеты по 50
   - **ВАЖНО:** Метод `getLatestPositionsByUniqueId()` - фильтрация дубликатов по reportTime
   - Изменить header с `Authorization: Bearer` на `token`

2. **GpsPositionDTO.java**
   - Добавить поля `utcTime`, `reportTime`
   - Добавить `uniqueId` в `GpsAttributesDTO`
   - Fallback для `motion` (вычислять из `speed`)

3. **GpsApiResponseDTO.java** (новый)
   - Wrapper для ответа API
   - Поля: `code`, `msg`, `traceId`, `data`

4. **application.yml**
   - `base-url: http://95.85.97.118/app-overseas-newenergy-core`
   - `token: YT2AE19C2B1A4FDD8F10B934DA1E6905`
   - `batch-size: 50`
   - `max-concurrent-batches: 3`
   - `time-window-minutes: 3`

5. **База данных**
   - Добавить поле `gps_unique_id` в таблицу `vehicles`
   - Импортировать данные из `bus.xlsx`

---

## 📝 Пример Кода - Batch Request

```java
// Разбить на батчи по 50 ID
List<List<String>> batches = partitionList(uniqueIds, 50);

// Параллельная обработка батчей
return Flux.fromIterable(batches)
    .flatMap(batch -> fetchBatch(batch, from, to), 3) // 3 параллельно
    .flatMapIterable(list -> list)
    .collectList();

// Один batch запрос
private Mono<List<GpsPositionDTO>> fetchBatch(List<String> ids,
                                               Instant from,
                                               Instant to) {
    return webClient.get()
        .uri(uriBuilder -> {
            UriBuilder builder = uriBuilder
                .path("/api/vehicleinfo/v1/getVehicleData")
                .queryParam("from", from.toString())
                .queryParam("to", to.toString());

            // Добавить все ID как отдельные параметры
            for (String id : ids) {
                builder.queryParam("id", id);
            }

            return builder.build();
        })
        .header("token", token)  // НЕ Authorization: Bearer!
        .retrieve()
        .bodyToMono(GpsApiResponseDTO.class)
        .map(response -> {
            List<GpsPositionDTO> allPositions = response.getData();

            // ВАЖНО: API возвращает множественные записи за период
            // Берем только последнюю для каждого uniqueId
            return getLatestPositionsByUniqueId(allPositions);
        });
}

// Фильтрация дубликатов - берем только последнюю позицию
private List<GpsPositionDTO> getLatestPositionsByUniqueId(List<GpsPositionDTO> positions) {
    return positions.stream()
        .filter(pos -> pos.getAttributes() != null
                    && pos.getAttributes().getUniqueId() != null
                    && pos.getReportTime() != null)
        .collect(Collectors.toMap(
            pos -> pos.getAttributes().getUniqueId(),
            pos -> pos,
            // В случае дубликатов - берем с более поздним reportTime
            (existing, replacement) ->
                existing.getReportTime().compareTo(replacement.getReportTime()) >= 0
                    ? existing
                    : replacement
        ))
        .values()
        .stream()
        .collect(Collectors.toList());
}
```

---

## 🎯 План Действий (Приоритеты)

### Этап 1: Подготовка ✅ MUST HAVE
- [ ] Создать `GpsApiResponseDTO`
- [ ] Обновить `GpsPositionDTO` (добавить поля)
- [ ] Миграция БД: добавить `gps_unique_id` в `vehicles`
- [ ] Импортировать данные из `bus.xlsx`

### Этап 2: Реализация API ✅ MUST HAVE
- [ ] Обновить `GpsApiClient.fetchBatch()`
- [ ] Реализовать `partitionList()` helper
- [ ] Изменить authentication header
- [ ] Обновить base URL в конфигурации
- [ ] Добавить логику временного окна (from/to)

### Этап 3: Оптимизация ⚠️ SHOULD HAVE
- [ ] Настроить кэширование (30-60s TTL)
- [ ] Настроить Resilience4j для batch запросов
- [ ] Добавить метрики и логирование

### Этап 4: Тестирование ✅ MUST HAVE
- [ ] Unit тесты для batch processing
- [ ] Integration тесты с mock сервером
- [ ] Performance тесты (700+ автобусов)

---

## ⚠️ Критические Моменты

1. **Маппинг ID обязателен**
   - Без связи deviceId ↔ uniqueId система не заработает
   - Данные в `bus.xlsx` должны быть полными

2. **Временное окно**
   - API требует `from` и `to`
   - Рекомендуется: последние 3-5 минут для real-time данных

3. **🔥 КРИТИЧНО: Фильтрация дубликатов**
   - API возвращает **множественные записи** для каждого автобуса (история за период)
   - Один запрос на 50 автобусов может вернуть 500+ записей
   - **Обязательно группировать по uniqueId и брать только последнюю по reportTime**
   - Без фильтрации система будет обрабатывать одного автобуса 10+ раз!

4. **Обработка ошибок**
   - Проверять `response.code == 1`
   - Fallback для отсутствующих полей (`motion`, `ignition`)

5. **Rate Limiting**
   - Не больше 3-5 параллельных запросов
   - Circuit breaker обязателен

---

## 🚀 Быстрый Старт

### 1. Обновить конфигурацию
```yaml
# application.yml
external:
  api:
    gps:
      base-url: http://95.85.97.118/app-overseas-newenergy-core
      token: YT2AE19C2B1A4FDD8F10B934DA1E6905
      batch-size: 50
      max-concurrent-batches: 3
      time-window-minutes: 3
```

### 2. Создать миграцию БД
```sql
ALTER TABLE vehicles ADD COLUMN gps_unique_id VARCHAR(255);
CREATE INDEX idx_vehicles_gps_unique_id ON vehicles(gps_unique_id);
```

### 3. Обновить DTO
```java
// GpsApiResponseDTO.java
@Data
public class GpsApiResponseDTO {
    private Integer code;
    private String msg;
    private String traceId;
    private List<GpsPositionDTO> data;
}
```

### 4. Обновить GpsApiClient
```java
// Изменить метод fetchAllVehiclePositions()
public Mono<List<GpsPositionDTO>> fetchAllVehiclePositions() {
    // 1. Получить все uniqueId из БД
    // 2. Разбить на батчи по 50
    // 3. Параллельные запросы
    // 4. Объединить результаты
}
```

---

## 📚 Полезные Ссылки

- **Полный отчет:** `GPS_API_MIGRATION_REPORT.md`
- **Данные автобусов:** `new_external_api/bus.xlsx`
- **Пример endpoint:** `new_external_api/endpoint.txt`
- **Пример response:** `new_external_api/response`

---

## ❓ Вопросы для Уточнения

1. Есть ли у API rate limits? (важно для настройки параллелизма)
2. Все ли 700 автобусов активны? (можно оптимизировать количество запросов)
3. Можно ли использовать WebSocket для real-time обновлений?
4. Какая задержка допустима для обновления позиций?

---

## ✅ Итоговая Оценка

| Метрика | Оценка |
|---------|--------|
| **Сложность реализации** | Средняя ⚠️ |
| **Риски** | Низкие ✅ |
| **Производительность** | Приемлемая ✅ |
| **Обратная совместимость** | Поддерживается ✅ |
| **Время разработки** | 2-3 дня |

**Рекомендация:** Можно приступать к реализации! 🚀
