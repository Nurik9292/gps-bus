# Admin API Documentation

## Overview

Документация описывает RESTful API для административной панели системы планирования автобусных маршрутов. API построен на Spring Boot 3.5 WebFlux (reactive) и использует JWT-аутентификацию.

**Base URL**: `/api/v1/admin`

**API Version**: V1

**Architecture**: Fully reactive (Project Reactor)

**Authentication**: JWT Bearer Token

## Table of Contents

1. [Authentication](#1-authentication)
2. [Admin User Management](#2-admin-user-management)
3. [Route Management](#3-route-management)
4. [Stop Management](#4-stop-management)
5. [Banner Management](#5-banner-management)
6. [Notification Management](#6-notification-management)
7. [City Management](#7-city-management)
8. [External Services Management](#8-external-services-management)
9. [Common Response Format](#9-common-response-format)
10. [Error Handling](#10-error-handling)
11. [Pagination](#11-pagination)

---

## 1. Authentication

**Base Path**: `/api/v1/admin/auth`

All admin endpoints (except login and refresh) require JWT authentication via `Authorization: Bearer <token>` header.

**JWT Configuration**:
- Access Token TTL: 1 hour
- Refresh Token TTL: 7 days
- Token Type: Bearer

### 1.1 Login

Аутентификация администратора и получение токенов доступа.

**Endpoint**: `POST /api/v1/admin/auth/login`

**Authentication**: Not required

**Request Body**:
```json
{
  "username": "string",    // 3-20 characters, required
  "password": "string"     // 6-100 characters, required
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 3600,
    "admin": {
      "id": "uuid",
      "username": "admin",
      "full_name": "Admin User",
      "is_super_admin": true,
      "is_active": true,
      "last_login_at": "2025-11-06T10:30:00",
      "created_at": "2025-01-01T00:00:00",
      "updated_at": "2025-11-06T10:30:00",
      "avatar": "http://example.com/avatar.jpg"
    }
  }
}
```

**Validation Rules**:
- `username`: 3-20 characters, required
- `password`: 6-100 characters, required

---

### 1.2 Refresh Token

Обновление access token с использованием refresh token.

**Endpoint**: `POST /api/v1/admin/auth/refresh`

**Authentication**: Not required

**Request Body**:
```json
{
  "refreshToken": "string"
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "access_token": "new_access_token",
    "refresh_token": "new_refresh_token",
    "token_type": "Bearer",
    "expires_in": 3600,
    "admin": { /* AdminProfileResponse */ }
  }
}
```

---

### 1.3 Logout

Выход из системы и инвалидация токена.

**Endpoint**: `POST /api/v1/admin/auth/logout`

**Authentication**: Required

**Headers**:
```
Authorization: Bearer <access_token>
```

**Response**: `204 No Content`

---

### 1.4 Get Current Admin

Получение информации о текущем администраторе.

**Endpoint**: `GET /api/v1/admin/auth/me`

**Authentication**: Required

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "username": "admin",
    "full_name": "Admin User",
    "is_super_admin": true,
    "is_active": true,
    "last_login_at": "2025-11-06T10:30:00",
    "created_at": "2025-01-01T00:00:00",
    "updated_at": "2025-11-06T10:30:00",
    "avatar": "http://example.com/avatar.jpg"
  }
}
```

---

## 2. Admin User Management

**Base Path**: `/api/v1/admin/users`

**Authentication**: Required for all endpoints

### 2.1 Get All Admins

Получение списка всех администраторов.

**Endpoint**: `GET /api/v1/admin/users`

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "admins": [
      {
        "id": "uuid",
        "username": "admin",
        "full_name": "Admin User",
        "avatar": "url",
        "is_active": true,
        "is_super_admin": true,
        "last_login_at": "2025-11-06T10:30:00",
        "created_at": "2025-01-01T00:00:00"
      }
    ]
  }
}
```

---

### 2.2 Get Admin By ID

Получение информации об администраторе по ID.

**Endpoint**: `GET /api/v1/admin/users/{adminId}`

**Path Parameters**:
- `adminId` (string, required): ID администратора

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "username": "admin",
    "full_name": "Admin User",
    "avatar": "http://example.com/avatars/2025/11/original_xxx.jpg",
    "is_active": true,
    "is_super_admin": true,
    "last_login_at": "2025-11-06T10:30:00",
    "created_at": "2025-01-01T00:00:00",
    "updated_at": "2025-11-06T10:30:00"
  }
}
```

**Error Responses**:
- `404 NOT FOUND`: Администратор с указанным ID не найден
```json
{
  "success": false,
  "message": "Admin not found with id: {adminId}",
  "data": null
}
```

---

### 2.3 Create Admin

Создание нового администратора с опциональным аватаром.

**Endpoint**: `POST /api/v1/admin/users`

**Request Body**:
```json
{
  "username": "string",         // 3-20 characters, required
  "password": "string",         // min 8 characters, required
  "full_name": "string",        // max 100 characters, required
  "avatar": "string",           // URL or base64 encoded image (data:image/...), optional
  "is_super_admin": false,      // optional, default: false
  "is_active": true             // optional, default: true
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "message": "Created successfully",
  "data": {
    "id": "uuid",
    "username": "newadmin",
    "full_name": "New Admin",
    "avatar": "http://example.com/avatars/uuid/avatar.jpg",
    "is_active": true,
    "is_super_admin": false,
    "last_login_at": null,
    "created_at": "2025-11-06T10:30:00"
  }
}
```

**Validation Rules**:
- `username`: 3-20 characters, required
- `password`: minimum 8 characters, required
- `full_name`: maximum 100 characters, required
- `avatar`: optional, can be:
  - URL string (e.g., "http://example.com/image.jpg")
  - Base64 encoded image with data URI scheme (e.g., "data:image/png;base64,iVBORw0KG...")

**Avatar Handling**:
- If `avatar` starts with `data:image/`, it will be processed as base64 and saved to storage
- If avatar save fails, admin will be created without avatar (no error thrown)
- Supported image formats: PNG, JPEG, GIF, WebP

---

### 2.4 Update Admin

Обновление данных администратора.

**Endpoint**: `PUT /api/v1/admin/users/{adminId}`

**Path Parameters**:
- `adminId` (string, required): ID администратора

**Request Body**:
```json
{
  "username": "string",
  "full_name": "string",
  "is_super_admin": false,
  "is_active": true
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "username": "updatedadmin",
    "full_name": "Updated Name",
    "avatar": null,
    "is_active": true,
    "is_super_admin": false,
    "last_login_at": "2025-11-06T10:30:00",
    "created_at": "2025-01-01T00:00:00"
  }
}
```

---

### 2.5 Delete Admin

Удаление администратора.

**Endpoint**: `DELETE /api/v1/admin/users/{adminId}`

**Path Parameters**:
- `adminId` (string, required): ID администратора

**Response**: `204 No Content`

---

### 2.6 Activate Admin

Активация администратора.

**Endpoint**: `POST /api/v1/admin/users/{id}/activate`

**Path Parameters**:
- `id` (string, required): ID администратора

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "username": "admin",
    "is_active": true,
    /* ... other admin fields ... */
  }
}
```

---

### 2.7 Deactivate Admin

Деактивация администратора.

**Endpoint**: `POST /api/v1/admin/users/{id}/deactivate`

**Path Parameters**:
- `id` (string, required): ID администратора

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "username": "admin",
    "is_active": false,
    /* ... other admin fields ... */
  }
}
```

---

### 2.8 Update Current Admin Profile

Обновление профиля текущего администратора.

**Endpoint**: `PATCH /api/v1/admin/users/profile`

**Request Body**:
```json
{
  "username": "string",
  "full_name": "string"
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "username": "updatedusername",
    "full_name": "Updated Full Name",
    /* ... other profile fields ... */
  }
}
```

---

### 2.9 Update Current Admin Avatar

Обновление аватара текущего администратора.

**Endpoint**: `PATCH /api/v1/admin/users/profile/avatar`

**Request Body**:
```json
{
  "avatar": "string"  // URL or base64 encoded image
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "avatar": "http://example.com/new-avatar.jpg",
    /* ... other profile fields ... */
  }
}
```

---

### 2.10 Remove Current Admin Avatar

Удаление аватара текущего администратора.

**Endpoint**: `DELETE /api/v1/admin/users/profile/avatar`

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "avatar": null,
    /* ... other profile fields ... */
  }
}
```

---

## 3. Route Management

**Base Path**: `/api/v1/admin/routes`

**Authentication**: Required for all endpoints

### 3.1 Get All Routes (Paginated)

Получение списка маршрутов с пагинацией и фильтрацией.

**Endpoint**: `GET /api/v1/admin/routes`

**Query Parameters**:
- `page` (integer, optional, default: 1): Номер страницы
- `size` (integer, optional, default: 20): Размер страницы (max: 100)
- `sort` (string, optional, default: "routeNumber"): Поле для сортировки (camelCase)
- `order` (string, optional, default: "asc"): Порядок сортировки (asc/desc)
- `active` (boolean, optional): Фильтр по статусу активности

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "routes": [
      {
        "id": "uuid",
        "route_number": "29",
        "route_name": "Центр - Вокзал",
        "name_tm": "Merkez - Wokzal",
        "name_en": "Center - Station",
        "route_color": "#1976D2",
        "estimated_duration_minutes": 45,
        "is_active": true,
        "city_id": "uuid",
        "forward_stops": ["stop_id_1", "stop_id_2"],
        "backward_stops": ["stop_id_2", "stop_id_1"],
        "forward_geometry": [[37.95, 58.38], [37.96, 58.39]],
        "backward_geometry": [[37.96, 58.39], [37.95, 58.38]],
        "created_at": "2025-01-01T00:00:00",
        "updated_at": "2025-11-06T10:30:00"
      }
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "total_elements": 50,
      "total_pages": 3
    }
  }
}
```

---

### 3.2 Get All Routes (No Pagination)

Получение всех маршрутов без пагинации.

**Endpoint**: `GET /api/v1/admin/routes/all`

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "routes": [/* array of routes */]
  }
}
```

