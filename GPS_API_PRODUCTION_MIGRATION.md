# Инструкция по Миграции БД на Production Сервере

## GPS API Migration - Production Deployment Guide

---

## 📋 Обзор Миграции

**Цель:** Миграция с старого GPS API на новый batch-based API
**Версия миграции:** V23
**Затронутая таблица:** `vehicles`
**Количество записей:** ~279

**Что изменится:**
- Колонка `device_id` будет заполнена из `license_plate` (без пробелов)
- GPS API начнёт обновлять позиции автобусов каждые 30 секунд
- Batch обработка: до 50 device IDs за запрос

---

## ⚠️ ВАЖНО: Подготовка к Миграции

### 1. Создайте Backup БД (ОБЯЗАТЕЛЬНО!)

```bash
# На production сервере
pg_dump -h localhost -U bus_route_user -d bus_route_db \
  > backup_before_gps_migration_$(date +%Y%m%d_%H%M%S).sql

# Проверьте размер backup
ls -lh backup_before_gps_migration_*.sql
```

### 2. Проверьте Environment Variables

```bash
# Убедитесь, что установлены:
echo $GPS_API_BASE_URL
echo $GPS_API_TOKEN
echo $GPS_API_TIME_WINDOW_MINUTES
```

**Необходимые значения:**
```bash
export GPS_API_BASE_URL=http://95.85.97.118/app-overseas-newenergy-core
export GPS_API_TOKEN=YT2AE19C2B1A4FDD8F10B934DA1E6905
export GPS_API_TIME_WINDOW_MINUTES=60
```

### 3. Проверьте Текущее Состояние БД

```sql
-- Подключитесь к БД
psql -h localhost -U bus_route_user -d bus_route_db

-- Проверьте версию миграций
SELECT version, description, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;

-- Проверьте количество записей без device_id
SELECT
    COUNT(*) as total_vehicles,
    COUNT(device_id) as with_device_id,
    COUNT(*) - COUNT(device_id) as missing_device_id
FROM vehicles;

-- Примеры license_plate (для проверки формата)
SELECT license_plate, device_id FROM vehicles LIMIT 10;
```

**Ожидаемый результат:**
- `total_vehicles`: ~279
- `missing_device_id`: ~279 (или > 0)

---

## 🚀 Вариант 1: Автоматическая Миграция (Рекомендуется)

### Шаги:

**1. Остановите приложение**
```bash
sudo systemctl stop bus-route-backend
# или
kill $(cat /var/run/bus-route-backend.pid)
```

**2. Deploy новой версии**
```bash
# Скопируйте новый JAR файл
scp target/bus-route-backend-1.0.0.jar user@production:/opt/bus-route-backend/

# Обновите application.yml (если нужно)
```

**3. Запустите приложение**
```bash
sudo systemctl start bus-route-backend
# или
java -jar /opt/bus-route-backend/bus-route-backend-1.0.0.jar
```

**4. Мониторинг логов**
```bash
tail -f /var/log/bus-route-backend/bus-route-backend.log

# Ищите:
# [INFO] Flyway - Migrating schema "public" to version "23 - migrate device ids"
# [INFO] Flyway - Successfully applied 1 migration to schema "public"
```

**5. Проверка после запуска**

После старта приложения (через 1-2 минуты):

```sql
-- Проверьте, что миграция выполнена
SELECT version, description, success
FROM flyway_schema_history
WHERE version = '23';

-- Проверьте заполнение device_id
SELECT
    COUNT(*) as total,
    COUNT(device_id) as filled,
    COUNT(*) - COUNT(device_id) as empty
FROM vehicles;

-- Примеры данных
SELECT license_plate, device_id
FROM vehicles
WHERE device_id IS NOT NULL
LIMIT 10;
```

**Ожидаемый результат:**
```
license_plate | device_id
--------------+-----------
1499 AGG      | 1499AGG
2301 AGB      | 2301AGB
3101 AGC      | 3101AGC
```

---

## 🔧 Вариант 2: Ручная Миграция (Для опытных пользователей)

Если хотите выполнить миграцию **вручную** перед запуском приложения:

### Шаги:

**1. Создайте backup таблицы**
```sql
CREATE TABLE vehicles_backup AS SELECT * FROM vehicles;
```

**2. Выполните миграцию данных**
```sql
-- Обновите device_id
UPDATE vehicles
SET device_id = REPLACE(license_plate, ' ', '')
WHERE device_id IS NULL;

-- Проверьте результат
SELECT
    license_plate,
    device_id,
    CASE
        WHEN device_id IS NOT NULL THEN '✓'
        ELSE '✗'
    END as migrated
FROM vehicles
LIMIT 20;
```

**3. Зарегистрируйте миграцию в Flyway**
```sql
INSERT INTO flyway_schema_history (
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_by,
    installed_on,
    execution_time,
    success
) VALUES (
    (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history),
    '23',
    'migrate device ids',
    'SQL',
    'V23__migrate_device_ids.sql',
    -1234567890,  -- Dummy checksum
    CURRENT_USER,
    NOW(),
    0,
    TRUE
);
```

**4. Запустите приложение**
```bash
sudo systemctl start bus-route-backend
```

Flyway увидит, что V23 уже выполнена, и пропустит её.

---

## ✅ Проверка Работы GPS API

### 1. Проверьте GPS обновления в БД

Подождите 1-2 минуты после запуска, затем:

