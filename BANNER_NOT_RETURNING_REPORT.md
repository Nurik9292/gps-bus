# Отчет: Почему баннеры не доходят в админ панель

## Проблема
При запросе `GET /api/v1/admin/banners` возвращается пустой список баннеров.

## Корневая причина

### ✅ Запросы успешно проходят
Логи показывают что запросы обрабатываются успешно:
```
2025-11-10 07:49:32.240 [reactor-http-epoll-4] INFO - Request started - GET /api/v1/admin/banners
2025-11-10 07:49:32.276 [reactor-tcp-epoll-6] INFO - Request completed successfully - CorrelationId: ADMIN-464e0c7b
```

### ❌ ВСЕ баннеры истекли по дате

**Текущая дата в БД:** `2025-11-10 05:11:32`

**Баннеры в базе:**
```
id                                   | title        | is_active | end_date            | ИСТЕК?
-------------------------------------|--------------|-----------|---------------------|-------
bbbd091f-5002-4c37-aa82-dbadb0780b20 | dsfsdf       | true      | 2025-10-19 07:37:00 | ✅ ДА
e3f01424-5808-4a17-ba1c-985f64da9528 | 2222         | true      | 2025-08-28 11:28:00 | ✅ ДА
dbf726de-448b-476f-92d8-74e685230ef5 | teseser      | true      | 2025-10-25 21:05:00 | ✅ ДА
67972e14-7777-4095-83e0-dd49b67036b6 | dfdsfdsf2222 | true      | 2025-08-27 22:27:00 | ✅ ДА
1785b0a7-d31b-47b1-8123-69857d9fdb6e | sdf          | true      | 2025-08-29 22:40:00 | ✅ ДА
```

**Все 5 баннеров имеют `end_date` в прошлом!**

---

## Логика фильтрации

### Use Case использует `findAll(pageable)`
**Файл:** `GetBannersWithPaginationUseCase.java:54`
```java
return bannerRepository.findAll(pageable)
```

### BaseR2dbcRepository.findAll() НЕ фильтрует по датам
**Файл:** `BaseR2dbcRepository.java:62`
```java
String sql = String.format(
    "SELECT * FROM %s %s LIMIT :limit OFFSET :offset",
    tableName,
    getOrderByClause(pageable)
);
```

**SQL:** `SELECT * FROM banners ORDER BY display_order ASC LIMIT 20 OFFSET 0`

### ❗ Проблема: метод игнорирует параметр `active`

Контроллер передает `active=true` (по умолчанию), но Use Case использует `findAll()` который:
1. ❌ **НЕ проверяет** `is_active`
2. ❌ **НЕ проверяет** `start_date <= NOW()`
3. ❌ **НЕ проверяет** `end_date >= NOW()`

---

## Почему баннеры НЕ возвращаются?

### Сценарий 1: Если используется правильная фильтрация
Если бы Use Case использовал метод `findActiveBanners()`, он бы применил фильтр:

**Файл:** `R2dbcAdminBannerRepository.java:21`
```java
String sql = """
    SELECT * FROM banners
    WHERE is_active = true
    AND (start_date IS NULL OR start_date <= NOW())
    AND (end_date IS NULL OR end_date >= NOW())  ← ЭТА ПРОВЕРКА!
    ORDER BY display_order ASC, created_at DESC
    """;
```

С этим фильтром **ВСЕ 5 баннеров отсеиваются** потому что:
- `end_date >= NOW()` → `2025-08-27 >= 2025-11-10` → **FALSE**

---

### Сценарий 2: Если используется текущий `findAll()`
Метод `findAll()` должен бы вернуть все 5 баннеров, так как не проверяет даты.

**🔍 Требуется дополнительная диагностика:**
- Проверить, какой именно метод вызывается в рантайме
- Возможно есть маппер или другой слой который фильтрует результаты

---

## Где может быть фильтрация?

### 1. BannerResponseMapper
**Файл:** `GetBannersWithPaginationUseCase.java:55`
```java
.flatMap(bannerResponseMapper::toResponse)
```

Возможно маппер отфильтровывает истекшие баннеры.

### 2. Кеширование
Возможно результаты кешируются в Redis и возвращается старый пустой результат.

### 3. Другой репозиторий
Возможно в рантайме вызывается не тот метод репозитория.

---

## Решение

### Краткосрочное решение: Обновить даты баннеров
```sql
UPDATE banners
SET end_date = NOW() + INTERVAL '30 days',
    start_date = NOW() - INTERVAL '1 day'
WHERE is_active = true;
```

### Долгосрочное решение: Исправить логику

**Проблема 1:** Use Case игнорирует параметр `activeOnly`
- Нужно использовать правильный метод репозитория в зависимости от `query.getActiveOnly()`

**Проблема 2:** Админ панель должна показывать истекшие баннеры
- Админу нужно видеть ВСЕ баннеры (включая истекшие) для управления
- Клиентам показывать только активные с валидными датами

**Рекомендация:**
1. Для админ панели использовать `findAll(pageable)` БЕЗ фильтрации по датам
2. В UI показывать статус баннера (активен/истек)
3. Для клиентского API использовать `findActiveBanners()` С фильтрацией

---

## Следующие шаги

1. ✅ Проверить `BannerResponseMapper` - может там фильтрация
2. ✅ Включить DEBUG логирование для Use Case
3. ✅ Проверить Redis кеш
4. ✅ Обновить даты баннеров для теста
5. ✅ Исправить логику Use Case

---

## SQL для диагностики

```sql
-- Текущая дата
SELECT NOW();

-- Все баннеры с проверкой валидности
SELECT
    id,
    title,
    is_active,
    start_date,
    end_date,
    start_date <= NOW() as start_valid,
    end_date >= NOW() as end_valid,
    CASE
        WHEN is_active = true
         AND (start_date IS NULL OR start_date <= NOW())
         AND (end_date IS NULL OR end_date >= NOW())
        THEN 'ACTIVE'
        ELSE 'INACTIVE/EXPIRED'
    END as status
FROM banners
ORDER BY display_order;

-- Обновить даты для теста
UPDATE banners
SET end_date = NOW() + INTERVAL '30 days',
    start_date = NOW() - INTERVAL '1 day'
WHERE id = 'bbbd091f-5002-4c37-aa82-dbadb0780b20';
```