---

### 3.3 Create Route

Создание нового маршрута.

**Endpoint**: `POST /api/v1/admin/routes`

**Request Body**:
```json
{
  "route_number": "29",              // Pattern: ^[0-9]{1,3}[A-Z]?$, required
  "route_name": "Центр - Вокзал",    // 2-200 characters, required
  "name_tm": "Merkez - Wokzal",      // max 200 characters, optional
  "name_en": "Center - Station",     // max 200 characters, optional
  "route_color": "#1976D2",          // Hex color pattern, default: #1976D2
  "estimated_duration_minutes": 45,  // 2-300, default: 60
  "is_active": true,                 // default: true
  "city_id": "uuid",                 // optional
  "forward_stops": ["uuid1", "uuid2"],
  "backward_stops": ["uuid2", "uuid1"],
  "forward_geometry": [[37.95, 58.38], [37.96, 58.39]],
  "backward_geometry": [[37.96, 58.39], [37.95, 58.38]]
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "message": "Created successfully",
  "data": {
    "id": "uuid",
    "route_number": "29",
    /* ... other route fields ... */
  }
}
```

**Validation Rules**:
- `route_number`: Pattern `^[0-9]{1,3}[A-Z]?$` (e.g., "29", "7A")
- `route_name`: 2-200 characters
- `route_color`: Valid hex color (e.g., "#1976D2")
- `estimated_duration_minutes`: 2-300 minutes
- Geometry: Array of [latitude, longitude] pairs

---

### 3.4 Update Route

Обновление маршрута.

**Endpoint**: `PUT /api/v1/admin/routes/{routeId}`

**Path Parameters**:
- `routeId` (string, required): ID маршрута

**Request Body**: Same as Create Route

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "route_number": "29A",
    /* ... updated route fields ... */
  }
}
```

---

### 3.5 Delete Route

Удаление маршрута.

**Endpoint**: `DELETE /api/v1/admin/routes/{routeId}`

**Path Parameters**:
- `routeId` (string, required): ID маршрута

**Response**: `204 No Content`

---

### 3.6 Check Route Number Availability

Проверка доступности номера маршрута.

**Endpoint**: `GET /api/v1/admin/routes/check-availability`

**Query Parameters**:
- `routeNumber` (string, optional): Номер маршрута для проверки

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "available": true,
    "route_number": "29"
  }
}
```

