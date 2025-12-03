# Предложение: Создать отдельный endpoint для списка городов

## Проблема

При загрузке городов для SelectBox во frontend мы сталкиваемся с ограничениями пагинации:

### Текущая ситуация:

**Backend ограничение:**
```java
// BasePaginatedController.java
public static final int MAX_SIZE = 100;
```

**Frontend запрос:**
```typescript
// Запрашиваем только активные города для селекта
const response = await cityAPI.getAll({ size: 100, active: true });
```

### Недостатки текущего подхода:

1. **Ограничение в 100 городов** - если в системе будет больше 100 активных городов, они не попадут в селект
2. **Ненужная пагинация** - для селектов не нужна полная информация о пагинации (totalPages, currentPage и т.д.)
3. **Избыточные данные** - возвращаются все поля города, хотя для селекта нужны только `id` и `name`
4. **Производительность** - каждый раз при открытии формы делается полноценный запрос с пагинацией

## Предлагаемое решение

Создать отдельный endpoint `/cities/list` специально для селектов без пагинации.

### 1. Создать новый DTO для списка городов

**Файл:** `admin/application/dto/city/CityListItemDto.java`

```java
package biz.ugur.busroutebackend.admin.application.dto.city;

public record CityListItemDto(
    String id,
    String name,
    Boolean isActive
) {
    public static CityListItemDto fromEntity(City city) {
        return new CityListItemDto(
            city.getId().toString(),
            city.getName(),
            city.getIsActive()
        );
    }
}
```

### 2. Создать Use Case для получения списка

**Файл:** `admin/application/usecase/city/GetCitiesListUseCase.java`

```java
package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityListItemDto;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GetCitiesListUseCase extends BaseUseCase<Mono<Boolean>, List<CityListItemDto>> {

    private final CityRepository cityRepository;

    public GetCitiesListUseCase(
        CityRepository cityRepository,
        CorrelationContextService correlationService,
        EventBus eventBus
    ) {
        super(correlationService, eventBus);
        this.cityRepository = cityRepository;
    }

    @Override
    protected Mono<List<CityListItemDto>> process(Mono<Boolean> activeOnly) {
        return activeOnly.flatMap(active -> {
            Flux<City> citiesFlux = active
                ? cityRepository.findByIsActive(true)
                : cityRepository.findAll();

            return citiesFlux
                .map(CityListItemDto::fromEntity)
                .collectList()
                .doOnSuccess(list ->
                    log.debug("Retrieved {} cities for list", list.size())
                );
        });
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }
}
```

### 3. Добавить endpoint в контроллер

**Файл:** `interfaces/rest/admin/V1/controller/AdminCityController.java`

```java
@GetMapping("/list")
public Mono<ResponseEntity<ApiResponse<List<CityListItemDto>>>> getCitiesList(
        @RequestParam(required = false, defaultValue = "true") Boolean active) {

    log.debug("Getting cities list with active={}", active);

    return ok(Mono.just(active)
        .as(getCitiesListUseCase::execute));
}
```

### 4. Обновить Frontend

**Файл:** `src/features/cities/api/cityAPI.ts`

```typescript
export const cityAPI = {
  // ... существующие методы ...

  /**
   * Получить список городов для селектов (без пагинации)
   */
  async getList(activeOnly = true): Promise<ApiResponse<City[]>> {
    const url = `/cities/list?active=${activeOnly}`;
    const response = await apiClient.get<ApiResponse<BackendCity[]>>(url);

    if (response.data.success && response.data.data) {
      return {
        success: true,
        data: response.data.data.map(transformFromBackend)
      };
    }
    return response.data as any;
  }
};
```

**Использование во frontend:**

```typescript
// StopCreateView.vue и StopEditView.vue
async function loadCities() {
  try {
    const response = await cityAPI.getList(true); // Только активные города
    if (response.success && response.data) {
      cities.value = response.data;
      console.log('✅ Загружено городов:', cities.value.length);
    }
  } catch (error) {
    console.error('❌ Ошибка загрузки городов:', error);
    toastError('Ошибка загрузки городов');
  }
}
```

## Преимущества

1. ✅ **Нет ограничения на количество** - вернутся все активные города
2. ✅ **Меньше данных** - только необходимые поля (id, name)
3. ✅ **Быстрее** - нет расчета пагинации и лишних полей
4. ✅ **Кэширование** - можно добавить кэш на бэкенде для этого endpoint
5. ✅ **Гибкость** - можно добавить фильтр по региону в будущем
6. ✅ **Переиспользование** - можно использовать для других селектов

## Альтернативы

### Альтернатива 1: Увеличить MAX_SIZE
❌ Плохое решение - это не решает проблему масштабирования

### Альтернатива 2: Сделать множественные запросы с пагинацией
❌ Неэффективно - много лишних запросов

### Альтернатива 3: Использовать текущий endpoint с size=100
✅ Временное решение (текущий вариант)
- Работает если городов < 100
- Требует рефакторинга при росте количества

## Рекомендация

Реализовать **Предлагаемое решение** с отдельным endpoint `/cities/list` для:
- Долгосрочной масштабируемости
- Лучшей производительности
- Чистоты архитектуры (разделение ответственности)

## Дополнительные улучшения

### Кэширование на бэкенде

```java
@Cacheable(value = "cities-list", key = "#active")
public Mono<List<CityListItemDto>> getCitiesList(Boolean active) {
    // ...
}
```

### Добавить фильтр по региону (опционально)

```java
@GetMapping("/list")
public Mono<ResponseEntity<ApiResponse<List<CityListItemDto>>>> getCitiesList(
        @RequestParam(required = false, defaultValue = "true") Boolean active,
        @RequestParam(required = false) String region) {
    // ...
}
```

## Приоритет

**Средний приоритет** - текущее решение работает для систем с < 100 городами, но рекомендуется реализовать при первой возможности.

## Оценка трудоемкости

- Backend: 2-3 часа (UseCase + Controller + тесты)
- Frontend: 1 час (обновить API + компоненты)
- **Всего: 3-4 часа**
