# External Services API Integration Guide

## Обзор

Система управления внешними сервисами позволяет предоставлять доступ к Mobile API (`/api/v1/mobile/*`) внешним системам через статические API-токены.

## Архитектура

### Bounded Context
Реализовано как отдельный bounded context `integration`:
- `integration/domain` - доменная модель (ExternalService aggregate)
- `integration/application` - use cases и DTOs
- `integration/infrastructure` - R2DBC repositories, security фильтры
- `integration/interfaces` - REST API для админов

### Компоненты

1. **ExternalService** - Aggregate root, управляет жизненным циклом внешнего сервиса
2. **ApiToken** - Value object, криптографически безопасный токен (формат: `brt_<base64>`)
3. **ApiTokenAuthenticationFilter** - Security фильтр для аутентификации по токену
4. **ApiTokenRateLimiter** - Rate limiting через Redis

## Admin API - Управление внешними сервисами

### Base URL
```
/api/v1/admin/external-services
```

Требуется JWT-аутентификация админа.

### 1. Создать внешний сервис

**POST** `/api/v1/admin/external-services`

**Request Body:**
```json
{
  "name": "Partner System Name",
  "description": "Description of the service",
  "allowedEndpoints": [
    "/api/v1/mobile/routes/*",
    "/api/v1/mobile/stops/*"
  ],
  "rateLimitPerMinute": 100
}
```

**Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "id": "uuid",
    "name": "Partner System Name",
    "description": "Description",
    "apiToken": "brt_abc123...",  // ⚠️ Показывается ТОЛЬКО при создании!
    "maskedToken": "brt_abc1...xyz",
    "isActive": true,
    "allowedEndpoints": ["/api/v1/mobile/routes/*"],
    "rateLimitPerMinute": 100,
    "lastUsedAt": null,
    "createdByAdminId": "admin-uuid",
    "createdAt": "2025-11-13T10:00:00Z",
    "updatedAt": "2025-11-13T10:00:00Z"
  }
}
```

⚠️ **ВАЖНО**: Токен `apiToken` показывается только один раз при создании! Сохраните его в безопасном месте.

### 2. Получить список всех сервисов

**GET** `/api/v1/admin/external-services`

**Query Parameters:**
- `activeOnly` (boolean, default: false) - показать только активные

**Response:**
```json
{
  "status": "success",
  "data": {
    "services": [...],
    "totalCount": 5,
    "activeCount": 3
  }
}
```

### 3. Получить сервис по ID

**GET** `/api/v1/admin/external-services/{id}`

### 4. Обновить сервис

**PUT** `/api/v1/admin/external-services/{id}`

```json
{
  "name": "Updated Name",
  "description": "Updated description",
  "allowedEndpoints": ["/api/v1/mobile/**"],
  "rateLimitPerMinute": 200
}
```

### 5. Заблокировать сервис

**POST** `/api/v1/admin/external-services/{id}/block?reason=Security%20issue`

Немедленно прекращает доступ к API.

### 6. Разблокировать сервис

**POST** `/api/v1/admin/external-services/{id}/unblock`

### 7. Удалить сервис

**DELETE** `/api/v1/admin/external-services/{id}`

Немедленно прекращает доступ и удаляет токен навсегда.

## Использование API токена внешней системой

### Аутентификация

Добавьте заголовок Authorization с API токеном:

```http
GET /api/v1/mobile/routes HTTP/1.1
Host: api.example.com
Authorization: Bearer brt_abc123...
```

### Пример с curl

```bash
curl -H "Authorization: Bearer brt_abc123..." \
     https://api.example.com/api/v1/mobile/routes
```

### Пример с JavaScript

```javascript
fetch('https://api.example.com/api/v1/mobile/routes', {
  headers: {
    'Authorization': 'Bearer brt_abc123...'
  }
})
.then(response => response.json())
.then(data => console.log(data));
```

### Пример с Python

```python
import requests

headers = {
    'Authorization': 'Bearer brt_abc123...'
}