---

## 4. Stop Management

**Base Path**: `/api/v1/admin/stops`

**Authentication**: Required for all endpoints

### 4.1 Get All Stops (Paginated)

Получение списка остановок с пагинацией, фильтрацией и поиском.

**Endpoint**: `GET /api/v1/admin/stops`

**Query Parameters**:
- `page` (integer, optional, default: 1): Номер страницы
- `size` (integer, optional, default: 20): Размер страницы
- `sort` (string, optional, default: "do"): Поле для сортировки
- `order` (string, optional, default: "desc"): Порядок сортировки
- `active` (boolean, optional): Фильтр по статусу активности
- `search` (string, optional): Поиск по названию остановки

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "stops": [
      {
        "id": "uuid",
        "stop_name": "Площадь Независимости",
        "name_en": "Independence Square",
        "name_tm": "Garaşsyzlyk meýdançasy",
        "latitude": 37.95,
        "longitude": 58.38,
        "is_major_stop": true,
        "is_active": true,
        "city_id": "uuid",
        "created_at": "2025-01-01T00:00:00",
        "updated_at": "2025-11-06T10:30:00"
      }
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "total_elements": 150,
      "total_pages": 8
    }
  }
}
```

---

### 4.2 Create Stop

Создание новой остановки.

**Endpoint**: `POST /api/v1/admin/stops`

**Request Body**:
```json
{
  "stop_name": "Площадь Независимости",  // 2-100 characters, required
  "name_en": "Independence Square",      // max 100 characters, optional
  "name_tm": "Garaşsyzlyk meýdançasy",   // max 100 characters, optional
  "latitude": 37.95,                     // 35.1 - 42.8 (Turkmenistan bounds), required
  "longitude": 58.38,                    // 52.5 - 66.7 (Turkmenistan bounds), required
  "is_major_stop": true,                 // default: false
  "is_active": true,                     // default: true
  "city_id": "uuid"                      // required
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "message": "Created successfully",
  "data": {
    "id": "uuid",
    "stop_name": "Площадь Независимости",
    /* ... other stop fields ... */
  }
}
```

**Validation Rules**:
- `stop_name`: 2-100 characters
- `latitude`: 35.1 - 42.8 (Turkmenistan geographic bounds)
- `longitude`: 52.5 - 66.7 (Turkmenistan geographic bounds)
- `city_id`: required

---

### 4.3 Get Stop by ID

Получение остановки по ID.

**Endpoint**: `GET /api/v1/admin/stops/{stopId}`

**Path Parameters**:
- `stopId` (string, required): ID остановки

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "stop_name": "Площадь Независимости",
    /* ... other stop fields ... */
  }
}
```

---

### 4.4 Update Stop

Обновление остановки.

**Endpoint**: `PUT /api/v1/admin/stops/{stopId}`

**Path Parameters**:
- `stopId` (string, required): ID остановки

**Request Body**: Same as Create Stop

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "stop_name": "Updated Stop Name",
    /* ... updated stop fields ... */
  }
}
```

---

### 4.5 Delete Stop

Удаление остановки.

**Endpoint**: `DELETE /api/v1/admin/stops/{stopId}`

**Path Parameters**:
- `stopId` (string, required): ID остановки

**Response**: `204 No Content`

---

## 5. Banner Management

**Base Path**: `/api/v1/admin/banners`

**Authentication**: Required for all endpoints

**Banner Types**: `main`, `stops`, `routes`, `places`, `popup`

**Note**: `reply_time` field is **required** for `popup` type banners and must be `null` for other types.

### 5.1 Get All Banners (Paginated)

Получение списка баннеров с пагинацией и фильтрацией.

**Endpoint**: `GET /api/v1/admin/banners`

**Query Parameters**:
- `page` (integer, optional, default: 1): Номер страницы
- `size` (integer, optional, default: 20): Размер страницы
- `sort` (string, optional, default: "display_order"): Поле для сортировки
- `order` (string, optional, default: "asc"): Порядок сортировки
- `active` (boolean, optional): Фильтр по статусу активности

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "banners": [
      {
        "id": "uuid",
        "title": "Весенняя акция",
        "type": "main",
        "image_url": "http://example.com/banner.jpg",
        "target_url": "http://example.com/promo",
        "display_order": 1,
        "is_active": true,
        "start_date": "2025-03-01T00:00:00",
        "end_date": "2025-04-01T00:00:00",
        "content": "Описание акции",
        "reply_time": null,
        "created_at": "2025-01-01T00:00:00",
        "updated_at": "2025-11-06T10:30:00"
      },
      {
        "id": "uuid-2",
        "title": "Важное уведомление",
        "type": "popup",
        "image_url": "http://example.com/popup-banner.jpg",
        "target_url": null,
        "display_order": 1,
        "is_active": true,
        "start_date": "2025-03-01T00:00:00",
        "end_date": "2025-04-01T00:00:00",
        "content": "Согласны ли вы?",
        "reply_time": 10,
        "created_at": "2025-01-01T00:00:00",
        "updated_at": "2025-11-06T10:30:00"
      }
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "total_elements": 10,
      "total_pages": 1
    }
  }
}
```

---

### 5.2 Create Banner

Создание нового баннера.

**Endpoint**: `POST /api/v1/admin/banners`

**Request Body**:
```json
{
  "title": "Весенняя акция",         // max 200 characters, required
  "type": "main",                    // one of: main, stops, routes, places, popup; default: "main"
  "imageUrl": "http://example.com/banner.jpg",  // required
  "targetUrl": "http://example.com/promo",      // optional
  "displayOrder": 1,                 // default: 0
  "startDate": "2025-03-01T00:00:00", // optional
  "endDate": "2025-04-01T00:00:00",  // optional
  "content": "Описание акции",       // optional
  "replyTime": null                  // required for popup type, must be null for others
}
```

