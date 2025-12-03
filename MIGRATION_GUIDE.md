# GPS Device ID Migration Guide

## 🎯 Цель

Обновить device_id в таблице vehicles новыми значениями из bus.xlsx для работы с новым GPS API.

---

## 📝 Шаг 1: Конвертация Excel в CSV

### Вариант A: Excel / LibreOffice (Windows/Mac/Linux)

1. Открыть `new_external_api/bus.xlsx`
2. **File → Save As**
3. Формат: **CSV (Comma delimited)**
4. Сохранить как: `scripts/bus.csv`
5. Кодировка: **UTF-8**

### Вариант B: Google Sheets

1. Загрузить `bus.xlsx` в Google Sheets
2. **File → Download → Comma Separated Values (.csv)**
3. Переименовать в `bus.csv`
4. Переместить в `scripts/bus.csv`

### Вариант C: Docker + LibreOffice

```bash
docker run --rm -v "$(pwd)/new_external_api:/data" \
  -v "$(pwd)/scripts:/output" \
  linuxserver/libreoffice:latest \
  libreoffice --headless --convert-to csv --outdir /output /data/bus.xlsx
```

### Вариант D: Online Converter

1. Открыть https://cloudconvert.com/xlsx-to-csv
2. Загрузить `bus.xlsx`
3. Скачать результат как `bus.csv`
4. Переместить в `scripts/bus.csv`

---

## 📝 Шаг 2: Проверить CSV файл

```bash
head -5 scripts/bus.csv
```

**Ожидаемый формат:**
```
car_number,device_id,vin,extra
6360AGJ,1211096522230550528,LZYTMGE60S1018894,25H976V-0441
1478AGJ,1212226353768910848,LZYTMGE64S1020535,25H976V-0579
...
```

**⚠️ ВАЖНО:**
- Убедитесь что `car_number` совпадает с `license_plate` в БД
- В БД: "1499 AGG" (с пробелом) → в CSV должно быть ТОЧНО так же!

---

## 📝 Шаг 3: Backup База Данных

```bash
# Создать backup (ОБЯЗАТЕЛЬНО!)
docker exec postgres-container pg_dump -U postgres bus_route_db > backup_$(date +%Y%m%d_%H%M%S).sql
```

---

## 📝 Шаг 4: Запустить Миграцию

### Обновить путь к CSV в SQL скрипте:

Отредактировать `scripts/migrate_device_ids.sql`:
```sql
-- Строка 24: ЗАМЕНИТЕ '/path/to/bus.csv' на:
\copy temp_device_mapping(car_number, device_id, vin, extra) FROM '/home/developer/projects/bus/ugur_v4/bus-route-backend/scripts/bus.csv' DELIMITER ',' CSV HEADER;
```

### Запустить миграцию:

```bash
# Через docker
docker exec -i postgres-container psql -U postgres bus_route_db < scripts/migrate_device_ids.sql

# Или напрямую через psql
psql -h localhost -U postgres -d bus_route_db -f scripts/migrate_device_ids.sql
```

---

## 📝 Шаг 5: Проверить Результаты

```sql
-- Подключиться к БД
docker exec -it postgres-container psql -U postgres bus_route_db

-- Проверить количество обновленных записей
SELECT
    COUNT(*) as total_vehicles,
    COUNT(CASE WHEN LENGTH(device_id) > 15 THEN 1 END) as new_format,
    ROUND(COUNT(CASE WHEN LENGTH(device_id) > 15 THEN 1 END)::numeric / COUNT(*)::numeric * 100, 2) as percentage
FROM vehicles;

-- Показать примеры
SELECT license_plate, device_id, is_active
FROM vehicles
WHERE LENGTH(device_id) > 15
LIMIT 10;

-- Проверить несопоставленные
SELECT license_plate, device_id
FROM vehicles
WHERE LENGTH(device_id) <= 15
  AND is_active = true;
```

**Ожидаемый результат:**
```
 total_vehicles | new_format | percentage
----------------+------------+------------
            700 |        700 |     100.00
```

---