```sql
-- Проверьте свежие GPS обновления
SELECT
    device_id,
    license_plate,
    current_latitude,
    current_longitude,
    last_position_update,
    AGE(NOW(), last_position_update) as update_age
FROM vehicles
WHERE last_position_update > NOW() - INTERVAL '5 minutes'
ORDER BY last_position_update DESC
LIMIT 20;
```

**Ожидаемый результат:**
- `update_age`: < 2 minutes
- Широта/Долгота: заполнены
- Количество: ~160-180 автобусов

### 2. Проверьте логи приложения

```bash
tail -f /var/log/bus-route-backend/bus-route-backend.log | grep -E "GPS|Batch"
```

**Ожидаемые сообщения:**
```
[INFO] Successfully fetched 167 GPS positions from 18 batches
[INFO] Batch updated 80 vehicles
[INFO] Batch updated 99 vehicles
[INFO] GPS update completed: duration=2809ms, updated=166, created=0, failed=0
```

### 3. Проверьте REST API

```bash
# Получите JWT токен
curl -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Проверьте GPS positions endpoint
curl http://localhost:8080/api/vehicles/positions | head -50
```

---

## 📊 Мониторинг После Миграции

### Первые 10 минут:

**1. Проверяйте логи каждые 30 секунд:**
```bash
watch -n 30 "tail -20 /var/log/bus-route-backend/bus-route-backend.log | grep GPS"
```

**2. Мониторьте обновления в БД:**
```sql
-- Запускайте каждую минуту
SELECT
    COUNT(*) FILTER (WHERE last_position_update > NOW() - INTERVAL '2 minutes') as recent_updates,
    COUNT(*) FILTER (WHERE last_position_update > NOW() - INTERVAL '1 hour') as hourly_updates,
    COUNT(*) as total_vehicles
FROM vehicles;
```

**3. Проверьте Resilience4j метрики:**
```bash
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state | grep gpsApi
curl http://localhost:8080/actuator/health | grep gps
```

---

## 🔄 Откат Миграции (если нужно)

Если миграция прошла неудачно:

### Шаг 1: Остановите приложение
```bash
sudo systemctl stop bus-route-backend
```

### Шаг 2: Восстановите данные
```sql
-- Восстановите таблицу из backup
BEGIN;

TRUNCATE TABLE vehicles CASCADE;
INSERT INTO vehicles SELECT * FROM vehicles_backup;

COMMIT;
```

### Шаг 3: Удалите запись миграции
```sql
DELETE FROM flyway_schema_history WHERE version = '23';
```

### Шаг 4: Откатите код
```bash
# Восстановите предыдущую версию JAR
cp /opt/bus-route-backend/bus-route-backend-old.jar \
   /opt/bus-route-backend/bus-route-backend-1.0.0.jar
```

### Шаг 5: Запустите старую версию
```bash
sudo systemctl start bus-route-backend
```

---

## 📝 Чек-лист Миграции

- [ ] Создан backup БД
- [ ] Проверены environment variables
- [ ] Проверено свободное место на диске (> 1GB)
- [ ] Остановлено приложение
- [ ] Deployed новый JAR файл
- [ ] Запущено приложение
- [ ] Проверены логи Flyway (миграция V23 success)
- [ ] Проверено заполнение device_id (все 279 записей)
- [ ] Проверены GPS обновления (< 2 минуты)
- [ ] Проверен REST API endpoint
- [ ] Мониторинг работает стабильно 10+ минут

---

## 🆘 Troubleshooting

### Проблема 1: Миграция не выполняется

**Симптомы:** В логах нет сообщения о миграции V23

**Решение:**
```sql
-- Проверьте, не выполнена ли уже
SELECT * FROM flyway_schema_history WHERE version = '23';

-- Если выполнена, но device_id пустые:
UPDATE vehicles
SET device_id = REPLACE(license_plate, ' ', '')
WHERE device_id IS NULL;
```

### Проблема 2: GPS positions не обновляются

**Симптомы:** `last_position_update` не меняется

**Проверки:**
1. Проверьте токен GPS API
2. Проверьте network connectivity до GPS API
3. Проверьте device_ids в БД совпадают с GPS API

```bash
# Проверьте connectivity
curl -H "Authorization: Bearer $GPS_API_TOKEN" \
  "http://95.85.97.118/app-overseas-newenergy-core/api/vehicleinfo/v1/getVehicleData?from=2025-11-20T00:00:00Z&to=2025-11-20T23:59:59Z&id=1499AGG"
```

### Проблема 3: SQL binding errors

**Симптомы:** В логах `Binding index 0 when only 0 parameters are expected`

**Решение:** Эта ошибка уже исправлена в новой версии. Убедитесь, что используете последний JAR файл.

### Проблема 4: Circuit Breaker OPEN

**Симптомы:** `CircuitBreaker 'gpsApi' is OPEN`

**Решение:**
```bash
# Проверьте health
curl http://localhost:8080/actuator/health

# Подождите 5 минут для auto-recovery
# Или перезапустите приложение
sudo systemctl restart bus-route-backend
```

---

## 📞 Контакты и Поддержка

При возникновении проблем:

1. Соберите логи: `journalctl -u bus-route-backend -n 1000 > issue.log`
2. Экспортируйте состояние БД: `psql -c "SELECT * FROM flyway_schema_history;"`
3. Сохраните метрики: `curl http://localhost:8080/actuator/metrics > metrics.json`

---

## 📅 История Изменений

- **2025-11-20**: Initial GPS API migration guide
- **Версия миграции**: V23
- **Затронутые компоненты**: GpsApiClient, VehicleRepository, VehicleDataScheduler

---

**Успешной миграции! 🚀**