**Example for POPUP banner**:
```json
{
  "title": "Важное уведомление",
  "type": "popup",
  "imageUrl": "http://example.com/popup.jpg",
  "targetUrl": null,
  "displayOrder": 1,
  "startDate": "2025-03-01T00:00:00",
  "endDate": "2025-04-01T00:00:00",
  "content": "Согласны ли вы принять условия?",
  "replyTime": 10
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "message": "Created successfully",
  "data": {
    "id": "uuid",
    "title": "Важное уведомление",
    "type": "popup",
    "image_url": "http://example.com/popup.jpg",
    "target_url": null,
    "display_order": 1,
    "is_active": true,
    "start_date": "2025-03-01T00:00:00",
    "end_date": "2025-04-01T00:00:00",
    "content": "Согласны ли вы принять условия?",
    "reply_time": 10,
    "created_at": "2025-11-18T10:30:00",
    "updated_at": "2025-11-18T10:30:00"
  }
}
```

**Validation Rules**:
- `title`: maximum 200 characters, required
- `type`: one of `main`, `stops`, `routes`, `places`, `popup`; default: `main`
- `imageUrl`: required
- `replyTime`:
  - **For `popup` type**: Required, must be a positive integer (seconds)
  - **For other types**: Must be `null` or omitted

**Validation Errors**:

**Missing replyTime for popup** (400):
```json
{
  "success": false,
  "message": "Reply time is required for POPUP banners",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": "replyTime field is required when type is 'popup'"
  }
}
```

**Invalid replyTime for non-popup** (400):
```json
{
  "success": false,
  "message": "Reply time is only applicable for POPUP banners",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": "replyTime must be null for banner types: main, stops, routes, places"
  }
}
```

**Invalid replyTime value** (400):
```json
{
  "success": false,
  "message": "Reply time must be a positive number (in seconds)",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": "replyTime must be greater than 0"
  }
}
```

---

### 5.3 Update Banner

Обновление баннера.

**Endpoint**: `PUT /api/v1/admin/banners/{bannerId}`

**Path Parameters**:
- `bannerId` (string, required): ID баннера

**Request Body**: Same as Create Banner

**Example updating to POPUP type**:
```json
{
  "title": "Обновлённое уведомление",
  "type": "popup",
  "imageUrl": "http://example.com/updated-popup.jpg",
  "targetUrl": null,
  "displayOrder": 1,
  "isActive": true,
  "startDate": "2025-03-01T00:00:00",
  "endDate": "2025-04-01T00:00:00",
  "content": "Новый текст",
  "replyTime": 15
}
```

**Example updating from POPUP to MAIN type**:
```json
{
  "title": "Обычный баннер",
  "type": "main",
  "imageUrl": "http://example.com/main-banner.jpg",
  "targetUrl": "http://example.com/promo",
  "displayOrder": 1,
  "isActive": true,
  "startDate": "2025-03-01T00:00:00",
  "endDate": "2025-04-01T00:00:00",
  "content": "Описание",
  "replyTime": null
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "title": "Обновлённое уведомление",
    "type": "popup",
    "image_url": "http://example.com/updated-popup.jpg",
    "target_url": null,
    "display_order": 1,
    "is_active": true,
    "start_date": "2025-03-01T00:00:00",
    "end_date": "2025-04-01T00:00:00",
    "content": "Новый текст",
    "reply_time": 15,
    "created_at": "2025-01-01T00:00:00",
    "updated_at": "2025-11-18T10:35:00"
  }
}
```

**Note**: Same validation rules apply as in Create Banner. When changing banner type to/from `popup`, ensure `replyTime` is set accordingly.

---

### 5.4 Delete Banner

Удаление баннера.

**Endpoint**: `DELETE /api/v1/admin/banners/{bannerId}`

**Path Parameters**:
- `bannerId` (string, required): ID баннера

**Response**: `204 No Content`

---

### 5.5 Toggle Banner Status

Изменение статуса активности баннера.

**Endpoint**: `GET /api/v1/admin/banners/toggle-status/{id}`

**Path Parameters**:
- `id` (string, required): ID баннера

**Query Parameters**:
- `active` (boolean, required): Новый статус активности

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "is_active": false,
    /* ... other banner fields ... */
  }
}
```

---

### 5.6 Banner Types Reference

Система поддерживает следующие типы баннеров:

| Type | Description | Reply Time | Typical Use Case |
|------|-------------|------------|------------------|
| `main` | Главные баннеры | Not allowed (null) | Промо-акции, реклама на главной странице |
| `stops` | Баннеры для остановок | Not allowed (null) | Информация о конкретных остановках |
| `routes` | Баннеры для маршрутов | Not allowed (null) | Информация о маршрутах |
| `places` | Баннеры мест | Not allowed (null) | Достопримечательности, POI |
| `popup` | Всплывающие уведомления | **Required** (seconds) | Важные уведомления, опросы, согласия |

**Reply Time Details**:
- **Purpose**: Определяет время в секундах, в течение которого popup баннер ожидает ответа пользователя
- **Format**: Integer (положительное число)
- **Unit**: Секунды
- **Validation**:
  - Для `popup` типа: обязательно, должно быть > 0
  - Для остальных типов: должно быть `null`
- **Example Values**:
  - `10` - краткое уведомление
  - `30` - стандартное время для опроса
  - `60` - длительное информационное сообщение

**Use Case Examples**:

**POPUP with reply_time=10**:
```json
{
  "title": "Согласие на обработку данных",
  "type": "popup",
  "content": "Мы используем cookies. Продолжая использование, вы соглашаетесь с политикой конфиденциальности.",
  "replyTime": 10
}
```

**POPUP with reply_time=30**:
```json
{
  "title": "Оцените приложение",
  "type": "popup",
  "content": "Оцените наше приложение, это займет всего 30 секунд!",
  "replyTime": 30
}
```

**MAIN banner (no reply_time)**:
```json
{
  "title": "Весенняя распродажа",
  "type": "main",
  "content": "Скидки до 50% на все билеты!",
  "replyTime": null
}
```

---

## 6. Notification Management

**Base Path**: `/api/v1/admin/notifications`

**Authentication**: Required for all endpoints

Управление системными уведомлениями для пользователей. Уведомления похожи на баннеры, но без полей type, imageUrl, period (startDate/endDate), targetUrl и replyTime.

### 6.1 Get All Notifications (Paginated)

Получение списка уведомлений с пагинацией и фильтрацией.

**Endpoint**: `GET /api/v1/admin/notifications`

**Query Parameters**:
- `page` (integer, optional, default: 1): Номер страницы
- `size` (integer, optional, default: 20): Размер страницы
- `sort` (string, optional, default: "display_order"): Поле для сортировки
- `order` (string, optional, default: "asc"): Порядок сортировки
- `active` (boolean, optional): Фильтр по статусу активности
- `query` (string, optional): Поиск по заголовку

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "notifications": [
      {
        "id": "uuid",
        "title": "Важное уведомление",
        "content": "Текст уведомления",
        "display_order": 1,
        "is_active": true,
        "created_at": "2025-01-01T00:00:00",
        "updated_at": "2025-11-06T10:30:00"
      }
    ],
    "active_count": 5,
    "pagination": {
      "page": 1,
      "size": 20,
      "total_elements": 10,
      "total_pages": 1
    }
  }
}
```

