# AI Voice Recognition API Specification
## Спецификация API для AI-сервера голосового поиска

---

## 1. Введение

### 1.1 Обзор
AI-сервер получает голосовой запрос пользователя, распознает намерение и возвращает структурированный **Action** с указанием экрана приложения и параметрами для Backend.

### 1.2 Архитектура
```
[Пользователь] --голос--> [AI-сервер] --action+screen+params--> [Backend] --результат--> [Приложение]
```

### 1.3 Разделы приложения (Screens)

Приложение содержит 4 основных раздела:

| Screen | Описание | Функционал |
|--------|----------|------------|
| `bus` | Городской транспорт | Маршруты, остановки, планирование поездок |
| `taxi` | Такси | Заказ такси, расчет стоимости |
| `place` | Места | Достопримечательности, POI, информация о местах |
| `news` | Новости | Новости города, транспорта, события |

---

## 2. Все возможные Actions

### 2.1 Bus Actions (screen: "bus")

| Action | Описание | Примеры фраз |
|--------|----------|--------------|
| `search-routes-to-stop` | Найти маршруты до остановки | "Какие автобусы идут в Теке базар?" |
| `search-route-by-number` | Найти маршрут по номеру | "Покажи маршрут 12" |
| `plan-trip` | Построить маршрут между точками | "Как доехать от вокзала до университета?" |
| `find-nearby-stops` | Найти остановки рядом | "Какие остановки рядом со мной?" |
| `show-route-schedule` | Показать расписание маршрута | "Когда приходит 29-й автобус?" |
| `show-stop-arrivals` | Показать прибытие на остановку | "Когда придет автобус на эту остановку?" |
| `show-all-routes` | Показать все маршруты | "Покажи все автобусы" |
| `add-to-favorites` | Добавить в избранное | "Добавь этот маршрут в избранное" |

### 2.2 Taxi Actions (screen: "taxi")

| Action | Описание | Примеры фраз |
|--------|----------|--------------|
| `order-taxi` | Заказать такси | "Вызови такси", "Нужно такси" |
| `calculate-taxi-fare` | Рассчитать стоимость | "Сколько будет стоить такси до центра?" |

### 2.3 Place Actions (screen: "place")

| Action | Описание | Примеры фраз |
|--------|----------|--------------|
| `find-place` | Найти место | "Где находится Теке базар?" |
| `show-place-info` | Показать информацию о месте | "Расскажи про Национальный музей" |
| `find-nearby-places` | Места рядом | "Что интересного поблизости?" |

### 2.4 News Actions (screen: "news")

| Action | Описание | Примеры фраз |
|--------|----------|--------------|
| `show-latest-news` | Показать последние новости | "Какие новости?", "Что нового?" |
| `search-news` | Поиск новостей | "Новости о транспорте" |

### 2.5 Universal Actions (любой screen)

| Action | Описание | Примеры фраз |
|--------|----------|--------------|
| `clarification-needed` | Нужно уточнение | "Автобус" (без контекста) |
| `unsupported-request` | Запрос не поддерживается | "Сколько стоит билет в Москву?" |

---

## 3. Рекомендуемый формат Response

### 3.1 Базовая структура (ОБЯЗАТЕЛЬНАЯ)

```json
{
  "action": "string",
  "screen": "bus" | "taxi" | "place" | "news",
  "params": {
    // специфичные параметры для каждого action
  },
  "metadata": {
    "confidence": 0.95,
    "language": "ru",
    "recognized_text": "покажи маршруты до Теке базара"
  }
}
```

**Обоснование выбора:**
- ✅ **screen** - указывает, на какой экран приложения переключиться
- ✅ **action** - что именно делать на этом экране
- ✅ **params** - параметры для выполнения действия
- ✅ **metadata** - дополнительная информация (опционально)
- ✅ Простота парсинга на Backend
- ✅ Легко расширяется для новых screens/actions

### 3.2 Логика выбора Screen

**Правила определения screen:**

1. **Ключевые слова транспорта** → `screen: "bus"`
   - автобус, маршрут, остановка, поездка, доехать, расписание

2. **Ключевые слова такси** → `screen: "taxi"`
   - такси, вызвать такси, заказать такси, стоимость такси

3. **Ключевые слова мест** → `screen: "place"`
   - где находится, что такое, достопримечательность, музей, парк

4. **Ключевые слова новостей** → `screen: "news"`
   - новости, что нового, события, объявления

5. **Неоднозначность** → использовать контекст предыдущего запроса

