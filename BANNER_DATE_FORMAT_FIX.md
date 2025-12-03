# Banner Date Format Fix

## Проблема
Поля `start_date` и `end_date` возвращались в виде массива вместо строки ISO-8601:

```json
{
  "start_date": [2025, 10, 14, 8, 36, 33, 772018000],
  "end_date": [2025, 10, 19, 10, 37, 0]
}
```

**Ожидалось:**
```json
{
  "start_date": "2025-10-14T08:36:33",
  "end_date": "2025-10-19T10:37:00"
}
```

## Причина

### Дефолтное поведение Jackson с LocalDateTime
По умолчанию Jackson сериализует `LocalDateTime` как массив компонентов: `[year, month, day, hour, minute, second, nanosecond]`.

### Конфигурация в application.yml не работала для WebFlux
В `application.yml` была настройка:
```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
```

**Но:** Эта настройка работает для Spring MVC, но **НЕ автоматически применяется для WebFlux**.

### Особенность Java Records
В Java record аннотации применяются к параметрам конструктора, а Jackson нужна аннотация на accessor method. Из-за этого может потребоваться явная аннотация `@JsonFormat`.

## Решение

Добавлена аннотация `@JsonFormat` к полям `startDate` и `endDate` в `BannerResponse`:

**Файл:** `banner/application/dto/BannerResponse.java`

```java
import com.fasterxml.jackson.annotation.JsonFormat;

public record BannerResponse(
        // ... другие поля ...

        @JsonProperty("start_date")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startDate,

        @JsonProperty("end_date")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime endDate,

        // ... другие поля ...
) {}
```

## Результат

### До исправления:
```json
{
  "id": "bbbd091f-5002-4c37-aa82-dbadb0780b20",
  "title": "dsfsdf",
  "start_date": [2025, 10, 14, 8, 36, 33, 772018000],
  "end_date": [2025, 10, 19, 10, 37, 0]
}
```

### После исправления:
```json
{
  "id": "bbbd091f-5002-4c37-aa82-dbadb0780b20",
  "title": "dsfsdf",
  "start_date": "2025-10-14T08:36:33",
  "end_date": "2025-10-19T10:37:00"
}
```

## Альтернативные решения

### Вариант 1: Глобальная конфигурация для WebFlux
Создать `@Configuration` с кастомным `CodecCustomizer`:

```java
@Configuration
public class JacksonConfig {

    @Bean
    public CodecCustomizer jacksonCodecCustomizer(ObjectMapper objectMapper) {
        return configurer -> {
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            configurer.defaultCodecs().jackson2JsonEncoder(
                new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON)
            );
            configurer.defaultCodecs().jackson2JsonDecoder(
                new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON)
            );
        };
    }
}
```

**Преимущества:**
- Применяется ко всем DTO автоматически
- Централизованная конфигурация

**Недостатки:**
- Требует дополнительный класс конфигурации
- Может повлиять на другие части системы

### Вариант 2: Использование @JsonFormat (Выбранное решение)
**Преимущества:**
- Явное и понятное
- Локальное для конкретного DTO
- Не требует дополнительной конфигурации
- Легко контролировать формат для каждого поля

**Недостатки:**
- Нужно добавлять к каждому полю `LocalDateTime`

### Вариант 3: Использование JsonSerializer
Создать кастомный сериализатор:

```java
public class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}

// Использование:
@JsonSerialize(using = LocalDateTimeSerializer.class)
LocalDateTime startDate;
```

## Рекомендации

### Для этого проекта:
✅ Используем `@JsonFormat` - простое, явное решение

### Для будущего:
Если будет много DTO с `LocalDateTime`, рассмотреть создание глобальной конфигурации WebFlux с `CodecCustomizer`.

### Проверить другие DTO:
Убедиться что все остальные DTO с датами тоже используют правильный формат.

## Тестирование

```bash
# Тест: GET баннер с датами
curl -X GET "http://localhost:8080/api/v1/admin/banners?page=1&size=1" \
  -H "Authorization: Bearer $TOKEN"

# Результат:
# ✅ "start_date": "2025-10-14T08:36:33"
# ✅ "end_date": "2025-10-19T10:37:00"
```

## Статус
✅ **Исправлено** - даты теперь возвращаются в формате ISO-8601 строками