---

### 6.2 Get Notification by ID

Получение уведомления по ID.

**Endpoint**: `GET /api/v1/admin/notifications/{notificationId}`

**Path Parameters**:
- `notificationId` (string, required): ID уведомления

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "title": "Важное уведомление",
    "content": "Текст уведомления",
    "display_order": 1,
    "is_active": true,
    "created_at": "2025-01-01T00:00:00",
    "updated_at": "2025-11-06T10:30:00"
  }
}
```

**Error Responses**:
- `404 NOT FOUND`: Уведомление с указанным ID не найдено
```json
{
  "success": false,
  "message": "Notification not found with ID: {notificationId}",
  "data": null
}
```

---

### 6.3 Create Notification

Создание нового уведомления.

**Endpoint**: `POST /api/v1/admin/notifications`

**Request Body**:
```json
{
  "title": "Важное уведомление",       // max 200 characters, required
  "content": "Текст уведомления",      // optional
  "displayOrder": 1,                   // default: 0
  "isActive": true                     // default: true
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "message": "Created successfully",
  "data": {
    "id": "uuid",
    "title": "Важное уведомление",
    "content": "Текст уведомления",
    "display_order": 1,
    "is_active": true,
    "created_at": "2025-12-02T10:30:00",
    "updated_at": "2025-12-02T10:30:00"
  }
}
```

**Validation Rules**:
- `title`: maximum 200 characters, required
- `displayOrder`: integer, default: 0
- `isActive`: boolean, default: true

**Validation Errors**:

**Missing title** (400):
```json
{
  "success": false,
  "message": "Validation failed",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": {
      "title": "Title is required"
    }
  }
}
```

---

### 6.4 Update Notification

Обновление уведомления.

**Endpoint**: `PUT /api/v1/admin/notifications/{notificationId}`

**Path Parameters**:
- `notificationId` (string, required): ID уведомления

**Request Body**:
```json
{
  "title": "Обновлённое уведомление",
  "content": "Обновлённый текст",
  "displayOrder": 2,
  "isActive": true
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "title": "Обновлённое уведомление",
    "content": "Обновлённый текст",
    "display_order": 2,
    "is_active": true,
    "created_at": "2025-01-01T00:00:00",
    "updated_at": "2025-12-02T10:35:00"
  }
}
```

---

### 6.5 Delete Notification

Удаление уведомления.

**Endpoint**: `DELETE /api/v1/admin/notifications/{notificationId}`

**Path Parameters**:
- `notificationId` (string, required): ID уведомления

**Response**: `204 No Content`

---

### 6.6 Toggle Notification Status

Изменение статуса активности уведомления.

**Endpoint**: `GET /api/v1/admin/notifications/toggle-status/{id}`

**Path Parameters**:
- `id` (string, required): ID уведомления

**Query Parameters**:
- `active` (boolean, required): Новый статус активности

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "title": "Важное уведомление",
    "content": "Текст уведомления",
    "display_order": 1,
    "is_active": false,
    "created_at": "2025-01-01T00:00:00",
    "updated_at": "2025-12-02T10:40:00"
  }
}
```

---

### 6.7 Notification Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string (UUID) | Уникальный идентификатор уведомления |
| `title` | string | Заголовок уведомления (макс. 200 символов) |
| `content` | string | Содержимое/текст уведомления |
| `display_order` | integer | Порядок отображения (чем меньше, тем выше) |
| `is_active` | boolean | Статус активности |
| `created_at` | datetime | Дата и время создания |
| `updated_at` | datetime | Дата и время последнего обновления |

---

## 7. City Management

**Base Path**: `/api/v1/admin/cities`

**Authentication**: Required for all endpoints

### 7.1 Get All Cities (Paginated)

Получение списка городов с пагинацией и фильтрацией.

**Endpoint**: `GET /api/v1/admin/cities`

**Query Parameters**:
- `page` (integer, optional, default: 1): Номер страницы
- `size` (integer, optional, default: 20): Размер страницы
- `sort` (string, optional, default: "name"): Поле для сортировки
- `order` (string, optional, default: "asc"): Порядок сортировки
- `active` (boolean, optional): Фильтр по статусу активности

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "cities": [
      {
        "id": "uuid",
        "name": "Ашхабад",
        "name_tm": "Aşgabat",
        "display_order": 1,
        "is_active": true,
        "created_at": "2025-01-01T00:00:00",
        "updated_at": "2025-11-06T10:30:00"
      }
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "total_elements": 5,
      "total_pages": 1
    }
  }
}
```

---

### 7.2 Create City

Создание нового города.

**Endpoint**: `POST /api/v1/admin/cities`

**Request Body**:
```json
{
  "name": "Ашхабад",           // max 100 characters, required
  "name_tm": "Aşgabat",        // max 100 characters, optional
  "display_order": 1           // default: 0
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "message": "Created successfully",
  "data": {
    "id": "uuid",
    "name": "Ашхабад",
    "name_tm": "Aşgabat",
    "display_order": 1,
    "is_active": true,
    "created_at": "2025-11-06T10:30:00",
    "updated_at": "2025-11-06T10:30:00"
  }
}
```

**Validation Rules**:
- `name`: maximum 100 characters, required

---

### 7.3 Update City

Обновление города.

**Endpoint**: `PUT /api/v1/admin/cities/{id}`

**Path Parameters**:
- `id` (string, required): ID города

**Request Body**: Same as Create City

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "name": "Updated City Name",
    /* ... updated city fields ... */
  }
}
```