---

## 4. Детальное описание Actions

### 4.1 Bus Actions

#### 4.1.1 `search-routes-to-stop`

**Screen:** `bus`

**Примеры:**
- "Какие автобусы идут в Теке базар?"
- "Как доехать до центра?"

**Структура:**
```json
{
  "action": "search-routes-to-stop",
  "screen": "bus",
  "params": {
    "stop_name": "Teke bazar"
  },
  "metadata": {
    "confidence": 0.95,
    "language": "ru",
    "recognized_text": "какие автобусы идут в Теке базар"
  }
}
```

1. Открывает экран "bus"
2. Ищет остановку по названию
3. Показывает список маршрутов

---

#### 4.1.2 `search-route-by-number`

**Screen:** `bus`

**Примеры:**
- "Покажи маршрут 12"
- "Где ходит 29-й автобус?"

**Структура:**
```json
{
  "action": "search-route-by-number",
  "screen": "bus",
  "params": {
    "route_number": "12"
  },
  "metadata": {
    "confidence": 0.98,
    "language": "ru",
    "recognized_text": "покажи маршрут 12"
  }
}
```

1. Открывает экран "bus"
2. Ищет маршрут по номеру
3. Показывает детали маршрута + остановки

---

#### 4.1.3 `plan-trip`

**Screen:** `bus`

**Примеры:**
- "Как доехать от вокзала до Теке базара?"
- "Маршрут из центра в аэропорт"

**Структура (вариант 1 - по названиям):**
```json
{
  "action": "plan-trip",
  "screen": "bus",
  "params": {
    "from": {
      "type": "place_name",
      "value": "вокзал"
    },
    "to": {
      "type": "place_name",
      "value": "Teke bazar"
    },
    "preferences": {
      "max_transfers": 2,
      "prioritize_speed": true
    }
  },
  "metadata": {
    "confidence": 0.88,
    "language": "ru",
    "recognized_text": "как доехать от вокзала до Теке базара"
  }
}
```

**Структура (вариант 2 - по координатам):**
```json
{
  "action": "plan-trip",
  "screen": "bus",
  "params": {
    "from": {
      "type": "coordinates",
      "lat": 37.9601,
      "lon": 58.3261,
      "description": "вокзал"
    },
    "to": {
      "type": "coordinates",
      "lat": 37.9401,
      "lon": 58.3861,
      "description": "Teke bazar"
    }
  },
  "metadata": {
    "confidence": 0.92,
    "language": "ru"
  }
}
```

1. Открывает экран "bus"
2. Выполняет геокодирование (если нужно)
3. Вызывает `POST /api/v1/routing/search`
4. Показывает варианты маршрутов

---

#### 4.1.4 `find-nearby-stops`

**Screen:** `bus`

**Примеры:**
- "Какие остановки рядом?"
- "Ближайшая остановка"

**Структура:**
```json
{
  "action": "find-nearby-stops",
  "screen": "bus",
  "params": {
    "lat": 37.9601,
    "lon": 58.3261,
    "radius_km": 1.0
  },
  "metadata": {
    "confidence": 0.93,
    "language": "ru",
    "recognized_text": "какие остановки рядом"
  }
}
```

1. Открывает экран "bus"
2. Вызывает `GET /api/v1/routing/nearby-stops`
3. Показывает список остановок с расстоянием

---

#### 4.1.5 `show-route-schedule`

**Screen:** `bus`

**Примеры:**
- "Когда приходит 29-й?"
- "Расписание 12-го маршрута"

**Структура:**
```json
{
  "action": "show-route-schedule",
  "screen": "bus",
  "params": {
    "route_number": "29"
  },
  "metadata": {
    "confidence": 0.96,
    "language": "ru",
    "recognized_text": "когда приходит 29-й"
  }
}
```

---

#### 4.1.6 `show-stop-arrivals`

**Screen:** `bus`

**Примеры:**
- "Когда придет автобус?"
- "Сколько ждать автобус на этой остановке?"

**Структура:**
```json
{
  "action": "show-stop-arrivals",
  "screen": "bus",
  "params": {
    "stop_id": "uuid-или-название",
    "current_location": {
      "lat": 37.9601,
      "lon": 58.3261
    }
  },
  "metadata": {
    "confidence": 0.85,
    "language": "ru",
    "recognized_text": "когда придет автобус"
  }
}
```

---

#### 4.1.7 `show-all-routes`

**Screen:** `bus`

