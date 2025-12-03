# JWT Authentication - Быстрый старт

## 🔐 Проблема: HTTP 401 Unauthorized

API требует JWT токен для авторизации. Вот как его получить и использовать.

---

## ⚡ Вариант 1: Автоматический (рекомендуется)

### Использование скрипта

```bash
cd /home/developer/projects/bus/ugur_v4/bus-route-backend

# Получить токен (по умолчанию admin/admin123)
./get-jwt-token.sh

# Или с другими учетными данными
./get-jwt-token.sh admin admin123
./get-jwt-token.sh client client123
```

Токен будет:
- ✅ Выведен в консоль
- ✅ Сохранен в файл `.jwt-token`
- ✅ Скопирован в буфер обмена (если доступно)

---

## 🌐 Вариант 2: Через HTML страницу

1. Откройте `gps-test-map.html` в браузере
2. Нажмите кнопку **"Получить токен"**
3. Введите username (например: `admin`)
4. Введите password (например: `admin123`)
5. Токен автоматически заполнится в поле
6. Нажмите **"Загрузить данные"**

---

## 📝 Вариант 3: Ручной (через curl)

### Admin Login

```bash
curl -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Client Login

```bash
curl -X POST http://localhost:8080/api/client/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"client","password":"client123"}'
```

Ответ будет содержать:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "...",
  "expiresIn": 3600
}
```

---

## 🚀 Использование токена

### В curl

```bash
# Сохранить токен в переменную
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

# Использовать токен
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/vehicles/positions
```

### Или прочитать из файла

```bash
# Если использовали get-jwt-token.sh
TOKEN=$(cat .jwt-token)

curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/vehicles/positions
```

### В JavaScript (HTML страница)

```javascript
fetch('http://localhost:8080/api/vehicles/positions', {
    headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
    }
})
```

---

## 🔑 Учетные данные по умолчанию

### Admin
```
Username: admin
Password: admin123
Endpoint: /api/admin/auth/login
Token TTL: 1 час
```

### Client
```
Username: client  
Password: client123
Endpoint: /api/client/auth/login
Token TTL: 31 день
```

**⚠️ ВАЖНО:** Учетные данные должны быть созданы в базе данных!

---

## 🔄 Если токен истек

Признаки:
- `HTTP 401: Unauthorized`
- Ошибка "Token expired"

Решение:
```bash
# Получить новый токен
./get-jwt-token.sh admin admin123

# Или обновить через HTML страницу
# Кнопка "Получить токен"
```

---

## 🧪 Тестирование авторизации

### Проверка токена

```bash
TOKEN=$(cat .jwt-token)

# Должен вернуть данные
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/vehicles/positions | jq '.'

# Без токена - ошибка 401
curl http://localhost:8080/api/vehicles/positions
```

### Проверка срока действия

```bash
# Декодировать токен (показать payload)
TOKEN=$(cat .jwt-token)
echo $TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq '.'
```

---

## 📋 Пошаговая инструкция для HTML карты

1. **Запустить backend**
   ```bash
   ./mvnw spring-boot:run
   ```

2. **Получить токен**
   ```bash
   ./get-jwt-token.sh admin admin123
   ```

3. **Открыть HTML страницу**
   ```bash
   # В браузере
   file:///home/developer/.../gps-test-map.html
   
   # Или через HTTP сервер
   python3 -m http.server 8000
   # http://localhost:8000/gps-test-map.html
   ```

4. **Вставить токен**
   - Скопируйте токен из вывода скрипта
   - Вставьте в поле "JWT Token (Bearer)"

5. **Загрузить данные**
   - Нажмите "Загрузить данные"
   - Автобусы появятся на карте

---

## ❓ FAQ

### Q: Где взять username/password?

A: Они должны быть созданы в базе данных. Проверьте:
```sql
SELECT username, role FROM admins;
SELECT username FROM clients;
```

### Q: Как создать нового пользователя?

A: Через API регистрации:
```bash
# Admin
curl -X POST http://localhost:8080/api/admin/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"newadmin","password":"pass123","email":"admin@test.com"}'

# Client  
curl -X POST http://localhost:8080/api/client/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"newclient","password":"pass123","phoneNumber":"+99312345678"}'
```

### Q: Токен сохраняется?

A: Да, в HTML странице токен сохраняется в `localStorage` браузера.

### Q: Что делать если "Invalid token"?

A: 
1. Проверьте что токен полностью скопирован (без пробелов/переносов)
2. Получите новый токен
3. Проверьте что backend использует тот же JWT_SECRET

---

## 🎯 Готовые команды для копирования

### Получить токен и протестировать API

```bash
cd /home/developer/projects/bus/ugur_v4/bus-route-backend

# Получить токен
./get-jwt-token.sh admin admin123

# Сохранить в переменную
TOKEN=$(cat .jwt-token)

# Тест 1: Получить все автобусы
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/vehicles/positions | jq '.'

# Тест 2: Ограничить количество
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/vehicles/positions?limit=10" | jq '.'

# Тест 3: Только активные
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/vehicles/positions?active=true" | jq '.'
```

---

## ✅ Чеклист

- [ ] Backend запущен (`./mvnw spring-boot:run`)
- [ ] Учетные данные известны (admin/admin123)
- [ ] Токен получен (`./get-jwt-token.sh`)
- [ ] Токен вставлен в HTML страницу
- [ ] Данные загружаются без ошибки 401

---

**🎉 Готово! Теперь можете тестировать GPS API с авторизацией!**