---

### 7.4 Get City by ID

Получение города по ID.

**Endpoint**: `GET /api/v1/admin/cities/{id}`

**Path Parameters**:
- `id` (string, required): ID города

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "name": "Ашхабад",
    /* ... other city fields ... */
  }
}
```

---

### 7.5 Delete City

Удаление города.

**Endpoint**: `DELETE /api/v1/admin/cities/{id}`

**Path Parameters**:
- `id` (string, required): ID города

**Response**: `204 No Content`

---

### 7.6 Get Cities List

Получение простого списка городов (без пагинации).

**Endpoint**: `GET /api/v1/admin/cities/list`

**Query Parameters**:
- `active` (boolean, optional, default: true): Фильтр по статусу активности

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "id": "uuid",
      "name": "Ашхабад",
      "name_tm": "Aşgabat",
      "display_order": 1,
      "is_active": true,
      "created_at": "2025-01-01T00:00:00",
      "updated_at": "2025-11-06T10:30:00"
    }
  ]
}
```

---

## 8. External Services Management

**Base Path**: `/api/v1/admin/external-services`

**Authentication**: Required for all endpoints

Управление внешними сервисами, которые имеют доступ к Mobile API через статические API-токены.

### 8.1 Get All External Services

Получение списка всех внешних сервисов.

**Endpoint**: `GET /api/v1/admin/external-services`

**Query Parameters**:
- `activeOnly` (boolean, optional, default: false): Показать только активные сервисы

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "services": [
      {
        "id": "uuid",
        "name": "Partner API Service",
        "description": "Partner integration for route data",
        "apiToken": null,
        "maskedToken": "brt_abc1...xyz9",
        "isActive": true,
        "allowedEndpoints": [
          "/api/v1/mobile/routes/*",
          "/api/v1/mobile/stops/*"
        ],
        "rateLimitPerMinute": 100,
        "lastUsedAt": "2025-11-13T10:30:00",
        "createdByAdminId": "admin-uuid",
        "createdAt": "2025-11-01T00:00:00",
        "updatedAt": "2025-11-13T10:30:00"
      }
    ],
    "totalCount": 5,
    "activeCount": 3
  }
}
```

**Notes**:
- `apiToken` всегда `null` в списке (токен показывается только при создании)
- `maskedToken` показывает первые 8 и последние 4 символа токена
- `allowedEndpoints` может быть `null` (все endpoints разрешены)

---

### 8.2 Get External Service by ID

Получение информации о внешнем сервисе по ID.

**Endpoint**: `GET /api/v1/admin/external-services/{id}`

**Path Parameters**:
- `id` (string, required): ID внешнего сервиса

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "name": "Partner API Service",
    "description": "Partner integration for route data",
    "apiToken": null,
    "maskedToken": "brt_abc1...xyz9",
    "isActive": true,
    "allowedEndpoints": ["/api/v1/mobile/**"],
    "rateLimitPerMinute": 100,
    "lastUsedAt": "2025-11-13T10:30:00",
    "createdByAdminId": "admin-uuid",
    "createdAt": "2025-11-01T00:00:00",
    "updatedAt": "2025-11-13T10:30:00"
  }
}
```

**Error Responses**:
- `404 NOT FOUND`: Внешний сервис не найден
```json
{
  "success": false,
  "message": "External service not found: {id}",
  "data": null
}
```

---

### 8.3 Create External Service

Создание нового внешнего сервиса с автоматической генерацией API токена.

**Endpoint**: `POST /api/v1/admin/external-services`

**Request Body**:
```json
{
  "name": "Partner API Service",           // 3-255 characters, required
  "description": "Partner integration",    // optional
  "allowedEndpoints": [                    // optional, null = all endpoints
    "/api/v1/mobile/routes/*",
    "/api/v1/mobile/stops/*"
  ],
  "rateLimitPerMinute": 100                // optional, null = no limit
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "message": "Created successfully",
  "data": {
    "id": "uuid",
    "name": "Partner API Service",
    "description": "Partner integration",
    "apiToken": "brt_abc123def456ghi789...",  // ⚠️ Показывается ТОЛЬКО при создании!
    "maskedToken": "brt_abc1...xyz9",
    "isActive": true,
    "allowedEndpoints": [
      "/api/v1/mobile/routes/*",
      "/api/v1/mobile/stops/*"
    ],
    "rateLimitPerMinute": 100,
    "lastUsedAt": null,
    "createdByAdminId": "current-admin-uuid",
    "createdAt": "2025-11-13T10:30:00",
    "updatedAt": "2025-11-13T10:30:00"
  }
}
```

**⚠️ ВАЖНО**:
- Поле `apiToken` содержит полный токен ТОЛЬКО в ответе на создание
- Сохраните токен в безопасном месте - повторно получить его невозможно
- При последующих запросах `apiToken` всегда будет `null`

**Validation Rules**:
- `name`: 3-255 characters, required, unique
- `rateLimitPerMinute`: must be positive if specified
- `allowedEndpoints`: array of endpoint patterns with wildcard support:
  - `*` - matches any characters except `/`
  - `**` - matches any characters including `/`
  - Examples: `/api/v1/mobile/routes/*`, `/api/v1/mobile/**`

**Endpoint Pattern Examples**:
```json
[
  "/api/v1/mobile/routes",           // Только этот endpoint
  "/api/v1/mobile/routes/*",         // Все routes endpoints
  "/api/v1/mobile/routes/**",        // Все routes и sub-routes
  "/api/v1/mobile/**"                // Все mobile endpoints
]
```

---

### 8.4 Update External Service

Обновление параметров внешнего сервиса (токен НЕ меняется).

**Endpoint**: `PUT /api/v1/admin/external-services/{id}`

**Path Parameters**:
- `id` (string, required): ID внешнего сервиса