**Примеры:**
- "Покажи все автобусы"
- "Список всех маршрутов"

**Структура:**
```json
{
  "action": "show-all-routes",
  "screen": "bus",
  "params": {},
  "metadata": {
    "confidence": 0.99,
    "language": "ru",
    "recognized_text": "покажи все автобусы"
  }
}
```

---

#### 4.1.8 `add-to-favorites`

**Screen:** `bus`

**Примеры:**
- "Добавь этот маршрут в избранное"
- "Сохрани эту остановку"

**Структура:**
```json
{
  "action": "add-to-favorites",
  "screen": "bus",
  "params": {
    "type": "route",
    "id": "route-uuid-or-number",
    "operation": "add"
  },
  "metadata": {
    "confidence": 0.91,
    "language": "ru",
    "recognized_text": "добавь этот маршрут в избранное"
  }
}
```

**Где:**
- `type`: "route" | "stop"
- `operation`: "add" | "remove" | "toggle"

---

### 4.2 Taxi Actions

#### 4.2.1 `order-taxi`

**Screen:** `taxi`

**Примеры:**
- "Вызови такси"
- "Нужно такси до аэропорта"
- "Заказать такси"

**Структура:**
```json
{
  "action": "order-taxi",
  "screen": "taxi",
  "params": {
    "from": {
      "type": "current_location",
      "lat": 37.9601,
      "lon": 58.3261
    },
    "to": {
      "type": "place_name",
      "value": "аэропорт"
    }
  },
  "metadata": {
    "confidence": 0.92,
    "language": "ru",
    "recognized_text": "вызови такси до аэропорта"
  }
}
```

1. Открывает экран "taxi"
2. Предзаполняет форму заказа
3. Показывает кнопку "Заказать"

---

#### 4.2.2 `calculate-taxi-fare`

**Screen:** `taxi`

**Примеры:**
- "Сколько будет стоить такси до центра?"
- "Цена такси до вокзала"

**Структура:**
```json
{
  "action": "calculate-taxi-fare",
  "screen": "taxi",
  "params": {
    "from": {
      "type": "current_location",
      "lat": 37.9601,
      "lon": 58.3261
    },
    "to": {
      "type": "place_name",
      "value": "центр"
    }
  },
  "metadata": {
    "confidence": 0.89,
    "language": "ru",
    "recognized_text": "сколько будет стоить такси до центра"
  }
}
```

1. Открывает экран "taxi"
2. Рассчитывает стоимость поездки
3. Показывает примерную цену

---

### 4.3 Place Actions

#### 4.3.1 `find-place`

**Screen:** `place`

**Примеры:**
- "Где находится Теке базар?"
- "Как найти Национальный музей?"

**Структура:**
```json
{
  "action": "find-place",
  "screen": "place",
  "params": {
    "place_name": "Teke bazar",
    "place_type": "market"
  },
  "metadata": {
    "confidence": 0.94,
    "language": "ru",
    "recognized_text": "где находится Теке базар"
  }
}
```

1. Открывает экран "place"
2. Ищет место по названию
3. Показывает на карте + детали

---

#### 4.3.2 `show-place-info`

**Screen:** `place`

**Примеры:**
- "Расскажи про Национальный музей"
- "Что такое Теке базар?"
- "Информация о монументе Нейтралитета"

**Структура:**
```json
{
  "action": "show-place-info",
  "screen": "place",
  "params": {
    "place_name": "Национальный музей",
    "info_type": "description"
  },
  "metadata": {
    "confidence": 0.91,
    "language": "ru",
    "recognized_text": "расскажи про национальный музей"
  }
}
```

1. Открывает экран "place"
2. Получает информацию о месте
3. Показывает описание, фото, часы работы

---

#### 4.3.3 `find-nearby-places`

**Screen:** `place`

**Примеры:**
- "Что интересного поблизости?"
- "Достопримечательности рядом"

**Структура:**
```json
{
  "action": "find-nearby-places",
  "screen": "place",
  "params": {
    "lat": 37.9601,
    "lon": 58.3261,
    "radius_km": 2.0,
    "category": "all"
  },
  "metadata": {
    "confidence": 0.87,
    "language": "ru",
    "recognized_text": "что интересного поблизости"
  }
}
```

**Где:**
- `category`: "all" | "restaurant" | "museum" | "park" | "shopping"

---

### 4.4 News Actions

#### 4.4.1 `show-latest-news`

**Screen:** `news`

**Примеры:**
- "Какие новости?"
- "Что нового?"
- "Последние новости"