## 📝 Шаг 6: Запустить Приложение

```bash
# Запустить приложение
./mvnw spring-boot:run

# Или через Docker
make dev-up
```

---

## 📝 Шаг 7: Проверить Логи

```bash
# Следить за логами GPS API
tail -f logs/bus-route-backend.log | grep -i gps

# Или через Docker
docker logs -f bus-route-backend | grep -i gps
```

**Ожидаемые логи:**
```
GpsApiClient initialized: batchSize=50, maxConcurrentBatches=3, timeWindowMinutes=3
Fetching GPS positions for 700 devices using batch processing
Split 700 device IDs into 14 batches (batch size: 50)
Fetching batch of 50 device IDs from 2025-11-20T09:00:00Z to 2025-11-20T09:03:00Z
Batch result: 125 total records -> 50 unique vehicles
Successfully fetched 700 GPS positions from 14 batches
```

---

## 📝 Шаг 8: Тестировать API

### Test 1: Health Check

```bash
curl -X GET http://localhost:8080/actuator/health
```

### Test 2: Прямой запрос к GPS API

```bash
curl -H "token: YT2AE19C2B1A4FDD8F10B934DA1E6905" \
  "http://95.85.97.118/app-overseas-newenergy-core/api/vehicleinfo/v1/getVehicleData?id=1211862078810443776&from=2025-11-20T09:00:00Z&to=2025-11-20T09:05:00Z"
```

### Test 3: Проверить что vehicle positions обновляются

```sql
-- Подключиться к БД
docker exec -it postgres-container psql -U postgres bus_route_db

-- Проверить последние обновления позиций
SELECT license_plate, current_latitude, current_longitude, last_position_update
FROM vehicles
WHERE last_position_update > NOW() - INTERVAL '5 minutes'
ORDER BY last_position_update DESC
LIMIT 20;
```

---

## ⚠️ Troubleshooting

### Проблема 1: CSV неправильно загружен

**Симптом:** Ошибка при `\copy`

**Решение:**
```bash
# Проверить формат CSV
file scripts/bus.csv
head -1 scripts/bus.csv

# Убедиться что разделитель - запятая
# Убедиться что кодировка UTF-8
```

### Проблема 2: Несопоставленные записи

**Симптом:** Много записей "NOT found in database"

**Решение:**
```sql
-- Проверить форматы
SELECT DISTINCT license_plate FROM vehicles ORDER BY license_plate LIMIT 20;

-- Сравнить с CSV
-- Возможно нужно добавить/убрать пробелы
```

### Проблема 3: Дубликаты device_id

**Симптом:** Несколько vehicles с одним device_id

**Решение:**
```sql
-- Найти дубликаты
SELECT device_id, array_agg(license_plate) as vehicles
FROM vehicles
GROUP BY device_id
HAVING COUNT(*) > 1;

-- Проверить исходные данные в bus.xlsx
```

### Проблема 4: GPS API не возвращает данные

**Симптом:** Empty list в логах

**Решение:**
1. Проверить token: `YT2AE19C2B1A4FDD8F10B934DA1E6905`
2. Проверить что device_id обновлены
3. Проверить доступность API:
```bash
curl -I http://95.85.97.118/app-overseas-newenergy-core/api/vehicleinfo/v1/getVehicleData
```

---

## 🎯 Чеклист Миграции

- [ ] Конвертировать bus.xlsx в CSV
- [ ] Проверить формат CSV
- [ ] Создать backup БД
- [ ] Обновить путь к CSV в migrate_device_ids.sql
- [ ] Запустить миграцию SQL
- [ ] Проверить результаты миграции (100% обновлено?)
- [ ] Запустить приложение
- [ ] Проверить логи GPS API
- [ ] Протестировать обновление позиций
- [ ] Проверить что нет ошибок

---

## ✅ Готово!

После успешной миграции система будет:
- ✅ Использовать новый GPS API
- ✅ Делать batch запросы (14 вместо 700)
- ✅ Фильтровать дубликаты
- ✅ Обновлять позиции автобусов в реальном времени