response = requests.get(
    'https://api.example.com/api/v1/mobile/routes',
    headers=headers
)
data = response.json()
```

## Разрешения endpoints

### Wildcard паттерны

- `*` - любые символы кроме `/`
- `**` - любые символы включая `/`

### Примеры

```json
"allowedEndpoints": [
  "/api/v1/mobile/routes/*",         // все routes endpoints
  "/api/v1/mobile/stops/*",          // все stops endpoints
  "/api/v1/mobile/**"                // все mobile endpoints
]
```

Если `allowedEndpoints` = `null`, доступны ВСЕ endpoints.

## Rate Limiting

- Ограничение по количеству запросов в минуту
- Считается sliding window через Redis
- При превышении: HTTP 429 Too Many Requests

**Response при превышении:**
```json
{
  "status": "error",
  "message": "Rate limit exceeded for service 'Partner System': 101/100 requests per minute"
}
```

## Логирование использования

Все запросы от внешних сервисов логируются в таблицу `external_service_api_logs`:

- Endpoint
- HTTP method
- Response status
- Response time
- IP address
- User agent
- Error message (если есть)

## База данных

### Таблица `external_services`

```sql
CREATE TABLE external_services (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    api_token VARCHAR(64) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT true,
    allowed_endpoints TEXT[],
    rate_limit_per_minute INTEGER,
    last_used_at TIMESTAMP,
    created_by_admin_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL
);
```

### Таблица `external_service_api_logs`

```sql
CREATE TABLE external_service_api_logs (
    id UUID PRIMARY KEY,
    external_service_id UUID NOT NULL,
    endpoint VARCHAR(500) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    response_status INTEGER,
    response_time_ms INTEGER,
    ip_address VARCHAR(45),
    user_agent TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL
);
```

## Security

### Безопасность токенов

- Токены генерируются криптографически безопасным генератором (`SecureRandom`)
- Длина: 48 байт (64 символа в base64)
- Формат: `brt_<random_base64>`
- Хранятся в БД в открытом виде (для проверки), но показываются только при создании

### Цепочка security фильтров

1. **IntegrationSecurityConfig** (Order=1) - проверяет API токены
2. **ClientSecurityConfig** (Order=2) - проверяет client JWT

Если запрос имеет API токен (`brt_*`), он аутентифицируется как external service.
Если нет - передаётся дальше для JWT аутентификации.

### Best Practices

1. **Храните токены безопасно** - как пароли
2. **Используйте HTTPS** - всегда
3. **Ротация токенов** - периодически пересоздавайте сервисы
4. **Минимальные разрешения** - указывайте только нужные endpoints
5. **Мониторинг** - следите за логами использования
6. **Rate limiting** - устанавливайте адекватные лимиты

## Domain Events

Система генерирует следующие domain events:

- `ExternalServiceCreatedEvent`
- `ExternalServiceUpdatedEvent`
- `ExternalServiceBlockedEvent`
- `ExternalServiceUnblockedEvent`
- `ExternalServiceDeletedEvent`

Можно использовать для аудита и интеграции с другими системами.

## Примеры сценариев

### Сценарий 1: Интеграция с партнёрским приложением

1. Партнёр обращается с запросом на доступ
2. Админ создаёт external service через Admin API
3. Админ передаёт токен партнёру безопасным способом
4. Партнёр использует токен в своём приложении
5. Система логирует все запросы партнёра

### Сценарий 2: Блокировка при подозрении на злоупотребление

1. Мониторинг выявляет подозрительную активность
2. Админ немедленно блокирует сервис через POST `/block`
3. Все последующие запросы с этим токеном отклоняются
4. Расследование проблемы
5. При необходимости - разблокировка или удаление

### Сценарий 3: Постепенное расширение доступа

1. Начать с ограниченных endpoints: `["/api/v1/mobile/routes/*"]`
2. После тестирования расширить: `["/api/v1/mobile/**"]`
3. Обновить через PUT `/external-services/{id}`
4. Изменения применяются немедленно

## Troubleshooting

### 401 Unauthorized
- Проверьте формат токена: должен начинаться с `brt_`
- Убедитесь, что токен не заблокирован или удалён

### 403 Forbidden (Unauthorized endpoint)
- Проверьте список `allowedEndpoints` для сервиса
- Убедитесь, что запрашиваемый путь соответствует паттернам

### 429 Too Many Requests
- Превышен rate limit
- Подождите до начала следующей минуты
- Или попросите админа увеличить лимит

## Миграция базы данных

Миграция находится в:
```
src/main/resources/db/migration/V19__create_external_services_tables.sql
```

Выполняется автоматически при запуске приложения через Flyway.