**Структура:**
```json
{
  "action": "show-latest-news",
  "screen": "news",
  "params": {
    "category": "all",
    "limit": 10
  },
  "metadata": {
    "confidence": 0.97,
    "language": "ru",
    "recognized_text": "какие новости"
  }
}
```

1. Открывает экран "news"
2. Вызывает `GET /api/v1/mobile/banners`
3. Показывает список новостей

---

#### 4.4.2 `search-news`

**Screen:** `news`

**Примеры:**
- "Новости о транспорте"
- "Новости за последнюю неделю"

**Структура:**
```json
{
  "action": "search-news",
  "screen": "news",
  "params": {
    "query": "транспорт",
    "time_range": "week"
  },
  "metadata": {
    "confidence": 0.88,
    "language": "ru",
    "recognized_text": "новости о транспорте"
  }
}
```

**Где:**
- `time_range`: "today" | "week" | "month" | "all"

---

### 4.5 Universal Actions

#### 4.5.1 `clarification-needed`

**Screen:** текущий или "bus" (по умолчанию)

**Примеры:**
- "Автобус" (без контекста)
- "Остановка" (какая?)

**Структура:**
```json
{
  "action": "clarification-needed",
  "screen": "bus",
  "params": {
    "missing_info": "destination",
    "question": "Куда вы хотите поехать?",
    "suggestions": ["Теке базар", "центр", "вокзал"]
  },
  "metadata": {
    "confidence": 0.45,
    "language": "ru",
    "recognized_text": "автобус"
  }
}
```

- Остается на текущем экране
- Показывает вопрос с вариантами ответов

---

#### 4.5.2 `unsupported-request`

**Screen:** текущий

**Примеры:**
- "Сколько стоит билет в Москву?"
- "Погода завтра"

**Структура:**
```json
{
  "action": "unsupported-request",
  "screen": "bus",
  "params": {
    "reason": "out_of_scope",
    "message": "Извините, я могу помочь только с городским транспортом и такси Туркменистана"
  },
  "metadata": {
    "confidence": 0.78,
    "language": "ru",
    "recognized_text": "сколько стоит билет в москву"
  }
}
```

---

## 5. Обработка ошибок и неоднозначностей

### 5.1 Уровни confidence

| Confidence | Действие |
|-----------|----------|
| 0.9 - 1.0 | Выполнить action + переключить screen немедленно |
| 0.7 - 0.89 | Выполнить + показать "Вы имели в виду...?" |
| 0.5 - 0.69 | Запросить подтверждение перед переключением screen |
| 0.0 - 0.49 | Отправить `clarification-needed` без переключения |

### 5.2 Множественные результаты

Если AI нашел несколько вариантов:

```json
{
  "action": "search-routes-to-stop",
  "screen": "bus",
  "params": {
    "stop_name": "центр",
    "alternatives": [
      { "name": "Центральный рынок", "confidence": 0.82 },
      { "name": "Центр города", "confidence": 0.78 },
      { "name": "Торговый центр", "confidence": 0.65 }
    ]
  }
}
```

Backend показывает все варианты пользователю на экране "bus".

### 5.3 Контекстные переходы между screens

**Пример 1: Bus → Taxi**
```
Пользователь: "Какие автобусы идут в аэропорт?"
AI: {screen: "bus", action: "search-routes-to-stop", ...}

Пользователь: "Нет, лучше такси"
AI: {screen: "taxi", action: "order-taxi", params: {to: "аэропорт"}}
```

AI должен запомнить место назначения из предыдущего запроса.

**Пример 2: Place → Bus**
```
Пользователь: "Где находится Национальный музей?"
AI: {screen: "place", action: "find-place", ...}

Пользователь: "Как туда доехать?"
AI: {screen: "bus", action: "plan-trip", params: {to: "Национальный музей"}}
```

---

## 6. Выводы

### 6.1 Выбранная структура

**Финальный формат:**
```json
{
  "action": "string",
  "screen": "bus" | "taxi" | "place" | "news",
  "params": { ... },
  "metadata": { ... }
}
```

**Почему именно такой:**
- ✅ **screen** указывает раздел приложения → улучшает UX
- ✅ Простой для парсинга на Backend
- ✅ Поддержка многофункционального приложения
- ✅ Расширяемый (легко добавить новые screens)
- ✅ Контекстные переходы между разделами


---

**Документ готов к передаче AI-команде и Backend-разработчикам** ✅