**Request Body**:
```json
{
  "name": "Updated Service Name",
  "description": "Updated description",
  "allowedEndpoints": ["/api/v1/mobile/**"],
  "rateLimitPerMinute": 200
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "name": "Updated Service Name",
    "description": "Updated description",
    "apiToken": null,
    "maskedToken": "brt_abc1...xyz9",  // Токен не меняется
    "isActive": true,
    "allowedEndpoints": ["/api/v1/mobile/**"],
    "rateLimitPerMinute": 200,
    "lastUsedAt": "2025-11-13T10:30:00",
    "createdByAdminId": "admin-uuid",
    "createdAt": "2025-11-01T00:00:00",
    "updatedAt": "2025-11-13T11:00:00"
  }
}
```

**Notes**:
- API токен не изменяется при обновлении
- Изменения применяются немедленно
- Для смены токена нужно создать новый сервис и удалить старый

---

### 8.5 Block External Service

Блокировка (деактивация) внешнего сервиса. Доступ к API немедленно прекращается.

**Endpoint**: `POST /api/v1/admin/external-services/{id}/block`

**Path Parameters**:
- `id` (string, required): ID внешнего сервиса

**Query Parameters**:
- `reason` (string, optional): Причина блокировки

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "name": "Partner API Service",
    "isActive": false,  // Теперь заблокирован
    /* ... other fields ... */
  }
}
```

**Security Note**:
- Блокировка действует немедленно
- Все последующие запросы с этим токеном получат 401 Unauthorized
- Событие `ExternalServiceBlockedEvent` генерируется для аудита

---

### 8.6 Unblock External Service

Разблокировка (активация) внешнего сервиса.

**Endpoint**: `POST /api/v1/admin/external-services/{id}/unblock`

**Path Parameters**:
- `id` (string, required): ID внешнего сервиса

**Response**: `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "name": "Partner API Service",
    "isActive": true,  // Теперь активен
    /* ... other fields ... */
  }
}
```

---

### 8.7 Delete External Service

Удаление внешнего сервиса. API токен становится недействительным немедленно.

**Endpoint**: `DELETE /api/v1/admin/external-services/{id}`

**Path Parameters**:
- `id` (string, required): ID внешнего сервиса

**Response**: `204 No Content`

**Security Note**:
- Удаление необратимо
- Токен немедленно перестаёт работать
- Все логи API использования сохраняются (CASCADE DELETE в БД)
- Событие `ExternalServiceDeletedEvent` генерируется для аудита

---

### 8.8 External Service Features

#### API Token Format
- Prefix: `brt_` (Bus Route Token)
- Length: 64 characters (48 bytes base64-encoded)
- Generated using `SecureRandom` (cryptographically secure)
- Example: `brt_kJ8n3mP9qR2sT5vX7wY0zA1bC4dE6fG8hI0jK2lM4nO6pQ8rS0tU2vW4xY6zA8bC0dE2fG4h`

#### Rate Limiting
- Implemented via Redis (sliding window)
- Counted per minute
- When exceeded: HTTP 429 Too Many Requests
- `null` value = no limit

#### Endpoint Permissions
- `null` or empty array = all endpoints allowed
- Wildcard patterns supported:
  - `*` = any chars except `/`
  - `**` = any chars including `/`
- Checked on every request
- When unauthorized: HTTP 403 Forbidden

#### API Usage Logging
All requests from external services are logged to `external_service_api_logs`:
- Endpoint path
- HTTP method
- Response status
- Response time (ms)
- IP address
- User agent
- Error message (if failed)

#### Domain Events
The following events are emitted for audit:
- `ExternalServiceCreatedEvent`
- `ExternalServiceUpdatedEvent`
- `ExternalServiceBlockedEvent`
- `ExternalServiceUnblockedEvent`
- `ExternalServiceDeletedEvent`

---

### 8.9 How External Services Use API Tokens

External services authenticate by sending the API token in the Authorization header:

```http
GET /api/v1/mobile/routes HTTP/1.1
Host: api.example.com
Authorization: Bearer brt_abc123...
```

**Authentication Flow**:
1. Request includes `Authorization: Bearer brt_*` header
2. `ApiTokenAuthenticationFilter` intercepts (Order=1, before JWT)
3. Token validated against database
4. Check if service is active
5. Check if endpoint is allowed
6. Check rate limit
7. If all checks pass: request proceeds
8. Usage logged asynchronously

**Error Responses**:

**Invalid Token** (401):
```json
{
  "success": false,
  "message": "Invalid API token"
}
```

**Blocked Service** (401):
```json
{
  "success": false,
  "message": "External service is blocked: ServiceName"
}
```

**Unauthorized Endpoint** (403):
```json
{
  "success": false,
  "message": "External service 'ServiceName' is not authorized to access endpoint: /api/v1/mobile/vehicles"
}
```

**Rate Limit Exceeded** (429):
```json
{
  "success": false,
  "message": "Rate limit exceeded for service 'ServiceName': 101/100 requests per minute"
}
```

---

## 9. Common Response Format

Все успешные ответы API следуют единому формату:

```json
{
  "success": true,
  "message": "Success message",
  "data": { /* Response data */ }
}
```

**Response Fields**:
- `success` (boolean): Статус выполнения запроса
- `message` (string): Сообщение о результате
- `data` (object): Данные ответа

### Success Status Codes

- `200 OK`: Успешное выполнение запроса
- `201 Created`: Ресурс успешно создан
- `204 No Content`: Успешное выполнение без возвращаемых данных

---

## 10. Error Handling

В случае ошибки API возвращает следующий формат:

```json
{
  "success": false,
  "message": "Error description",
  "error": {
    "code": "ERROR_CODE",
    "details": "Detailed error information"
  }
}
```

### Error Status Codes

- `400 Bad Request`: Невалидные данные в запросе
- `401 Unauthorized`: Отсутствует или невалиден JWT токен
- `403 Forbidden`: Недостаточно прав для выполнения операции
- `404 Not Found`: Ресурс не найден
- `409 Conflict`: Конфликт данных (например, дубликат)
- `422 Unprocessable Entity`: Ошибка валидации
- `500 Internal Server Error`: Внутренняя ошибка сервера

### Common Error Examples

**Validation Error** (400):
```json
{
  "success": false,
  "message": "Validation failed",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": {
      "username": "Username must be between 3 and 20 characters",
      "password": "Password is required"
    }
  }
}
```

**Authentication Error** (401):
```json
{
  "success": false,
  "message": "Unauthorized",
  "error": {
    "code": "INVALID_TOKEN",
    "details": "JWT token is invalid or expired"
  }
}
```

**Not Found Error** (404):
```json
{
  "success": false,
  "message": "Resource not found",
  "error": {
    "code": "NOT_FOUND",
    "details": "Admin with id 'xyz' not found"
  }
}
```

---

## 11. Pagination

Endpoints с поддержкой пагинации принимают следующие параметры:

### Query Parameters

- `page` (integer, optional, default: 1): Номер страницы (начиная с 1)
- `size` (integer, optional, default: 20): Количество элементов на странице
  - Minimum: 1
  - Maximum: 100
- `sort` (string, optional): Поле для сортировки (в camelCase)
  - Автоматически конвертируется в snake_case для БД
- `order` (string, optional): Порядок сортировки
  - Values: `asc` или `desc`
  - Default зависит от endpoint'а

### Pagination Response Format

```json
{
  "data": {
    "items": [/* array of items */],
    "pagination": {
      "page": 1,
      "size": 20,
      "total_elements": 150,
      "total_pages": 8
    }
  }
}
```

**Pagination Fields**:
- `page` (integer): Текущая страница
- `size` (integer): Размер страницы
- `total_elements` (integer): Общее количество элементов
- `total_pages` (integer): Общее количество страниц

### Example Pagination Request

```
GET /api/v1/admin/routes?page=2&size=50&sort=routeNumber&order=desc
```

### CamelCase to Snake_Case Conversion

Query параметры в camelCase автоматически конвертируются в snake_case для SQL запросов:

- `routeNumber` → `route_number`
- `createdAt` → `created_at`
- `displayOrder` → `display_order`

---

## Additional Notes

### Base Controllers

API построен на базовых контроллерах:

1. **BaseController** (`interfaces/rest/admin/V1/controller/AuthController.java:26`):
   - Предоставляет общие методы для работы с ответами
   - Используется для не-пагинированных endpoints

2. **BasePaginatedController** (`interfaces/rest/admin/V1/controller/AdminRouteController.java:23`):
   - Расширяет BaseController
   - Добавляет поддержку пагинации и валидации параметров
   - Используется для endpoints с пагинацией

### Security

- Все endpoints (кроме `/auth/login` и `/auth/refresh`) требуют JWT токен
- JWT токен передается в header: `Authorization: Bearer <token>`
- Admin JWT имеет повышенный уровень безопасности:
  - Access Token: 1 час
  - Refresh Token: 7 дней
- Tokens хранятся в Redis для быстрой инвалидации

### CORS

Все admin endpoints поддерживают CORS с `origins = "*"` для разработки. В production рекомендуется настроить конкретные домены.

### Architecture Pattern

API следует принципам Clean Architecture и DDD:
- **Controllers**: Только маршрутизация и валидация
- **Use Cases**: Бизнес-логика
- **Domain**: Доменные модели и правила
- **Infrastructure**: Persistence и внешние сервисы

### Reactive Programming

Все endpoints полностью реактивны (Project Reactor):
- Возвращают `Mono<ResponseEntity<T>>` для единичных значений
- Используют R2DBC для неблокирующего доступа к БД
- Никогда не блокируют потоки (без `.block()`)

---

## API Reference Summary

| Resource | Base Path | Endpoints Count |
|----------|-----------|-----------------|
| Authentication | `/api/v1/admin/auth` | 4 |
| Admin Users | `/api/v1/admin/users` | 9 |
| Routes | `/api/v1/admin/routes` | 6 |
| Stops | `/api/v1/admin/stops` | 5 |
| Banners | `/api/v1/admin/banners` | 6 |
| Notifications | `/api/v1/admin/notifications` | 6 |
| Cities | `/api/v1/admin/cities` | 6 |
| External Services | `/api/v1/admin/external-services` | 7 |

**Total Endpoints**: 49

**Notes**:
- Banner endpoints now include `reply_time` field support for POPUP type banners (v1.2)
- Notification Management added (v1.3)

---

## Controller File Locations

- `AuthController`: `src/main/java/biz/ugur/busroutebackend/interfaces/rest/admin/V1/controller/AuthController.java`
- `AdminUserController`: `src/main/java/biz/ugur/busroutebackend/interfaces/rest/admin/V1/controller/AdminUserController.java`
- `AdminRouteController`: `src/main/java/biz/ugur/busroutebackend/interfaces/rest/admin/V1/controller/AdminRouteController.java`
- `AdminStopController`: `src/main/java/biz/ugur/busroutebackend/interfaces/rest/admin/V1/controller/AdminStopController.java`
- `AdminBannerController`: `src/main/java/biz/ugur/busroutebackend/interfaces/rest/admin/V1/controller/AdminBannerController.java`
- `AdminNotificationController`: `src/main/java/biz/ugur/busroutebackend/interfaces/rest/admin/V1/controller/AdminNotificationController.java`
- `AdminCityController`: `src/main/java/biz/ugur/busroutebackend/interfaces/rest/admin/V1/controller/AdminCityController.java`
- `AdminExternalServicesController`: `src/main/java/biz/ugur/busroutebackend/interfaces/rest/admin/V1/controller/AdminExternalServicesController.java`

---

## Related Documentation

- **External Services Integration Guide**: See `EXTERNAL_SERVICES_API_GUIDE.md` for detailed guide on:
  - How external services use API tokens
  - Rate limiting and permissions
  - Security best practices
  - Troubleshooting
  - Example implementations in various languages

---

**Documentation Version**: 1.3
**Last Updated**: 2025-12-02
**API Version**: V1

**Changelog**:
- **v1.3** (2025-12-02): Added Notification Management section (6 endpoints)
- **v1.2** (2025-11-18): Added `reply_time` field for Banner Management (required for `popup` type banners)
- **v1.1** (2025-11-13): Added External Services Management section
